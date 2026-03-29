package com.example.pp68_salestrackingapp.data.repository

import com.example.pp68_salestrackingapp.data.local.ContactDao
import com.example.pp68_salestrackingapp.data.model.ContactPerson
import com.example.pp68_salestrackingapp.data.remote.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

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
                    contactDao.clearAndInsert(contactResp.body()!!) // ✅ clear ก่อน insert
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
            try {
                val response = apiService.addContact(contact)
                contactDao.insertAll(listOf(contact))
                if (response.isSuccessful) kotlin.Result.success(Unit)
                else kotlin.Result.failure(Exception("HTTP ${response.code()}: ${response.errorBody()?.string()}"))
            } catch (e: Exception) {
                kotlin.Result.failure(e)
            }
        }
    }
}