package com.example.pp68_salestrackingapp.data.repository

import com.example.pp68_salestrackingapp.data.local.ContactDao
import com.example.pp68_salestrackingapp.data.local.CustomerDao
import com.example.pp68_salestrackingapp.data.model.ContactPerson
import com.example.pp68_salestrackingapp.data.remote.ApiService
import com.example.pp68_salestrackingapp.di.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import java.io.IOException

class ContactRepository @Inject constructor(
    private val apiService: ApiService,
    private val contactDao: ContactDao,
    private val customerDao: CustomerDao,
    private val tokenManager: TokenManager
) {
    fun getAllContactsFlow(): Flow<List<ContactPerson>> = contactDao.getAllContacts()
    fun searchContactsFlow(query: String): Flow<List<ContactPerson>> = contactDao.searchContactsWithCompany("%$query%")

    suspend fun refreshContacts(): kotlin.Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val userId = tokenManager.getUserData()?.userId
                val customerIds = if (!userId.isNullOrBlank())
                    customerDao.getCustomerIdsByUserId(userId)
                else
                    customerDao.getAllCustomerIds()

                if (customerIds.isEmpty()) return@withContext kotlin.Result.success(Unit)

                val codesParam = "in.(${customerIds.joinToString(",")})"
                val resp = apiService.getContactsByCustomerIds(custIds = codesParam)
                if (resp.isSuccessful && resp.body() != null) {
                    contactDao.clearAndInsert(resp.body()!!)
                    kotlin.Result.success(Unit)
                } else {
                    kotlin.Result.failure(Exception("โหลด Contact ไม่สำเร็จ: HTTP ${resp.code()}"))
                }
            } catch (e: Exception) {
                kotlin.Result.failure(Exception("Network Error: ${e.message}"))
            }
        }
    }

    suspend fun addContact(contact: ContactPerson): kotlin.Result<Unit> {
        return withContext(Dispatchers.IO) {
            contactDao.insertAll(listOf(contact))
            try {
                val response = apiService.addContact(contact)
                if (response.isSuccessful) kotlin.Result.success(Unit)
                else kotlin.Result.failure(Exception(response.errorBody()?.string()))
            } catch (e: IOException) {
                kotlin.Result.success(Unit)
            } catch (e: Exception) {
                kotlin.Result.failure(e)
            }
        }
    }

    suspend fun updateContact(contactId: String, contact: ContactPerson): kotlin.Result<Unit> {
        return withContext(Dispatchers.IO) {
            contactDao.insertAll(listOf(contact))
            try {
                val updates = buildMap<String, Any?> {
                    put("contact_name", contact.fullName)
                    put("mobile_phone", contact.phoneNumber)
                    put("email", contact.email)
                }
                val response = apiService.updateContact("eq.$contactId", updates)
                if (response.isSuccessful) kotlin.Result.success(Unit)
                else kotlin.Result.failure(Exception(response.errorBody()?.string()))
            } catch (e: IOException) {
                kotlin.Result.success(Unit)
            } catch (e: Exception) {
                kotlin.Result.failure(e)
            }
        }
    }

    suspend fun getContactById(id: String): ContactPerson? = contactDao.getContactById(id)
}
