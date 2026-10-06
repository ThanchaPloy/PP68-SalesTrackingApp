package com.example.pp68_salestrackingapp.data.repository

import com.example.pp68_salestrackingapp.data.local.ContactDao
import com.example.pp68_salestrackingapp.data.local.CustomerDao
import com.example.pp68_salestrackingapp.data.local.LocalIdMappingDao
import com.example.pp68_salestrackingapp.data.model.ContactPerson
import com.example.pp68_salestrackingapp.data.remote.ApiService
import com.example.pp68_salestrackingapp.di.TokenManager
import com.example.pp68_salestrackingapp.utils.NetworkMonitor
import com.example.pp68_salestrackingapp.utils.SyncManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
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
    private val localIdMappingDao: LocalIdMappingDao = mockk(relaxed = true)
    private val tokenManager: TokenManager = mockk(relaxed = true)
    private val syncManager: SyncManager = mockk(relaxed = true)
    private val networkMonitor: NetworkMonitor = mockk(relaxed = true)
    private lateinit var repo: ContactRepository

    private fun contact(id: String) = ContactPerson(
        contactId = id,
        custId = "ERP-1",
        customerName = "ERP Company",
        fullName = "Contact $id"
    )

    @Before
    fun setUp() {
        repo = ContactRepository(
            apiService,
            contactDao,
            customerDao,
            localIdMappingDao,
            tokenManager,
            syncManager,
            networkMonitor
        )
    }

    @Test
    fun `creator scoped response replaces the local set`() = runTest {
        coEvery { apiService.getContactPersons(null, 5000) } returns
            Response.success(listOf(contact("CT-1")))

        val result = repo.refreshContacts()

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { contactDao.clearAndInsert(any()) }
        coVerify(exactly = 0) { contactDao.insertAll(any()) }
        coVerify(exactly = 0) { apiService.getContactsByCustomerIds(any(), any()) }
    }

    @Test
    fun `failed creator scoped response must not wipe stored contacts`() = runTest {
        coEvery { apiService.getContactPersons(null, 5000) } returns
            Response.error(500, "boom".toResponseBody("text/plain".toMediaType()))

        val result = repo.refreshContacts()

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { contactDao.clearAndInsert(any()) }
        coVerify(exactly = 0) { contactDao.insertAll(any()) }
    }

    @Test
    fun `thrown request must not wipe stored contacts`() = runTest {
        coEvery { apiService.getContactPersons(null, 5000) } throws RuntimeException("offline")

        val result = repo.refreshContacts()

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { contactDao.clearAndInsert(any()) }
    }

    @Test
    fun `response at server limit must merge without clearing local contacts`() = runTest {
        val fullPage = (1..5000).map { contact("CT-$it") }
        coEvery { apiService.getContactPersons(null, 5000) } returns Response.success(fullPage)

        val result = repo.refreshContacts()

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { contactDao.clearAndInsert(any()) }
        coVerify(exactly = 1) { contactDao.insertAll(match { it.size == 5000 }) }
    }
}
