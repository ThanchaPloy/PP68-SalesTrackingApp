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
            val userId = user?.userId?.trim() // ทำความสะอาด ID

            // ✅ แนบเจ้าของข้อมูลเข้าไป และทำความสะอาดข้อมูลอื่นๆ
            val contactWithOwner = contact.copy(
                userId = userId,
                fullName = contact.fullName?.trim(),
                phoneNumber = contact.phoneNumber?.trim(),
                email = contact.email?.trim()
            )

            // 1. บันทึกลงเครื่องทันที
            contactDao.insertAll(listOf(contactWithOwner))
            try {
                // 2. พยายามส่งขึ้น Server
                val response = apiService.addContact(contactWithOwner)
                if (response.isSuccessful) {
                    kotlin.Result.success(Unit)
                } else {
                    val errBody = response.errorBody()?.string() ?: ""
                    // Log error ให้เห็นชัดเจนใน Logcat
                    android.util.Log.e("ContactRepo", "Server Rejected: $errBody")
                    kotlin.Result.failure(Exception("Server Rejected: $errBody"))
                }
            } catch (e: Exception) {
                // 3. ถ้า Error เพราะ Network (Timeout/Offline) ให้ผ่านไปก่อน เดี๋ยว Sync ทีหลัง
                if (e is IOException) kotlin.Result.success(Unit)
                else kotlin.Result.failure(e)
            }
        }
    }
}
