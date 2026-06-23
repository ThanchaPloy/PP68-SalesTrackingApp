package com.example.pp68_salestrackingapp.data.repository

import com.example.pp68_salestrackingapp.data.local.ContactDao
import com.example.pp68_salestrackingapp.data.local.CustomerDao
import com.example.pp68_salestrackingapp.data.model.ContactPerson
import com.example.pp68_salestrackingapp.data.remote.ApiService
import com.example.pp68_salestrackingapp.di.TokenManager
import com.example.pp68_salestrackingapp.utils.SyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import java.io.IOException

class ContactRepository @Inject constructor(
    private val apiService: ApiService,
    private val contactDao: ContactDao,
    private val customerDao: CustomerDao,
    private val tokenManager: TokenManager,
    private val syncManager: SyncManager
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
                    val contacts = resp.body()!!.map { it.copy(isSynced = true) }
                    contactDao.clearAndInsert(contacts)
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
            val localContact = contact.copy(isSynced = false)
            contactDao.insertContact(localContact)
            try {
                val fields = buildMap<String, Any?> {
                    put("customer_code", localContact.custId)
                    localContact.fullName?.let { put("contact_name", it) }
                    localContact.phoneNumber?.let { put("mobile_phone", it) }
                    localContact.email?.let { put("email", it) }
                }
                val response = apiService.addContact(fields)
                if (response.isSuccessful) {
                    val serverContact = response.body()?.firstOrNull()
                    if (serverContact != null && serverContact.contactId != localContact.contactId) {
                        // PostgREST generated a numeric id — replace the local-id record in Room
                        contactDao.deleteContactById(localContact.contactId)
                        contactDao.insertContact(serverContact.copy(isSynced = true))
                    } else {
                        contactDao.updateSyncStatus(localContact.contactId, true)
                    }
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

    suspend fun updateContact(contactId: String, contact: ContactPerson): kotlin.Result<Unit> {
        return withContext(Dispatchers.IO) {
            val localContact = contact.copy(isSynced = false)
            contactDao.insertContact(localContact)
            try {
                val updates = buildMap<String, Any?> {
                    put("contact_name", contact.fullName)
                    put("mobile_phone", contact.phoneNumber)
                    put("email", contact.email)
                    put("nickname", contact.nickname)
                    put("position", contact.position)
                    put("line", contact.line)
                    put("is_active", contact.isActive)
                    put("is_dm_confirmed", contact.isDmConfirmed)
                }
                val response = apiService.updateContact("eq.$contactId", updates)
                if (response.isSuccessful) {
                    contactDao.updateSyncStatus(contactId, true)
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

    suspend fun getContactById(id: String): ContactPerson? = contactDao.getContactById(id)

    suspend fun getContactsByCustomerId(custId: String): List<ContactPerson> =
        contactDao.getContactsByCustomerId(custId)
}
