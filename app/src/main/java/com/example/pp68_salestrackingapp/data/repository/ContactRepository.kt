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
import android.util.Log
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

                val allContacts = mutableListOf<ContactPerson>()
                val chunks = customerIds.chunked(50)
                for (chunk in chunks) {
                    try {
                        val batchQuery = "in.(" + chunk.joinToString(",") + ")"
                        val resp = apiService.getContactsByCustomerIds(custIds = batchQuery)
                        if (resp.isSuccessful && resp.body() != null) {
                            allContacts.addAll(resp.body()!!)
                        }
                    } catch (e: Exception) {
                        Log.e("ContactRepo", "Batch contact fetch error: ${e.message}")
                    }
                }

                val deduped = allContacts.distinctBy { it.contactId }.map { it.copy(isSynced = true) }
                if (deduped.isNotEmpty()) {
                    contactDao.clearAndInsert(deduped)
                }
                kotlin.Result.success(Unit)
            } catch (e: Exception) {
                Log.e("ContactRepo", "refreshContacts error: ${e.message}", e)
                kotlin.Result.failure(e)
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
                    localContact.nickname?.let { put("nickname", it) }
                    localContact.position?.let { put("position", it) }
                    localContact.line?.let { put("line", it) }
                    put("is_active", localContact.isActive)
                    put("is_dm_confirmed", localContact.isDmConfirmed)
                }
                val response = apiService.addContact(fields)
                Log.d("ContactRepo", "POST contact → HTTP ${response.code()}, custId=${localContact.custId}")
                if (response.isSuccessful) {
                    val serverContact = response.body()?.firstOrNull()
                    Log.d("ContactRepo", "serverContactId=${serverContact?.contactId} localId=${localContact.contactId}")
                    if (serverContact != null && serverContact.contactId != localContact.contactId) {
                        // server generated real contact_id — replace TEMP record
                        contactDao.deleteContactById(localContact.contactId)
                        contactDao.insertContact(serverContact.copy(isSynced = true))
                    } else {
                        contactDao.updateSyncStatus(localContact.contactId, true)
                    }
                    kotlin.Result.success(Unit)
                } else {
                    val errBody = response.errorBody()?.string()
                    Log.e("ContactRepo", "POST failed ${response.code()}: $errBody")
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
                }.filterValues { it != null }
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
