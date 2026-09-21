package com.example.pp68_salestrackingapp.data.repository

import com.example.pp68_salestrackingapp.data.local.ContactDao
import com.example.pp68_salestrackingapp.data.local.CustomerDao
import com.example.pp68_salestrackingapp.data.model.AuthUser
import com.example.pp68_salestrackingapp.data.model.ContactPerson
import com.example.pp68_salestrackingapp.data.remote.ApiService
import com.example.pp68_salestrackingapp.di.TokenManager
import com.example.pp68_salestrackingapp.utils.SyncManager
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class ContactRefreshTest {

    private val apiService: ApiService = mockk(relaxed = true)
    private val contactDao: ContactDao = mockk(relaxed = true)
    private val customerDao: CustomerDao = mockk(relaxed = true)
    private val tokenManager: TokenManager = mockk(relaxed = true)
    private val syncManager: SyncManager = mockk(relaxed = true)

    private lateinit var repo: ContactRepository

    // 120 ลูกค้า = 3 ก้อน (ก้อนละ 50)
    private val customerIds = (1..120).map { "C$it" }

    private fun contact(id: String) = ContactPerson(contactId = id, custId = "C1", fullName = "ผู้ติดต่อ $id")

    @Before
    fun setUp() {
        repo = ContactRepository(apiService, contactDao, customerDao, tokenManager, syncManager)
        every { tokenManager.getUserData() } returns AuthUser("U1", "u@test.com", "sale")
        coEvery { customerDao.getCustomerIdsByUserId("U1") } returns customerIds
    }

    @Test
    fun `every chunk succeeding replaces the local set`() = runTest {
        coEvery { apiService.getContactsByCustomerIds(any(), any()) } returns
            Response.success(listOf(contact("CT-1")))

        val result = repo.refreshContacts()

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { contactDao.clearAndInsert(any()) }
        coVerify(exactly = 0) { contactDao.insertAll(any()) }
    }

    // เคสที่เคยพัง: ก้อนหลังล้มกลางคัน แต่โค้ดยัง clearAndInsert ด้วยข้อมูลที่ไม่ครบ
    // ทำให้ผู้ติดต่อของก้อนที่ดึงไม่สำเร็จหายไปจากแอปเงียบ ๆ
    @Test
    fun `a failed chunk must not wipe contacts that are already stored`() = runTest {
        var call = 0
        coEvery { apiService.getContactsByCustomerIds(any(), any()) } answers {
            call++
            if (call == 1) Response.success(listOf(contact("CT-1")))
            else Response.error(500, "boom".toResponseBody("text/plain".toMediaType()))
        }

        val result = repo.refreshContacts()

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { contactDao.clearAndInsert(any()) }
        coVerify(exactly = 1) { contactDao.insertAll(any()) }
    }

    @Test
    fun `a thrown chunk is treated the same as a failed one`() = runTest {
        coEvery { apiService.getContactsByCustomerIds(any(), any()) } throws RuntimeException("offline")

        val result = repo.refreshContacts()

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { contactDao.clearAndInsert(any()) }
    }
}
