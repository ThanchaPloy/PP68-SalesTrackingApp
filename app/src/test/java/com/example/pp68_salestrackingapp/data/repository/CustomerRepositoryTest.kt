package com.example.pp68_salestrackingapp.data.repository

import com.example.pp68_salestrackingapp.data.local.ContactDao
import com.example.pp68_salestrackingapp.data.local.CustomerDao
import com.example.pp68_salestrackingapp.data.local.ProjectDao
import com.example.pp68_salestrackingapp.data.local.ActivityDao
import com.example.pp68_salestrackingapp.data.model.ContactPerson
import com.example.pp68_salestrackingapp.data.model.Customer
import com.example.pp68_salestrackingapp.data.model.ProjectMemberDto
import com.example.pp68_salestrackingapp.data.model.Project
import com.example.pp68_salestrackingapp.data.remote.ApiService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class CustomerRepositoryExtendedTest {

    private lateinit var repository: CustomerRepository
    private val apiService  = mockk<ApiService>(relaxed = true)
    private val customerDao = mockk<CustomerDao>(relaxed = true)
    private val contactDao  = mockk<ContactDao>(relaxed = true)
    private val projectDao  = mockk<ProjectDao>(relaxed = true)
    private val activityDao = mockk<ActivityDao>(relaxed = true)
    private val authRepo    = mockk<AuthRepository>(relaxed = true)

    private val sampleCustomer = Customer(
        custId = "CST-001", companyName = "บริษัท แสนสิริ", branch = null,
        custType = "Developer", companyAddr = null, companyLat = 13.0,
        companyLong = 100.0, companyStatus = "customer", firstCustomerDate = null
    )

    private val sampleContacts = listOf(
        ContactPerson(
            contactId = "CON-001", custId = "CST-001",
            fullName = "คุณวิภาวี", phoneNumber = "0812345678", position = "ผู้จัดการ"
        ),
        ContactPerson(contactId = "CON-002", custId = "CST-001",
            fullName = "คุณสมชาย", phoneNumber = null, position = "เจ้าหน้าที่")
    )

    private val sampleCustomers = listOf(
        Customer("CST-001", "แสนสิริ", null, "Developer", null, 13.0, 100.0, "customer", null),
        Customer("CST-002", "เมเจอร์", null, "Developer", null, 13.0, 100.0, "customer", null)
    )

    @Before
    fun setup() {
        repository = CustomerRepository(apiService, customerDao, contactDao, projectDao, activityDao, authRepo)
    }

    // TC-UNIT-CUST-EXT-01
    @Test
    fun `getCustomerById should return local customer first`() = runTest {
        every { customerDao.getAllCustomers() } returns flowOf(listOf(sampleCustomer))

        val result = repository.getCustomerById("CST-001")

        assertTrue(result.isSuccess)
        assertEquals("CST-001", result.getOrNull()?.custId)
        coVerify(exactly = 0) { apiService.getCustomerById(any()) }
    }

    // TC-UNIT-CUST-EXT-02
    @Test
    fun `getCustomerById local miss should fetch from API`() = runTest {
        every { customerDao.getAllCustomers() } returns flowOf(emptyList())
        coEvery { apiService.getCustomerById("eq.CST-001") } returns
                Response.success(listOf(sampleCustomer))

        val result = repository.getCustomerById("CST-001")

        assertTrue(result.isSuccess)
        coVerify { apiService.getCustomerById("eq.CST-001") }
    }

    // TC-UNIT-CUST-EXT-03
    @Test
    fun `getCustomerById not found should return failure`() = runTest {
        every { customerDao.getAllCustomers() } returns flowOf(emptyList())
        coEvery { apiService.getCustomerById(any()) } returns Response.success(emptyList())

        val result = repository.getCustomerById("CST-999")

        assertTrue(result.isFailure)
    }

    // TC-UNIT-CUST-EXT-04
    @Test
    fun `addCustomer success should insert to local DB`() = runTest {
        coEvery { apiService.addCustomer(any()) } returns Response.success(listOf(sampleCustomer))

        val result = repository.addCustomer(sampleCustomer)

        assertTrue(result.isSuccess)
        coVerify { customerDao.insertCustomer(sampleCustomer) }
    }

    // TC-UNIT-CUST-EXT-05
    @Test
    fun `addCustomer API error should return failure but still save local`() = runTest {
        coEvery { apiService.addCustomer(any()) } returns
                Response.error(400, "error".toResponseBody())

        val result = repository.addCustomer(sampleCustomer)

        assertTrue(result.isFailure)
        coVerify { customerDao.insertCustomer(sampleCustomer) }
    }

    // TC-UNIT-CUST-EXT-06
    @Test
    fun `addCustomer offline should save local and return success`() = runTest {
        coEvery { apiService.addCustomer(any()) } throws Exception("Network error")

        val result = repository.addCustomer(sampleCustomer)

        assertTrue(result.isSuccess)
        coVerify { customerDao.insertCustomer(sampleCustomer) }
    }

    // TC-UNIT-CUST-EXT-07
    @Test
    fun `getAllContactPhoneMap should return phone to custId map`() = runTest {
        every { contactDao.getAllContacts() } returns flowOf(sampleContacts)

        val result = repository.getAllContactPhoneMap()

        assertEquals(1, result.size) // CON-002 ไม่มี phone จึงถูก filter ออก
        assertEquals("CST-001", result["0812345678"])
    }

    // TC-UNIT-CUST-EXT-08
    @Test
    fun `getAllContactPhoneMap with no phone numbers should return empty map`() = runTest {
        val contactsNoPhone = listOf(
            ContactPerson(contactId = "CON-001", custId = "CST-001",
                fullName = "คุณสมชาย", phoneNumber = null, position = "เจ้าหน้าที่")
        )
        every { contactDao.getAllContacts() } returns flowOf(contactsNoPhone)

        val result = repository.getAllContactPhoneMap()

        assertTrue(result.isEmpty())
    }

    // TC-UNIT-CUST-EXT-09
    @Test
    fun `getContactPersons success should return list`() = runTest {
        every { contactDao.getContactsByCustomer("CST-001") } returns flowOf(sampleContacts)

        val result = repository.getContactPersons("CST-001")

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull()?.size)
    }

    // TC-UNIT-CUST-EXT-10
    @Test
    fun `getContactPersons failure should return failure`() = runTest {
        every { contactDao.getContactsByCustomer(any()) } throws Exception("DB Error")

        val result = repository.getContactPersons("CST-001")

        assertTrue(result.isFailure)
    }

    // TC-UNIT-CUST-FINAL-01
    @Test
    fun `getCustomers should return from local DB`() = runTest {
        every { customerDao.getAllCustomers() } returns flowOf(sampleCustomers)

        val result = repository.getCustomers()

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull()?.size)
    }

    // TC-UNIT-CUST-FINAL-02
    @Test
    fun `getCustomers empty local should return empty list`() = runTest {
        every { customerDao.getAllCustomers() } returns flowOf(emptyList())

        val result = repository.getCustomers()

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()?.isEmpty() == true)
    }

    // TC-UNIT-CUST-FINAL-03
    @Test
    fun `getLocalCustomers non-empty should return success`() = runTest {
        every { customerDao.getAllCustomers() } returns flowOf(sampleCustomers)

        val result = repository.getLocalCustomers()

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull()?.size)
    }

    // TC-UNIT-CUST-FINAL-04
    @Test
    fun `getLocalCustomers empty should return failure`() = runTest {
        every { customerDao.getAllCustomers() } returns flowOf(emptyList())

        val result = repository.getLocalCustomers()

        assertTrue(result.isFailure)
    }

    // TC-UNIT-CUST-FINAL-05
    @Test
    fun `refreshCustomers success should clear and insert customers`() = runTest {
        coEvery { apiService.getCustomersByBranch(branchId = any()) } returns Response.success(sampleCustomers)

        val result = repository.refreshCustomers("BR-001")

        assertTrue(result.isSuccess)
        coVerify { customerDao.clearAndInsert(sampleCustomers) }
    }

    // TC-UNIT-CUST-FINAL-06
    @Test
    fun `refreshCustomers API error should return failure`() = runTest {
        coEvery { apiService.getCustomersByBranch(any()) } returns Response.error(500, "error".toResponseBody())

        val result = repository.refreshCustomers("BR-001")

        assertTrue(result.isFailure)
    }

    // TC-UNIT-CUST-FINAL-07
    @Test
    fun `refreshCustomers network exception should return failure`() = runTest {
        coEvery { apiService.getCustomersByBranch(any()) } throws Exception("Network error")

        val result = repository.refreshCustomers("BR-001")

        assertTrue(result.isFailure)
    }

    @Test
    fun `getCustomers DB exception should return failure`() = runTest {
        every { customerDao.getAllCustomers() } throws Exception("DB error")

        val result = repository.getCustomers()

        assertTrue(result.isFailure)
    }
}
