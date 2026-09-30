package com.example.pp68_salestrackingapp.data.repository

import com.example.pp68_salestrackingapp.data.local.ContactDao
import com.example.pp68_salestrackingapp.data.local.CustomerDao
import com.example.pp68_salestrackingapp.data.local.ProjectDao
import com.example.pp68_salestrackingapp.data.local.ActivityDao
import com.example.pp68_salestrackingapp.data.model.ContactPerson
import com.example.pp68_salestrackingapp.data.model.Customer
import com.example.pp68_salestrackingapp.data.remote.ApiService
import com.example.pp68_salestrackingapp.data.remote.AuthService
import com.example.pp68_salestrackingapp.di.TokenManager
import com.example.pp68_salestrackingapp.utils.SyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import android.util.Log
import javax.inject.Inject
import java.io.IOException
import com.example.pp68_salestrackingapp.utils.queuedOrFailed
import com.example.pp68_salestrackingapp.utils.retrySend

class CustomerRepository @Inject constructor(
    private val apiService: ApiService,
    private val authService: AuthService,
    private val customerDao: CustomerDao,
    private val contactDao: ContactDao,
    private val projectDao: ProjectDao,
    private val activityDao: ActivityDao,
    private val tokenManager: TokenManager,
    private val syncManager: SyncManager,
    private val networkMonitor: com.example.pp68_salestrackingapp.utils.NetworkMonitor
) {
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
                val currentUserId = tokenManager.getUserData()?.userId ?: ""
                val customers = mutableListOf<Customer>()

                // 1. Fetch current user's own customers FIRST & insert into Room immediately
                if (currentUserId.isNotBlank()) {
                    val ownCustResp = authService.getCustomers(
                        salespersonCode = "eq.$currentUserId",
                        limit = 1000
                    )
                    if (ownCustResp.isSuccessful && ownCustResp.body() != null) {
                        val ownCustomers = ownCustResp.body()!!.map { it.copy(isSynced = true) }
                        customers.addAll(ownCustomers)
                        if (ownCustomers.isNotEmpty()) {
                            // ✅ ห้ามใช้ clearAndInsert ตรงนี้ — มันลบลูกค้าสาขาอื่นที่เคยแคชไว้จาก
                            // การ refresh รอบก่อนทิ้งทันที ถ้า phase 2 (ดึงทั้งสาขา) ด้านล่างพังหรือ
                            // ออฟไลน์ต่อ จะไม่มีทาง insert กลับมาอีกเลย ทั้งที่ catch (IOException)
                            // ด้านล่างบอกว่า "Room data still valid" ซึ่งจะเป็นเท็จทันทีถ้าใช้ clearAndInsert ที่นี่
                            customerDao.insertCustomers(ownCustomers)
                        }
                    }
                }

                // 2. Fetch branch team member customers in background to enrich local database
                var branchFetchOk = branchId.isBlank() // ไม่มีสาขาให้ดึง = ไม่ถือว่าพลาด
                if (branchId.isNotBlank()) {
                    val custResp = authService.getCustomers(branchId = "eq.$branchId", limit = 5000)
                    if (custResp.isSuccessful && custResp.body() != null) {
                        customers.addAll(custResp.body()!!.map { it.copy(isSynced = true) })
                        branchFetchOk = true
                    }
                }

                val deduped = customers.distinctBy { it.custId }.map { it.copy(isSynced = true) }
                if (deduped.isNotEmpty()) {
                    // ✅ clearAndInsert = "รายการนี้คือทั้งหมดที่มี" ซึ่งจริงเฉพาะตอนที่ดึงครบทั้งสองรอบ
                    // ถ้ารอบสอง (ทั้งสาขา) พัง เราจะเหลือแค่ลูกค้าของตัวเอง แล้วการล้างทิ้งจะลบลูกค้า
                    // ของเพื่อนร่วมสาขาที่แคชไว้รอบก่อนหายไปหมด ทั้งที่ไม่มีอะไรบอกว่ามันถูกลบจริง
                    // — merge แทน แล้วรอให้รอบถัดไปที่สำเร็จครบเป็นคนล้างของที่ไม่มีแล้วออก
                    if (branchFetchOk) customerDao.clearAndInsert(deduped)
                    else customerDao.insertCustomers(deduped)
                }
                kotlin.Result.success(Unit)
            } catch (e: IOException) {
                kotlin.Result.success(Unit) // offline — Room data still valid
            } catch (e: Exception) {
                Log.e("CustomerRepo", "refreshCustomers error: ${e.message}", e)
                kotlin.Result.failure(e)
            }
        }
    }

    suspend fun getCustomerById(id: String): kotlin.Result<Customer> {
        return withContext(Dispatchers.IO) {
            try {
                val local = customerDao.getCustomerById(id)
                if (local != null) return@withContext kotlin.Result.success(local)

                val resp = apiService.getCustomerById("eq.$id")
                if (resp.isSuccessful && !resp.body().isNullOrEmpty()) {
                    val customer = resp.body()!!.first().copy(isSynced = true)
                    customerDao.insertCustomer(customer)
                    kotlin.Result.success(customer)
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
                val local = customerDao.getAllCustomers().first()
                kotlin.Result.success(local)
            } catch (e: Exception) {
                kotlin.Result.failure(e)
            }
        }
    }

    suspend fun addCustomer(customer: Customer): kotlin.Result<String> {
        return withContext(Dispatchers.IO) {
            val today = java.time.LocalDate.now().toString()
            val tempId = customer.custId
            val localCustomer = customer.copy(isSynced = false, createdAt = customer.createdAt ?: today)
            customerDao.insertCustomer(localCustomer)
            try {
                val body = mutableMapOf<String, Any?>(
                    "customer_name"         to localCustomer.companyName,
                    "gen_bus_posting_group" to localCustomer.branchId,
                    "cust_type"             to localCustomer.custType,
                    "address"               to localCustomer.companyAddr,
                    "latitude"              to localCustomer.companyLat,
                    "longitude"             to localCustomer.companyLong,
                    "customer_status"       to localCustomer.companyStatus,
                    "create_date"           to localCustomer.createdAt,
                    "created_at"            to localCustomer.createdAt,
                    "create_by"             to localCustomer.createdBy,
                    "salesperson_code"      to localCustomer.createdBy,
                    "grade"                 to localCustomer.grade,
                    "vat_registration_no"   to localCustomer.vatRegistrationNo
                ).filterValues { it != null }
                val response = retrySend(idempotent = false, tag = "addCustomer") { apiService.addCustomer(body) }
                Log.d("CustomerRepo", "POST customer → HTTP ${response.code()}")
                if (response.isSuccessful) {
                    val realCustId = response.body()?.firstOrNull()?.custId
                    Log.d("CustomerRepo", "realCustId=$realCustId tempId=$tempId")
                    if (realCustId != null && realCustId != tempId) {
                        // server generated a new ID — replace TEMP record in Room
                        contactDao.updateCustIdForContacts(tempId, realCustId)
                        customerDao.deleteCustomerById(tempId)
                        customerDao.insertCustomer(localCustomer.copy(custId = realCustId, isSynced = true, isLead = true))
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
            val localCustomer = customer.copy(isSynced = false)
            customerDao.insertCustomer(localCustomer)
            try {
                val updates = buildMap<String, Any?> {
                    put("customer_name", customer.companyName)
                    put("gen_bus_posting_group", customer.branchId)
                    put("cust_type", customer.custType)
                    put("address", customer.companyAddr)
                    put("latitude", customer.companyLat)
                    put("longitude", customer.companyLong)
                    put("customer_status", customer.companyStatus)
                    put("create_date", customer.createdAt)
                    put("created_at", customer.createdAt)
                    put("create_by", customer.createdBy)
                    put("salesperson_code", customer.createdBy)
                    put("grade", customer.grade)
                    put("vat_registration_no", customer.vatRegistrationNo)
                }.filterValues { it != null }
                val response = retrySend(idempotent = true, tag = "updateCustomer") {
                    if (customer.isLead) apiService.updateLeadCustomer("eq.$custId", updates)
                    else apiService.updateCustomer("eq.$custId", updates)
                }
                if (response.isSuccessful && response.body()?.isNotEmpty() == true) {
                    customerDao.updateSyncStatus(custId, true)
                    kotlin.Result.success(Unit)
                } else if (response.code() == 403) {
                    syncManager.markBlocked("customer", custId)
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
                // ทุกขั้นของ cascade ต้องสำเร็จก่อนจะลบลูกค้า — เดิมทิ้งผลลัพธ์ของขั้นก่อนหน้าไว้
                // ถ้าขั้นไหนพังเงียบ ๆ แล้วลบลูกค้าสำเร็จ จะเหลือนัดหมาย/โปรเจคกำพร้าบน server
                // ที่ชี้ไปหาลูกค้าที่ไม่มีอยู่แล้ว และ server ไม่มี FK คอยดักให้
                val cascade = mutableListOf<Pair<String, retrofit2.Response<*>>>()
                cascade += "นัดหมาย" to apiService.deleteActivitiesByCustomer("eq.$custId")
                val projects = projectDao.getProjectsByCustomer(custId).first()
                projects.forEach {
                    cascade += "ผู้ติดต่อของโครงการ" to apiService.deleteProjectContacts("eq.${it.projectId}")
                }
                cascade += "โครงการ" to apiService.deleteProjectsByCustomer("eq.$custId")
                cascade += "ผู้ติดต่อ" to apiService.deleteContactsByCustomer("eq.$custId")

                val failed = cascade.firstOrNull { !it.second.isSuccessful }
                if (failed != null) {
                    return@withContext kotlin.Result.failure(
                        Exception("ลบ${failed.first}ไม่สำเร็จ (HTTP ${failed.second.code()}) จึงยังไม่ลบลูกค้า")
                    )
                }

                val response = apiService.deleteCustomer("eq.$custId")
                if (response.isSuccessful) {
                    customerDao.deleteCustomerById(custId)
                    // ✅ Room ไม่มี FK CASCADE ให้ตารางพวกนี้ — ลบลูกค้าเสร็จแล้วต้องเก็บกวาดแถวกำพร้า
                    // ในเครื่องเองด้วย ไม่งั้นโครงการ/นัดหมาย/ผู้ติดต่อของลูกค้าที่ลบไปแล้วยังค้างอยู่
                    projectDao.deleteProjectsByCustomerId(custId)
                    activityDao.deleteActivitiesByCustomerId(custId)
                    contactDao.deleteContactsByCustomerId(custId)
                    kotlin.Result.success(Unit)
                } else {
                    kotlin.Result.failure(Exception("HTTP ${response.code()}"))
                }
            } catch (e: Exception) {
                kotlin.Result.failure(e)
            }
        }
    }

    suspend fun getContactPersons(customerId: String, userId: String? = null): kotlin.Result<List<ContactPerson>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getContactsByCustomer(custId = "eq.$customerId")
                if (response.isSuccessful && response.body() != null) {
                    contactDao.insertAll(response.body()!!.map { it.copy(isSynced = true) })
                }
            } catch (_: Exception) { /* offline — ใช้ local */ }
            val all = contactDao.getContactsByCustomer(customerId).first()
            kotlin.Result.success(all)
        }
    }

    suspend fun deleteContact(contactId: String): kotlin.Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.deleteContact("eq.$contactId")
                if (response.isSuccessful) {
                    contactDao.deleteContactById(contactId)
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
