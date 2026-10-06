package com.example.pp68_salestrackingapp.data.repository

import com.example.pp68_salestrackingapp.data.local.ContactDao
import com.example.pp68_salestrackingapp.data.local.CustomerDao
import com.example.pp68_salestrackingapp.data.local.ProjectDao
import com.example.pp68_salestrackingapp.data.local.ActivityDao
import com.example.pp68_salestrackingapp.data.local.LocalIdMappingDao
import com.example.pp68_salestrackingapp.data.model.LocalIdMapping
import com.example.pp68_salestrackingapp.data.model.ContactPerson
import com.example.pp68_salestrackingapp.data.model.Customer
import com.example.pp68_salestrackingapp.data.model.CustomerLookup
import com.example.pp68_salestrackingapp.data.remote.ApiService
import com.example.pp68_salestrackingapp.data.remote.AuthService
import com.example.pp68_salestrackingapp.di.TokenManager
import com.example.pp68_salestrackingapp.utils.SyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
import com.example.pp68_salestrackingapp.data.remote.CreateRequestPayloads

class CustomerRepository @Inject constructor(
    private val apiService: ApiService,
    private val authService: AuthService,
    private val customerDao: CustomerDao,
    private val contactDao: ContactDao,
    private val projectDao: ProjectDao,
    private val activityDao: ActivityDao,
    private val localIdMappingDao: LocalIdMappingDao,
    private val tokenManager: TokenManager,
    private val syncManager: SyncManager,
    private val networkMonitor: com.example.pp68_salestrackingapp.utils.NetworkMonitor
) {
    fun getCustomersPagingFlow(
        searchQuery: String,
        bizGroup: String?,
        custType: String?,
        tabIndex: Int,
        initial: String?
    ): Flow<PagingData<Customer>> = Pager(
        config = PagingConfig(
            pageSize = 30,
            initialLoadSize = 60,
            prefetchDistance = 10,
            maxSize = 150,
            enablePlaceholders = false
        ),
        pagingSourceFactory = {
            customerDao.getCustomersPaging(
                searchQuery = searchQuery.trim(),
                bizGroup = bizGroup,
                custType = custType,
                tabIndex = tabIndex.coerceIn(0, 2),
                initial = initial
            )
        }
    ).flow

    fun getAllCustomersFlow(): Flow<List<Customer>> = customerDao.getAllCustomers()
    fun searchCustomersFlow(query: String): Flow<List<Customer>> = customerDao.searchCustomers("%$query%")
    fun getAllContacts(): Flow<List<ContactPerson>> = contactDao.getAllContacts()

    suspend fun getLocalCustomers(): kotlin.Result<List<Customer>> {
        return withContext(Dispatchers.IO) {
            try {
                val list = customerDao.getAllCustomers().first()
                if (list.isNotEmpty()) kotlin.Result.success(list)
                else kotlin.Result.failure(Exception("No local customers"))
            } catch (e: Exception) {
                kotlin.Result.failure(e)
            }
        }
    }

    suspend fun refreshCustomers(branchId: String): kotlin.Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                // Backend derives branch/project-sales visibility from JWT. Only lead_customer
                // participates in the mobile snapshot; ERP customer rows are remote-only.
                val response = apiService.getLeads()
                if (!response.isSuccessful || response.body() == null) {
                    return@withContext kotlin.Result.failure(Exception("HTTP ${response.code()}"))
                }
                customerDao.replaceLeadSnapshot(response.body().orEmpty())
                kotlin.Result.success(Unit)
            } catch (e: IOException) {
                kotlin.Result.failure(e) // local cache ยังใช้ได้ แต่ download รอบนี้ไม่สำเร็จ
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("CustomerRepo", "refreshCustomers error: ${e.message}", e)
                kotlin.Result.failure(e)
            }
        }
    }

    suspend fun getCustomerById(id: String): kotlin.Result<Customer> {
        return withContext(Dispatchers.IO) {
            try {
                val resolvedId = localIdMappingDao.resolveMappedId(LocalIdMapping.ENTITY_CUSTOMER, id)
                val local = customerDao.getCustomerById(resolvedId)
                if (local != null) return@withContext kotlin.Result.success(local)

                val resp = apiService.lookupErpCustomer(resolvedId)
                if (resp.isSuccessful && resp.body() != null) {
                    val lookup = requireNotNull(resp.body())
                    // Display projection only. Never insert an ERP customer into Room.
                    kotlin.Result.success(
                        Customer(
                            custId = lookup.customerCode,
                            companyName = lookup.customerName,
                            isLead = false,
                            isSynced = true
                        )
                    )
                } else {
                    kotlin.Result.failure(Exception("ไม่พบข้อมูลลูกค้า"))
                }
            } catch (e: Exception) {
                kotlin.Result.failure(e)
            }
        }
    }

    suspend fun getCustomers(): kotlin.Result<List<Customer>> {
        return withContext(Dispatchers.IO) {
            try {
                val local = customerDao.getAllLeads().first()
                kotlin.Result.success(local)
            } catch (e: Exception) {
                kotlin.Result.failure(e)
            }
        }
    }

    suspend fun searchErpCustomers(
        query: String,
        after: String? = null,
        limit: Int = 20
    ): kotlin.Result<List<CustomerLookup>> = withContext(Dispatchers.IO) {
        val normalized = query.trim()
        if (normalized.length < 2) {
            return@withContext kotlin.Result.failure(Exception("กรุณาพิมพ์อย่างน้อย 2 ตัวอักษร"))
        }
        try {
            val response = apiService.searchErpCustomers(normalized, after, limit.coerceIn(1, 50))
            if (response.isSuccessful && response.body() != null) {
                kotlin.Result.success(requireNotNull(response.body()).items)
            } else {
                // server ตอบกลับมาจริง แค่ไม่สำเร็จ — ไม่ใช่เรื่องเน็ต ต้องโชว์โค้ดให้เห็น
                // (404 = backend ที่ deploy อยู่ยังไม่มี /customer/search, 5xx = server มีปัญหา)
                kotlin.Result.failure(Exception("ค้นหา ERP ไม่ได้ (HTTP ${response.code()}) แจ้งผู้ดูแลระบบได้เลย (Lead ในเครื่องยังเลือกได้)"))
            }
        } catch (e: IOException) {
            kotlin.Result.failure(Exception("ค้นหา ERP ไม่ได้ กรุณาตรวจสอบอินเทอร์เน็ต (Lead ในเครื่องยังเลือกได้)"))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            kotlin.Result.failure(e)
        }
    }

    suspend fun addCustomer(customer: Customer): kotlin.Result<String> {
        return withContext(Dispatchers.IO) {
            val today = java.time.LocalDate.now().toString()
            val tempId = customer.custId
            val localCustomer = customer.copy(
                isSynced = false,
                createdAt = customer.createdAt ?: today,
                operationId = customer.operationId ?: java.util.UUID.randomUUID().toString()
            )
            customerDao.insertCustomer(localCustomer)
            try {
                val body = CreateRequestPayloads.customer(localCustomer)
                val response = retrySend(idempotent = false, tag = "addCustomer") {
                    apiService.addCustomer(body, localCustomer.operationId)
                }
                Log.d("CustomerRepo", "POST customer → HTTP ${response.code()}")
                if (response.isSuccessful) {
                    val realCustId = response.body()?.firstOrNull()?.custId
                    Log.d("CustomerRepo", "realCustId=$realCustId tempId=$tempId")
                    if (realCustId != null && realCustId != tempId) {
                        // server generated a new ID — replace TEMP record in Room
                        localIdMappingDao.replaceTemporaryCustomer(
                            tempId,
                            localCustomer.copy(custId = realCustId, isSynced = true, isLead = true)
                        )
                    } else {
                        customerDao.updateSyncStatus(tempId, true)
                        customerDao.updateLeadStatus(tempId, true)
                    }
                    if (realCustId != null && realCustId != tempId) {
                        kotlin.Result.success(realCustId)
                    } else {
                        kotlin.Result.success(tempId)
                    }
                } else {
                    val errBody = response.errorBody()?.string()
                    Log.e("CustomerRepo", "POST failed ${response.code()}: $errBody")
                    if (response.code() == 403) {
                        // ❌ server ปฏิเสธถาวร ไม่ใช่ออฟไลน์ — ลองใหม่ไปก็ 403 ซ้ำทุกครั้ง
                        // ห้ามคืน success เพราะผู้ใช้จะเข้าใจว่าบันทึกสำเร็จทั้งที่ไม่มีวันถึง server
                        syncManager.markBlocked("customer", tempId)
                        kotlin.Result.failure(Exception("บันทึกลูกค้าไม่สำเร็จ: ไม่มีสิทธิ์ทำรายการนี้"))
                    } else {
                        syncManager.scheduleSync()
                        // ลูกค้าถูกบันทึกลงเครื่องแล้วและ outbox จะลองส่งใหม่ให้ — ถ้าไม่มีเน็ตจึงไม่ใช่
                        // ความล้มเหลวที่ผู้ใช้ต้องแก้ แต่ถ้าเน็ตดีอยู่แล้วยังส่งไม่ขึ้น ต้องบอกตามตรง
                        networkMonitor.queuedOrFailed(tempId, "เซิร์ฟเวอร์ตอบ ${response.code()}")
                    }
                }
            } catch (e: IOException) {
                syncManager.scheduleSync()
                networkMonitor.queuedOrFailed(tempId, e.message)
            } catch (e: Exception) {
                kotlin.Result.failure(e)
            }
        }
    }

    suspend fun updateCustomer(custId: String, customer: Customer): kotlin.Result<Unit> {
        return withContext(Dispatchers.IO) {
            val resolvedId = try {
                localIdMappingDao.resolveExistingId(LocalIdMapping.ENTITY_CUSTOMER, custId)
            } catch (e: Exception) {
                return@withContext kotlin.Result.failure(e)
            }
            val localCustomer = customer.copy(custId = resolvedId, isSynced = false)
            customerDao.insertCustomer(localCustomer)
            if (resolvedId.startsWith("TEMP-")) {
                syncManager.scheduleSync()
                return@withContext kotlin.Result.success(Unit)
            }
            try {
                val updates = buildMap<String, Any?> {
                    put("customer_name", customer.companyName)
                    put("gen_bus_posting_group", customer.bizPostingGroup)
                    put("cust_type", customer.custType)
                    put("address", customer.companyAddr)
                    put("latitude", customer.companyLat)
                    put("longitude", customer.companyLong)
                    put("customer_status", customer.companyStatus)
                    // ⚠️ ห้ามส่ง create_by / salesperson_code / create_date / created_at ตอนแก้ไข
                    //
                    // มันคือข้อมูลการสร้าง เจ้าของคือ server ตั้งให้ตอน POST ครั้งเดียว การส่งขึ้นไป
                    // ตอน PATCH เท่ากับเขียนทับ: ฝั่ง backend อนุญาตให้แก้ลูกค้าของเพื่อนร่วมสาขาได้
                    // (RouteUtils.allows) เมื่อค่าที่ส่งคือ "คนที่กำลังแก้" ลูกค้าจะเปลี่ยนมือไปเป็น
                    // ของคนแก้เงียบ ๆ แล้วหลุดจากรายการ "ลูกค้าของฉัน" ของเจ้าของเดิมทันที
                    // (refreshCustomers เฟสแรกดึงด้วย salespersonCode = eq.<ตัวเอง>)
                    // ส่วน create_date/created_at ที่ทับไปก็ทำให้ประวัติวันสร้างเพี้ยนไปด้วย
                    put("grade", customer.grade)
                }.filterValues { it != null }.toMutableMap().apply {
                    // ⚠️ filterValues ข้างบนตัด null ทิ้งหมด = "ล้างค่าไม่ได้เลย" ผู้ใช้ลบที่อยู่หรือ
                    // เลขผู้เสียภาษีในฟอร์มแล้วกดบันทึก (AddCustomerViewModel ส่ง .ifBlank { null })
                    // คีย์หายไปทั้งคู่ server คงค่าเดิมไว้ แล้ว refresh รอบหน้าดึงกลับมาทับ
                    // สองช่องนี้จึงต้องส่ง "" แทน null ให้ backend แปลงเป็น NULL — ข้อตกลงเดียวกับ
                    // ContactRepository.updateContact (Gson ของแอปไม่ได้เปิด serializeNulls
                    // คีย์ที่ค่าเป็น null จะถูกตัดออกจาก body ตั้งแต่ตอนแปลงเป็น JSON อยู่แล้ว)
                    // ฟิลด์ที่เหลือยังพึ่ง filterValues ต่อโดยตั้งใจ — ผู้เรียกไม่ได้ดูแลครบทุกช่อง
                    put("address", customer.companyAddr.orEmpty())
                    put("vat_registration_no", customer.vatRegistrationNo.orEmpty())
                }
                val response = retrySend(idempotent = true, tag = "updateCustomer") {
                    if (localCustomer.isLead) apiService.updateLeadCustomer("eq.$resolvedId", updates)
                    else apiService.updateCustomer("eq.$resolvedId", updates)
                }
                if (response.isSuccessful && response.body()?.isNotEmpty() == true) {
                    customerDao.updateSyncStatus(resolvedId, true)
                    kotlin.Result.success(Unit)
                } else if (response.code() == 403) {
                    syncManager.markBlocked("customer", resolvedId)
                    kotlin.Result.failure(Exception("แก้ไขลูกค้าไม่สำเร็จ: ไม่มีสิทธิ์ทำรายการนี้"))
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

    suspend fun deleteCustomer(custId: String): kotlin.Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val resolvedId = localIdMappingDao.resolveExistingId(LocalIdMapping.ENTITY_CUSTOMER, custId)
                if (resolvedId.startsWith("TEMP-")) {
                    customerDao.deleteCustomerById(resolvedId)
                    projectDao.deleteProjectsByCustomerId(resolvedId)
                    activityDao.deleteActivitiesByCustomerId(resolvedId)
                    contactDao.deleteContactsByCustomerId(resolvedId)
                    return@withContext kotlin.Result.success(Unit)
                }
                // ทุกขั้นของ cascade ต้องสำเร็จก่อนจะลบลูกค้า — เดิมทิ้งผลลัพธ์ของขั้นก่อนหน้าไว้
                // ถ้าขั้นไหนพังเงียบ ๆ แล้วลบลูกค้าสำเร็จ จะเหลือนัดหมาย/โปรเจคกำพร้าบน server
                // ที่ชี้ไปหาลูกค้าที่ไม่มีอยู่แล้ว และ server ไม่มี FK คอยดักให้
                val cascade = mutableListOf<Pair<String, retrofit2.Response<*>>>()
                cascade += "นัดหมาย" to apiService.deleteActivitiesByCustomer("eq.$resolvedId")
                val projects = projectDao.getProjectsByCustomer(resolvedId).first()
                projects.forEach {
                    cascade += "ผู้ติดต่อของโครงการ" to apiService.deleteProjectContacts("eq.${it.projectId}")
                }
                cascade += "โครงการ" to apiService.deleteProjectsByCustomer("eq.$resolvedId")
                cascade += "ผู้ติดต่อ" to apiService.deleteContactsByCustomer("eq.$resolvedId")

                val failed = cascade.firstOrNull { !it.second.isSuccessful }
                if (failed != null) {
                    return@withContext kotlin.Result.failure(
                        Exception("ลบ${failed.first}ไม่สำเร็จ (HTTP ${failed.second.code()}) จึงยังไม่ลบลูกค้า")
                    )
                }

                val response = apiService.deleteCustomer("eq.$resolvedId")
                if (response.isSuccessful) {
                    customerDao.deleteCustomerById(resolvedId)
                    // ✅ Room ไม่มี FK CASCADE ให้ตารางพวกนี้ — ลบลูกค้าเสร็จแล้วต้องเก็บกวาดแถวกำพร้า
                    // ในเครื่องเองด้วย ไม่งั้นโครงการ/นัดหมาย/ผู้ติดต่อของลูกค้าที่ลบไปแล้วยังค้างอยู่
                    projectDao.deleteProjectsByCustomerId(resolvedId)
                    activityDao.deleteActivitiesByCustomerId(resolvedId)
                    contactDao.deleteContactsByCustomerId(resolvedId)
                    kotlin.Result.success(Unit)
                } else {
                    kotlin.Result.failure(Exception("HTTP ${response.code()}"))
                }
            } catch (e: Exception) {
                kotlin.Result.failure(e)
            }
        }
    }

    /**
     * ผู้ติดต่อทั้งหมดของลูกค้ารายนี้ (ดึงจาก server ทับ Room ก่อนถ้าออนไลน์ แล้วอ่านจาก Room)
     *
     * ⚠️ [userId] ไม่ได้ถูกใช้กรองอะไรเลย — เคยมีเจตนาจะกรองให้เห็น "เฉพาะผู้ติดต่อที่ตนเองสร้าง"
     * แต่ไม่เคยมีโค้ดจริง ผู้ติดต่อเป็นของลูกค้า ไม่ใช่ของเซลส์คนใดคนหนึ่ง การเห็นครบจึงถูกแล้ว
     * (คงพารามิเตอร์ไว้เพราะมีจุดเรียกและเทสต์อ้างอยู่ประมาณ 20 แห่ง ถอดออกเท่ากับรื้อเทสต์
     * โดยผู้ใช้ไม่ได้อะไรเพิ่ม) — ถ้าวันหนึ่งต้องกรองจริง ต้องเขียน logic เพิ่ม ไม่ใช่แค่ส่งค่ามา
     */
    suspend fun getContactPersons(customerId: String, @Suppress("UNUSED_PARAMETER") userId: String? = null): kotlin.Result<List<ContactPerson>> {
        return withContext(Dispatchers.IO) {
            // ตัดช่องว่างหัวท้ายก่อนใช้ — รหัสที่ส่งมาจากหน้าจอผ่านมือผู้ใช้/ค่าที่ประกอบจากที่อื่นได้
            // (เป็นการกันไว้เฉย ๆ ไม่ได้แก้อาการที่เคยเจอ ซึ่งพิสูจน์แล้วว่ามาจากข้อมูลคนละบริษัท)
            val cleanId = localIdMappingDao.resolveMappedId(
                LocalIdMapping.ENTITY_CUSTOMER,
                customerId.trim()
            )
            try {
                val response = apiService.getContactsByCustomer(custId = "eq.$cleanId")
                if (response.isSuccessful && response.body() != null) {
                    contactDao.insertAll(response.body()!!.map { it.copy(isSynced = true) })
                }
            } catch (_: Exception) { /* offline — ใช้ local */ }
            val all = contactDao.getContactsByCustomer(cleanId).first()
            kotlin.Result.success(all)
        }
    }

    suspend fun deleteContact(contactId: String): kotlin.Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val resolvedId = localIdMappingDao.resolveExistingId(LocalIdMapping.ENTITY_CONTACT, contactId)
                if (resolvedId.startsWith("TEMP-")) {
                    contactDao.deleteContactById(resolvedId)
                    return@withContext kotlin.Result.success(Unit)
                }
                val response = apiService.deleteContact("eq.$resolvedId")
                if (response.isSuccessful) {
                    contactDao.deleteContactById(resolvedId)
                    kotlin.Result.success(Unit)
                } else {
                    kotlin.Result.failure(Exception("HTTP ${response.code()}"))
                }
            } catch (e: Exception) {
                kotlin.Result.failure(e)
            }
        }
    }

    suspend fun getAllContactPhoneMap(): Map<String, String> {
        return withContext(Dispatchers.IO) {
            try {
                contactDao.getAllContacts().first()
                    .filter { !it.phoneNumber.isNullOrBlank() }
                    .associate { it.phoneNumber!! to it.custId }
            } catch (e: Exception) {
                emptyMap()
            }
        }
    }
}
