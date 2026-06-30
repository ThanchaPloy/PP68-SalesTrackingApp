package com.example.pp68_salestrackingapp.data.repository

import com.example.pp68_salestrackingapp.data.local.ContactDao
import com.example.pp68_salestrackingapp.data.local.CustomerDao
import com.example.pp68_salestrackingapp.data.local.ProjectDao
import com.example.pp68_salestrackingapp.data.local.ActivityDao
import com.example.pp68_salestrackingapp.data.model.ContactPerson
import com.example.pp68_salestrackingapp.data.model.Customer
import com.example.pp68_salestrackingapp.data.remote.ApiService
import com.example.pp68_salestrackingapp.di.TokenManager
import com.example.pp68_salestrackingapp.utils.SyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import android.util.Log
import javax.inject.Inject
import java.io.IOException

class CustomerRepository @Inject constructor(
    private val apiService: ApiService,
    private val customerDao: CustomerDao,
    private val contactDao: ContactDao,
    private val projectDao: ProjectDao,
    private val activityDao: ActivityDao,
    private val tokenManager: TokenManager,
    private val syncManager: SyncManager
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
                val empType = tokenManager.getEmpType()
                val branchSuffix = branchId.takeLast(2)

                val empCodes: List<String> = if (empType == "Project") {
                    val resp = apiService.getProjectEmployeeCodes()
                    resp.body()?.mapNotNull { it["emp_code"] }?.filter { it.isNotBlank() } ?: emptyList()
                } else {
                    val resp = apiService.getEmployeeCodesByBranch(branchCode = "eq.$branchId")
                    resp.body()?.mapNotNull { it["emp_code"] }?.filter { it.isNotBlank() } ?: emptyList()
                }

                val customers = mutableListOf<Customer>()
                if (empCodes.isNotEmpty()) {
                    val codesParam = "in.(${empCodes.joinToString(",")})"
                    val custResp = apiService.getCustomersBySalespersonCodes(codes = codesParam)
                    customers.addAll(custResp.body() ?: emptyList())
                }

                val fallbackResp = apiService.getCustomersWithEmptySalesperson()
                val fallback = fallbackResp.body()
                    ?.filter { it.custId.takeLast(2).equals(branchSuffix, ignoreCase = true) }
                    ?: emptyList()
                customers.addAll(fallback)

                val deduped = customers.distinctBy { it.custId }.map { it.copy(isSynced = true) }
                customerDao.clearAndInsert(deduped)
                kotlin.Result.success(Unit)
            } catch (e: IOException) {
                kotlin.Result.success(Unit) // offline — Room data still valid
            } catch (e: Exception) {
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

    suspend fun addCustomer(customer: Customer): kotlin.Result<Unit> {
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
                    "company_lat"           to localCustomer.companyLat,
                    "company_long"          to localCustomer.companyLong,
                    "customer_status"       to localCustomer.companyStatus,
                    "create_date"           to localCustomer.createdAt,
                    "salesperson_code"      to localCustomer.createdBy,
                    "grade"                 to localCustomer.grade,
                    "vat_registration_no"   to localCustomer.vatRegistrationNo
                ).filterValues { it != null }
                val response = apiService.addCustomer(body)
                Log.d("CustomerRepo", "POST customer → HTTP ${response.code()}")
                if (response.isSuccessful) {
                    val realCustId = response.body()?.firstOrNull()?.custId
                    Log.d("CustomerRepo", "realCustId=$realCustId tempId=$tempId")
                    if (realCustId != null && realCustId != tempId) {
                        // server generated a new ID — replace TEMP record in Room
                        contactDao.updateCustIdForContacts(tempId, realCustId)
                        customerDao.deleteCustomerById(tempId)
                        customerDao.insertCustomer(localCustomer.copy(custId = realCustId, isSynced = true))
                    } else {
                        customerDao.updateSyncStatus(tempId, true)
                    }
                    kotlin.Result.success(Unit)
                } else {
                    val errBody = response.errorBody()?.string()
                    Log.e("CustomerRepo", "POST failed ${response.code()}: $errBody")
                    syncManager.scheduleSync()
                    kotlin.Result.success(Unit)
                }
            } catch (e: IOException) {
                syncManager.scheduleSync()
                kotlin.Result.success(Unit)
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
                    put("address", customer.companyAddr)
                    put("customer_status", customer.companyStatus)
                    put("create_date", customer.createdAt)
                    put("salesperson_code", customer.createdBy)
                    put("grade", customer.grade)
                    put("vat_registration_no", customer.vatRegistrationNo)
                }.filterValues { it != null }
                val response = apiService.updateCustomer("eq.$custId", updates)
                if (response.isSuccessful) {
                    customerDao.updateSyncStatus(custId, true)
                    kotlin.Result.success(Unit)
                } else {
                    syncManager.scheduleSync()
                    kotlin.Result.success(Unit)
                }
            } catch (e: IOException) {
                syncManager.scheduleSync()
                kotlin.Result.success(Unit)
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
