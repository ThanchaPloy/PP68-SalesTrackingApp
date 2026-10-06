package com.example.pp68_salestrackingapp.data.repository

import com.example.pp68_salestrackingapp.data.local.ContactDao
import com.example.pp68_salestrackingapp.data.local.CustomerDao
import com.example.pp68_salestrackingapp.data.local.LocalIdMappingDao
import com.example.pp68_salestrackingapp.data.model.LocalIdMapping
import com.example.pp68_salestrackingapp.data.model.ContactPerson
import com.example.pp68_salestrackingapp.data.remote.ApiService
import com.example.pp68_salestrackingapp.di.TokenManager
import com.example.pp68_salestrackingapp.utils.SyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import android.util.Log
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import javax.inject.Inject
import java.io.IOException
import kotlinx.coroutines.CancellationException
import com.example.pp68_salestrackingapp.utils.queuedOrFailed
import com.example.pp68_salestrackingapp.utils.retrySend

class ContactRepository @Inject constructor(
    private val apiService: ApiService,
    private val contactDao: ContactDao,
    private val customerDao: CustomerDao,
    private val localIdMappingDao: LocalIdMappingDao,
    private val tokenManager: TokenManager,
    private val syncManager: SyncManager,
    private val networkMonitor: com.example.pp68_salestrackingapp.utils.NetworkMonitor
) {
    fun getContactsPagingFlow(
        searchQuery: String,
        initial: String?
    ): Flow<PagingData<ContactPerson>> = Pager(
        config = PagingConfig(
            pageSize = 30,
            initialLoadSize = 60,
            prefetchDistance = 10,
            maxSize = 150,
            enablePlaceholders = false
        ),
        pagingSourceFactory = {
            contactDao.getContactsPaging(searchQuery.trim(), initial)
        }
    ).flow

    fun getAllContactsFlow(): Flow<List<ContactPerson>> = contactDao.getAllContacts()
    fun searchContactsFlow(query: String): Flow<List<ContactPerson>> = contactDao.searchContactsWithCompany("%$query%")

    suspend fun refreshContacts(): kotlin.Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                // The server scopes this endpoint by contact creator. Do not derive contact
                // visibility from locally cached customers because ERP customers are remote-only.
                val response = apiService.getContactPersons(limit = 5000)
                if (!response.isSuccessful || response.body() == null) {
                    return@withContext kotlin.Result.failure(Exception("HTTP ${response.code()}"))
                }
                val deduped = response.body().orEmpty()
                    .distinctBy { it.contactId }
                    .map { it.copy(isSynced = true) }
                // Reaching the API cap means the response may be truncated. Merge what we
                // received, but never clear rows that might live beyond this first page.
                if (response.body().orEmpty().size >= 5000) {
                    contactDao.insertAll(deduped)
                    return@withContext kotlin.Result.failure(
                        IllegalStateException("Contact refresh reached the 5,000-row safety limit")
                    )
                }
                // clearAndInsert ลบผู้ติดต่อที่ซิงค์แล้วทิ้งทั้งหมดก่อนใส่ชุดใหม่ ซึ่งถูกต้องเฉพาะตอนที่
                // ชุดใหม่ครบจริง ๆ ถ้าดึงมาได้แค่บางก้อน (เน็ตสะดุดกลางคัน) การลบจะกวาดผู้ติดต่อ
                // ของก้อนที่ดึงไม่สำเร็จหายไปจากแอปด้วย — กรณีนั้นให้เติมทับอย่างเดียว ไม่ลบ
                contactDao.clearAndInsert(deduped)
                kotlin.Result.success(Unit)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("ContactRepo", "refreshContacts error: ${e.message}", e)
                kotlin.Result.failure(e)
            }
        }
    }

    /**
     * คืน contact_id ที่ใช้งานได้จริง — id จาก server ถ้าส่งขึ้นสำเร็จ หรือ TEMP- ถ้ายังค้าง
     * ผู้ที่ต้องเลือกผู้ติดต่อที่เพิ่งสร้างต่อทันที (สร้างด่วนในหน้านัดหมาย/โครงการ) ต้องรู้ id นี้
     * เดิมคืน Unit แล้วสลับแถว TEMP- เป็นแถวจริงเงียบ ๆ ผู้เรียกจึงหา id ที่ถูกต้องไม่ได้
     */
    suspend fun addContact(contact: ContactPerson): kotlin.Result<String> {
        return withContext(Dispatchers.IO) {
            val localContact = localIdMappingDao.insertContactResolvingCustomer(contact.copy(isSynced = false))
            if (localContact.custId.startsWith("TEMP-")) {
                syncManager.scheduleSync()
                return@withContext kotlin.Result.success(localContact.contactId)
            }
            try {
                val fields = buildMap<String, Any?> {
                    put("customer_code", localContact.custId)
                    put("customer_name", localContact.customerName)
                    localContact.fullName?.let { put("contact_name", it) }
                    localContact.phoneNumber?.let { put("mobile_phone", it) }
                    localContact.email?.let { put("email", it) }
                    localContact.nickname?.let { put("nickname", it) }
                    localContact.position?.let { put("position", it) }
                    localContact.line?.let { put("line", it) }
                    put("is_active", localContact.isActive)
                    put("is_dm_confirmed", localContact.isDmConfirmed)
                }
                val response = retrySend(idempotent = false, tag = "addContact") { apiService.addContact(fields) }
                Log.d("ContactRepo", "POST contact → HTTP ${response.code()}, custId=${localContact.custId}")
                if (response.isSuccessful) {
                    val serverContact = response.body()?.firstOrNull()
                    Log.d("ContactRepo", "serverContactId=${serverContact?.contactId} localId=${localContact.contactId}")
                    if (serverContact != null && serverContact.contactId != localContact.contactId) {
                        // server generated real contact_id — replace TEMP record
                        localIdMappingDao.replaceTemporaryContact(
                            localContact.contactId,
                            serverContact.copy(isSynced = true)
                        )
                        kotlin.Result.success(serverContact.contactId)
                    } else {
                        contactDao.updateSyncStatus(localContact.contactId, true)
                        kotlin.Result.success(localContact.contactId)
                    }
                } else if (response.code() == 403) {
                    syncManager.markBlocked("contact", localContact.contactId)
                    kotlin.Result.failure(Exception("บันทึกผู้ติดต่อไม่สำเร็จ: ไม่มีสิทธิ์ทำรายการนี้"))
                } else {
                    val errBody = response.errorBody()?.string()
                    Log.e("ContactRepo", "POST failed ${response.code()}: $errBody")
                    syncManager.scheduleSync()
                    networkMonitor.queuedOrFailed(localContact.contactId, "เซิร์ฟเวอร์ตอบ ${response.code()}")
                }
            } catch (e: IOException) {
                syncManager.scheduleSync()
                networkMonitor.queuedOrFailed(localContact.contactId, e.message)
            } catch (e: Exception) {
                kotlin.Result.failure(e)
            }
        }
    }

    suspend fun updateContact(contactId: String, contact: ContactPerson): kotlin.Result<Unit> {
        return withContext(Dispatchers.IO) {
            val resolvedId = try {
                localIdMappingDao.resolveExistingId(LocalIdMapping.ENTITY_CONTACT, contactId)
            } catch (e: Exception) {
                return@withContext kotlin.Result.failure(e)
            }
            val localContact = try {
                localIdMappingDao.insertContactResolvingCustomer(
                    contact.copy(contactId = resolvedId, isSynced = false)
                )
            } catch (e: Exception) {
                return@withContext kotlin.Result.failure(e)
            }
            if (resolvedId.startsWith("TEMP-") || localContact.custId.startsWith("TEMP-")) {
                syncManager.scheduleSync()
                return@withContext kotlin.Result.success(Unit)
            }
            try {
                // ⚠️ ส่ง "" ไม่ใช่ null เพื่อล้างค่า — Gson ของแอปไม่ได้เปิด serializeNulls
                // (ดู NetworkModule) คีย์ที่ค่าเป็น null จะถูกตัดออกจาก body ตั้งแต่ตอนแปลงเป็น JSON
                // ส่ง null ไปจึงเท่ากับ "ไม่ได้แก้ฟิลด์นี้" ผลคือลบอีเมล/ไลน์/ตำแหน่ง/ชื่อเล่นไม่ได้เลย
                // ลบในแอปแล้วดูเหมือนสำเร็จ แต่ค่าเก่ายังอยู่บน server แล้ว refresh รอบหน้าดึงกลับมาทับ
                // ฝั่ง backend แปลง "" เป็น NULL ให้ (ContactPersonRepositoryImpl) — เป็นวิธีเดียวกับ
                // ที่ EditProjectFactorsViewModel ใช้กับ proposal_date อยู่แล้ว
                val updates = buildMap<String, Any?> {
                    // ⚠️ ฟอร์มแก้ไขเปิดให้เปลี่ยน "บริษัท" ได้ (ดรอปดาวน์ไม่ถูกปิดตอน edit)
                    // แต่เดิมไม่เคยส่งคีย์นี้ขึ้นไป ผลคือย้ายผู้ติดต่อไปบริษัทอื่นแล้ว
                    // PATCH สำเร็จ ไม่มี error แต่ server ยังผูกกับบริษัทเดิม พอ refresh รอบหน้า
                    // clearAndInsert ดึงแถวเก่ากลับมาทับ ชื่อบริษัทในแอปก็เด้งกลับเอง
                    // (SyncManager ฝั่ง outbox ส่ง customer_code อยู่แล้ว ทางนี้ทางเดียวที่หลุด)
                    put("customer_code", localContact.custId)
                    put("customer_name", localContact.customerName)
                    put("contact_name", localContact.fullName)
                    put("mobile_phone", localContact.phoneNumber.orEmpty())
                    put("email", localContact.email.orEmpty())
                    put("nickname", localContact.nickname.orEmpty())
                    put("position", localContact.position.orEmpty())
                    put("line", localContact.line.orEmpty())
                    put("is_active", localContact.isActive)
                    put("is_dm_confirmed", localContact.isDmConfirmed)
                }
                val response = retrySend(idempotent = true, tag = "updateContact") { apiService.updateContact("eq.$resolvedId", updates) }
                if (response.isSuccessful && response.body()?.isNotEmpty() == true) {
                    contactDao.updateSyncStatus(resolvedId, true)
                    kotlin.Result.success(Unit)
                } else if (response.code() == 403) {
                    syncManager.markBlocked("contact", resolvedId)
                    kotlin.Result.failure(Exception("แก้ไขผู้ติดต่อไม่สำเร็จ: ไม่มีสิทธิ์ทำรายการนี้"))
                } else {
                    syncManager.scheduleSync()
                    networkMonitor.queuedOrFailed(Unit, "เซิร์ฟเวอร์ตอบ ${response.code()}")
                }
            } catch (e: IOException) {
                syncManager.scheduleSync()
                networkMonitor.queuedOrFailed(Unit, e.message)
            } catch (e: Exception) {
                kotlin.Result.failure(e)
            }
        }
    }

    suspend fun getContactById(id: String): ContactPerson? {
        val resolvedId = localIdMappingDao.resolveMappedId(LocalIdMapping.ENTITY_CONTACT, id)
        return contactDao.getContactById(resolvedId)
    }

    suspend fun getContactsByCustomerId(custId: String): List<ContactPerson> {
        val resolvedId = localIdMappingDao.resolveMappedId(LocalIdMapping.ENTITY_CUSTOMER, custId)
        return contactDao.getContactsByCustomerId(resolvedId)
    }
}
