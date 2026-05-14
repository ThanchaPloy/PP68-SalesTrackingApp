package com.example.pp68_salestrackingapp.data.repository

import com.example.pp68_salestrackingapp.data.local.ContactDao
import com.example.pp68_salestrackingapp.data.local.CustomerDao
import com.example.pp68_salestrackingapp.data.local.ProjectDao
import com.example.pp68_salestrackingapp.data.local.ActivityDao
import com.example.pp68_salestrackingapp.data.model.ContactPerson
import com.example.pp68_salestrackingapp.data.model.Customer
import com.example.pp68_salestrackingapp.data.remote.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import java.io.IOException

class CustomerRepository @Inject constructor(
    private val apiService: ApiService,
    private val customerDao: CustomerDao,
    private val contactDao: ContactDao,
    private val projectDao: ProjectDao,
    private val activityDao: ActivityDao
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

    // ✅ ดึงลูกค้าทั้งหมดในสาขาเดียวกัน (filter by branch_id)
    suspend fun refreshCustomers(branchId: String): kotlin.Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val custResp = apiService.getCustomersByBranch(branchId = "eq.$branchId")
                if (custResp.isSuccessful && custResp.body() != null) {
                    customerDao.clearAndInsert(custResp.body()!!)
                    kotlin.Result.success(Unit)
                } else {
                    kotlin.Result.failure(Exception("โหลด Customer ไม่สำเร็จ: HTTP ${custResp.code()}"))
                }
            } catch (e: Exception) {
                kotlin.Result.failure(Exception("Network Error: ${e.message}"))
            }
        }
    }

    suspend fun getCustomerById(id: String): kotlin.Result<Customer> {
        return withContext(Dispatchers.IO) {
            try {
                val local = customerDao.getAllCustomers().first().find { it.custId == id }
                if (local != null) return@withContext kotlin.Result.success(local)

                val resp = apiService.getCustomerById("eq.$id")
                if (resp.isSuccessful && !resp.body().isNullOrEmpty()) {
                    val customer = resp.body()!!.first()
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

    // ✅ สร้าง Customer ใหม่ (POST) — ต้องส่ง branchId ด้วย
    suspend fun addCustomer(customer: Customer): kotlin.Result<Unit> {
        return withContext(Dispatchers.IO) {
            customerDao.insertCustomer(customer)
            try {
                val response = apiService.addCustomer(customer)
                if (response.isSuccessful) {
                    kotlin.Result.success(Unit)
                } else {
                    val errBody = response.errorBody()?.string() ?: ""
                    kotlin.Result.failure(Exception("Server Error: $errBody"))
                }
            } catch (e: IOException) {
                kotlin.Result.success(Unit) // offline mode
            } catch (e: Exception) {
                kotlin.Result.failure(e)
            }
        }
    }

    // ✅ อัปเดต Customer (PATCH) — ใช้ตอน edit mode
    suspend fun updateCustomer(custId: String, customer: Customer): kotlin.Result<Unit> {
        return withContext(Dispatchers.IO) {
            customerDao.insertCustomer(customer) // upsert local
            try {
                val updates = buildMap<String, Any?> {
                    put("company_name", customer.companyName)
                    put("branch_id", customer.branchId)
                    put("branch", customer.branch)
                    put("cust_type", customer.custType)
                    put("company_addr", customer.companyAddr)
                    put("company_lat", customer.companyLat)
                    put("company_long", customer.companyLong)
                    put("company_status", customer.companyStatus)
                    put("first_customer_date", customer.firstCustomerDate)
                }
                val response = apiService.updateCustomer("eq.$custId", updates)
                if (response.isSuccessful) {
                    kotlin.Result.success(Unit)
                } else {
                    val errBody = response.errorBody()?.string() ?: ""
                    kotlin.Result.failure(Exception("Server Error: $errBody"))
                }
            } catch (e: IOException) {
                kotlin.Result.success(Unit) // offline mode
            } catch (e: Exception) {
                kotlin.Result.failure(e)
            }
        }
    }

    suspend fun deleteCustomer(custId: String): kotlin.Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                apiService.deleteActivitiesByCustomer("eq.$custId")
                val projects = projectDao.getProjectsByCustomer(custId).first()
                projects.forEach {
                    apiService.deleteProjectMembers("eq.${it.projectId}")
                    apiService.deleteProjectContacts("eq.${it.projectId}")
                }
                apiService.deleteProjectsByCustomer("eq.$custId")
                apiService.deleteContactsByCustomer("eq.$custId")

                val response = apiService.deleteCustomer("eq.$custId")
                if (response.isSuccessful) {
                    customerDao.deleteCustomerById(custId)
                    kotlin.Result.success(Unit)
                } else {
                    kotlin.Result.failure(Exception("HTTP ${response.code()}"))
                }
            } catch (e: Exception) {
                kotlin.Result.failure(e)
            }
        }
    }

    // ✅ ดึง Contact ของ Customer — กรองเฉพาะที่ user คนนี้สร้าง (user_id)
    suspend fun getContactPersons(customerId: String, userId: String?): kotlin.Result<List<ContactPerson>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getContactsByCustomer(
                    custId    = "eq.$customerId",
                    createdBy = userId?.let { "eq.$it" }  // ✅ ส่ง user_id=eq.X ไปให้ PostgREST filter
                )
                if (response.isSuccessful && response.body() != null) {
                    val contacts = response.body()!!
                    contactDao.insertContacts(contacts)
                    kotlin.Result.success(contacts)
                } else {
                    // fallback local
                    val local = contactDao.getContactsByCustomer(customerId).first()
                    val filtered = if (userId != null) local.filter { it.createdBy == userId } else local
                    kotlin.Result.success(filtered)
                }
            } catch (e: Exception) {
                // fallback local
                val local = contactDao.getContactsByCustomer(customerId).first()
                val filtered = if (userId != null) local.filter { it.createdBy == userId } else local
                kotlin.Result.success(filtered)
            }
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