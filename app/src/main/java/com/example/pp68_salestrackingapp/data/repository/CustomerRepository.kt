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

class CustomerRepository @Inject constructor(
    private val apiService: ApiService,
    private val customerDao: CustomerDao,
    private val contactDao: ContactDao,
    private val projectDao: ProjectDao,
    private val activityDao: ActivityDao
) {
    fun getAllCustomersFlow(): Flow<List<Customer>> = customerDao.getAllCustomers()
    fun searchCustomersFlow(query: String): Flow<List<Customer>> = customerDao.searchCustomers("%$query%")


    suspend fun getLocalCustomers(): kotlin.Result<List<Customer>> {
        return withContext(Dispatchers.IO) {
            try {
                // ✅ ดึงจาก Room DB ซึ่งมีแค่ customer ของ user นี้
                val list = customerDao.getAllCustomers().first()
                if (list.isNotEmpty()) {
                    kotlin.Result.success(list)
                } else {
                    kotlin.Result.failure(Exception("No local customers"))
                }
            } catch (e: Exception) {
                kotlin.Result.failure(e)
            }
        }
    }

    suspend fun refreshCustomers(userId: String): kotlin.Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val memberResp = apiService.getMyProjectIds(userId = "eq.$userId")
                if (!memberResp.isSuccessful || memberResp.body().isNullOrEmpty()) {
                    customerDao.clearAndInsert(emptyList()) // ✅ ล้างของเก่าก่อน
                    return@withContext kotlin.Result.success(Unit)
                }

                val projectIds = memberResp.body()!!.map { it.projectId }
                val idsParam = "in.(${projectIds.joinToString(",")})"

                val projectResp = apiService.getProjectsByIds(projectIds = idsParam)
                if (!projectResp.isSuccessful || projectResp.body().isNullOrEmpty()) {
                    customerDao.clearAndInsert(emptyList())
                    return@withContext kotlin.Result.success(Unit)
                }

                val custIds = projectResp.body()!!.map { it.custId }.distinct()
                val custIdsParam = "in.(${custIds.joinToString(",")})"

                val custResp = apiService.getCustomersByIds(custIds = custIdsParam)
                if (custResp.isSuccessful && custResp.body() != null) {
                    customerDao.clearAndInsert(custResp.body()!!) // ✅ clear ก่อน insert
                    kotlin.Result.success(Unit)
                } else {
                    kotlin.Result.failure(Exception("โหลด Customer ไม่สำเร็จ: HTTP ${custResp.code()}"))
                }
            } catch (e: Exception) {
                kotlin.Result.failure(Exception("Network Error: ${e.message}"))
            }
        }
    }

    suspend fun getCustomerById(customerId: String): kotlin.Result<Customer> {
        return withContext(Dispatchers.IO) {
            try {
                val local = customerDao.getAllCustomers().first().find { it.custId == customerId }
                if (local != null) return@withContext kotlin.Result.success(local)

                val response = apiService.getCustomerById("eq.$customerId")
                val body = response.body()
                if (response.isSuccessful && !body.isNullOrEmpty()) {
                    kotlin.Result.success(body.first())
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
                // ✅ ดึงจาก local เท่านั้น ไม่ fallback ไป API
                val local = customerDao.getAllCustomers().first()
                kotlin.Result.success(local)
            } catch (e: Exception) {
                kotlin.Result.failure(e)
            }
        }
    }

    suspend fun addCustomer(customer: Customer): kotlin.Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                // ✅ ตรวจสอบและจัดการรูปแบบวันที่ก่อนส่ง (หากจำเป็น)
                val response = apiService.addCustomer(customer)
                
                // บันทึก Local ทันที
                customerDao.insertCustomer(customer)
                
                if (response.isSuccessful) {
                    kotlin.Result.success(Unit)
                } else {
                    val errBody = response.errorBody()?.string() ?: ""
                    kotlin.Result.failure(Exception("บันทึกไม่สำเร็จ: HTTP ${response.code()} - $errBody"))
                }
            } catch (e: Exception) {
                // กรณี Offline
                customerDao.insertCustomer(customer)
                kotlin.Result.success(Unit)
            }
        }
    }

    suspend fun deleteCustomer(custId: String): kotlin.Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Delete associated data in correct order to avoid FK issues
                apiService.deleteActivitiesByCustomer("eq.$custId")
                
                val projects = projectDao.getProjectsByCustomer(custId).first()
                projects.forEach { 
                    apiService.deleteProjectMembers("eq.${it.projectId}")
                    apiService.deleteProjectContacts("eq.${it.projectId}")
                }
                apiService.deleteProjectsByCustomer("eq.$custId")
                apiService.deleteContactsByCustomer("eq.$custId")
                
                // 2. Delete customer from remote
                val response = apiService.deleteCustomer("eq.$custId")
                if (response.isSuccessful) {
                    // 3. Delete from local
                    customerDao.deleteCustomerById(custId)
                    contactDao.getContactsByCustomer(custId).first().forEach {
                        contactDao.deleteContactById(it.contactId)
                    }
                    projectDao.getProjectsByCustomer(custId).first().forEach {
                        projectDao.deleteProjectById(it.projectId)
                        activityDao.deleteActivitiesByProjectId(it.projectId)
                    }
                    kotlin.Result.success(Unit)
                } else {
                    kotlin.Result.failure(Exception("HTTP ${response.code()}"))
                }
            } catch (e: Exception) {
                kotlin.Result.failure(e)
            }
        }
    }

    suspend fun getContactPersons(customerId: String): kotlin.Result<List<ContactPerson>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getContactsByCustomerIds("eq.$customerId")
                if (response.isSuccessful && response.body() != null) {
                    val contacts = response.body()!!
                    // ✅ บันทึกลง Local DB เพื่อป้องกัน FOREIGN KEY constraint failed ในตารางอื่น
                    contactDao.insertContacts(contacts)
                    kotlin.Result.success(contacts)
                } else {
                    kotlin.Result.failure(Exception("ดึงข้อมูลผู้ติดต่อไม่สำเร็จ"))
                }
            } catch (e: Exception) {
                kotlin.Result.failure(e)
            }
        }
    }

    suspend fun deleteContact(contactId: String): kotlin.Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                // Should also handle activities referencing this contact if any
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
                // ดึง contact_person ทั้งหมดที่มี phone_number
                // return Map<phone_number, cust_id>
                contactDao.getAllContacts().first()
                    .filter  { !it.phoneNumber.isNullOrBlank() }
                    .associate { it.phoneNumber!! to it.custId }
            } catch (e: Exception) { emptyMap() }
        }
    }
}
