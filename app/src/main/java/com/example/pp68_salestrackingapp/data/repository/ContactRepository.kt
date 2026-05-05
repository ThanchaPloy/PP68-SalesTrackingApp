package com.example.pp68_salestrackingapp.data.repository

import com.example.pp68_salestrackingapp.data.local.ContactDao
import com.example.pp68_salestrackingapp.data.model.ContactPerson
import com.example.pp68_salestrackingapp.data.remote.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import java.io.IOException

class ContactRepository @Inject constructor(
    private val apiService: ApiService,
    private val contactDao: ContactDao,
    private val authRepo: AuthRepository
) {
    fun getAllContactsFlow(): Flow<List<ContactPerson>> = contactDao.getAllContacts()
    fun searchContactsFlow(query: String): Flow<List<ContactPerson>> = contactDao.searchContacts("%$query%")

    suspend fun refreshContacts(userId: String): kotlin.Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                // ทำความสะอาด userId ก่อนส่ง
                val cleanUserId = userId.trim()
                val contactResp = apiService.getContactsByUserId(userId = "eq.$cleanUserId")
                
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
            val user = authRepo.currentUser()
            val userId = user?.userId?.trim()

            val contactWithOwner = contact.copy(
                userId = userId,
                fullName = contact.fullName?.trim(),
                phoneNumber = contact.phoneNumber?.trim(),
                email = contact.email?.trim()
            )

            contactDao.insertContact(contactWithOwner)
            try {
                val response = apiService.addContact(contactWithOwner)
                if (response.isSuccessful) {
                    kotlin.Result.success(Unit)
                } else {
                    val errBody = response.errorBody()?.string() ?: ""
                    kotlin.Result.failure(Exception("Server Rejected: $errBody"))
                }
            } catch (e: Exception) {
                if (e is IOException) kotlin.Result.success(Unit)
                else kotlin.Result.failure(e)
            }
        }
    }

    suspend fun updateContact(contact: ContactPerson): kotlin.Result<Unit> {
        return withContext(Dispatchers.IO) {
            contactDao.insertContact(contact)
            try {
                val updates = mutableMapOf<String, Any?>()
                updates["full_name"]   = contact.fullName
                updates["nickname"]    = contact.nickname
                updates["position"]    = contact.position
                updates["phone_number"] = contact.phoneNumber
                updates["email"]       = contact.email
                updates["line"]        = contact.line
                updates["is_active"]   = contact.isActive
                updates["is_dm_confirmed"] = contact.isDmConfirmed

                val response = apiService.updateContact("eq.${contact.contactId}", updates)
                if (response.isSuccessful) {
                    kotlin.Result.success(Unit)
                } else {
                    val errBody = response.errorBody()?.string() ?: ""
                    kotlin.Result.failure(Exception("Update failed: $errBody"))
                }
            } catch (e: Exception) {
                if (e is IOException) kotlin.Result.success(Unit)
                else kotlin.Result.failure(e)
            }
        }
    }
}
