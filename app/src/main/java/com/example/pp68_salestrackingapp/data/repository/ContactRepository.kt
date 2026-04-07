package com.example.pp68_salestrackingapp.data.repository

import com.example.pp68_salestrackingapp.data.local.ContactDao
import com.example.pp68_salestrackingapp.data.model.ContactPerson
import com.example.pp68_salestrackingapp.data.remote.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import java.io.IOException

class ContactRepository @Inject constructor(
    private val apiService: ApiService,
    private val contactDao: ContactDao
) {
    fun getAllContactsFlow(): Flow<List<ContactPerson>> = contactDao.getAllContacts()
    fun searchContactsFlow(query: String): Flow<List<ContactPerson>> = contactDao.searchContacts("%$query%")

    suspend fun refreshContacts(userId: String): kotlin.Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val memberResp = apiService.getMyProjectIds(userId = "eq.$userId")
                if (!memberResp.isSuccessful || memberResp.body().isNullOrEmpty()) {
                    return@withContext kotlin.Result.success(Unit)
                }

                val projectIds = memberResp.body()!!.map { it.projectId }
                val idsParam = "in.(${projectIds.joinToString(",")})"

                val projectResp = apiService.getProjectsByIds(projectIds = idsParam)
                if (!projectResp.isSuccessful || projectResp.body().isNullOrEmpty()) {
                    return@withContext kotlin.Result.success(Unit)
                }

                val custIds = projectResp.body()!!.map { it.custId }.distinct()
                val custIdsParam = "in.(${custIds.joinToString(",")})"

                val contactResp = apiService.getContactsByCustomerIds(custIds = custIdsParam)
                if (contactResp.isSuccessful && contactResp.body() != null) {
                    contactDao.clearAndInsert(contactResp.body()!!)
                    kotlin.Result.success(Unit)
                } else {
                    kotlin.Result.failure(Exception("โหลด Contact ไม่สำเร็จ: HTTP ${contactResp.code()}"))
                }
            } catch (e: Exception) {
                kotlin.Result.failure(Exception("Network Error: ${e.message}"))
            }
        }
    }

    suspend fun addContact(contact: ContactPerson): kotlin.Result<Unit> {
        return withContext(Dispatchers.IO) {
            // 1. บันทึกลงเครื่องทันที
            contactDao.insertAll(listOf(contact))
            try {
                // 2. พยายามส่งขึ้น Server
                val response = apiService.addContact(contact)
                if (response.isSuccessful) {
                    kotlin.Result.success(Unit)
                } else {
                    val errBody = response.errorBody()?.string() ?: ""
                    kotlin.Result.failure(Exception("Server Rejected: $errBody"))
                }
            } catch (e: Exception) {
                // 3. ถ้า Error เพราะ Network (Timeout/Offline) ให้ผ่าน
                if (e is IOException) kotlin.Result.success(Unit)
                else kotlin.Result.failure(e)
            }
        }
    }
}
