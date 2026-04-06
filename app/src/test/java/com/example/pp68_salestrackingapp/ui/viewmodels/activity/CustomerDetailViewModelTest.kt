package com.example.pp68_salestrackingapp.ui.viewmodels.customer

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.example.pp68_salestrackingapp.data.model.AuthUser
import com.example.pp68_salestrackingapp.data.model.ContactPerson
import com.example.pp68_salestrackingapp.data.model.Customer
import com.example.pp68_salestrackingapp.data.model.Project
import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import com.example.pp68_salestrackingapp.data.repository.CustomerRepository
import com.example.pp68_salestrackingapp.data.repository.ProjectRepository
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CustomerDetailViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()
    private val testDispatcher = StandardTestDispatcher()

    private val customerRepo = mockk<CustomerRepository>(relaxed = true)
    private val projectRepo = mockk<ProjectRepository>(relaxed = true)
    private val authRepo = mockk<AuthRepository>(relaxed = true)
    private lateinit var viewModel: CustomerDetailViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = CustomerDetailViewModel(customerRepo, projectRepo, authRepo)
    }

    @After
    fun tearDown() {
        clearAllMocks()
        Dispatchers.resetMain()
    }

    @Test
    fun `load should fetch data and split projects accurately`() = runTest {
        val custId = "C1"
        val mockCustomer = Customer(custId, "Corp A", null, "Owner", "BKK", 13.0, 100.0, "customer", null)
        val mockContacts = listOf(ContactPerson("CP1", custId, "John", null, null, null))
        val mockProjects = listOf(
            Project("P1", custId, null, "PJ01", "Active", null, "Quotation", null, null, null, null, null, null, null),
            Project("P2", custId, null, "PJ02", "Done", null, "Completed", null, null, null, null, null, null, null)
        )

        // ✅ ฟังก์ชัน suspend ใช้ coEvery
        coEvery { customerRepo.getCustomerById(any()) } returns Result.success(mockCustomer)
        coEvery { customerRepo.getContactPersons(any()) } returns Result.success(mockContacts)

        // ✅ ฟังก์ชัน Flow ธรรมดาใช้ every
        every { projectRepo.getAllProjectsFlow() } returns flowOf(mockProjects)

        viewModel.isLoading.test {
            val initialState = awaitItem()
            viewModel.load(custId)

            if (!initialState) {
                assertTrue(awaitItem())
            }
            assertFalse(awaitItem())

            assertEquals(mockCustomer, viewModel.customer.value)
            assertEquals(mockContacts, viewModel.contacts.value)
            assertEquals(1, viewModel.activeProjects.value.size)
            assertEquals(1, viewModel.closedProjects.value.size)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `load should handle failure gracefully and clear loading state`() = runTest {
        coEvery { customerRepo.getCustomerById(any()) } returns Result.failure(Exception("Network Error"))
        coEvery { customerRepo.getContactPersons(any()) } returns Result.success(emptyList())
        every { projectRepo.getAllProjectsFlow() } returns flowOf(emptyList())

        viewModel.isLoading.test {
            val initialState = awaitItem()
            viewModel.load("C1")

            if (!initialState) {
                assertTrue(awaitItem())
            }
            assertFalse(awaitItem())

            assertNull(viewModel.customer.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `load should succeed even if getContactPersons fails`() = runTest {
        val mockCustomer = Customer("C1", "Corp A", null, "Owner", "BKK", 13.0, 100.0, "customer", null)

        coEvery { customerRepo.getCustomerById(any()) } returns Result.success(mockCustomer)
        coEvery { customerRepo.getContactPersons(any()) } returns Result.failure(Exception("Database Error"))
        every { projectRepo.getAllProjectsFlow() } returns flowOf(emptyList())

        viewModel.load("C1")
        advanceUntilIdle()

        assertEquals(mockCustomer, viewModel.customer.value)
        assertTrue(viewModel.contacts.value.isEmpty())
        assertFalse(viewModel.isLoading.value)
    }

    @Test
    fun `deleteContact should refresh contacts when delete succeeds`() = runTest {
        val contactsAfterDelete = listOf(ContactPerson("CP2", "C1", "Jane"))
        coEvery { customerRepo.deleteContact("CP1") } returns Result.success(Unit)
        coEvery { customerRepo.getContactPersons("C1") } returns Result.success(contactsAfterDelete)
        coEvery { customerRepo.getCustomerById(any()) } returns Result.success(
            Customer("C1", "Corp A", null, "Owner", null, null, null, null, null)
        )
        every { projectRepo.getAllProjectsFlow() } returns flowOf(emptyList())

        viewModel.load("C1")
        advanceUntilIdle()
        viewModel.deleteContact("CP1")
        advanceUntilIdle()

        assertEquals(contactsAfterDelete, viewModel.contacts.value)
        coVerify(exactly = 1) { customerRepo.deleteContact("CP1") }
        coVerify(atLeast = 1) { customerRepo.getContactPersons("C1") }
    }

    @Test
    fun `deleteContact should not refresh contacts when delete fails`() = runTest {
        coEvery { customerRepo.deleteContact("CP1") } returns Result.failure(Exception("delete failed"))
        coEvery { customerRepo.getCustomerById(any()) } returns Result.success(
            Customer("C1", "Corp A", null, "Owner", null, null, null, null, null)
        )
        coEvery { customerRepo.getContactPersons("C1") } returns Result.success(emptyList())
        every { projectRepo.getAllProjectsFlow() } returns flowOf(emptyList())

        viewModel.load("C1")
        advanceUntilIdle()
        viewModel.deleteContact("CP1")
        advanceUntilIdle()

        coVerify(exactly = 1) { customerRepo.deleteContact("CP1") }
        coVerify(exactly = 1) { customerRepo.getContactPersons("C1") } // from initial load only
    }

    @Test
    fun `deleteContact should not refresh when current customer id is null`() = runTest {
        coEvery { customerRepo.deleteContact("CP1") } returns Result.success(Unit)

        viewModel.deleteContact("CP1")
        advanceUntilIdle()

        coVerify(exactly = 1) { customerRepo.deleteContact("CP1") }
        coVerify(exactly = 0) { customerRepo.getContactPersons(any()) }
    }

    @Test
    fun `deleteCustomer should early return when current customer id is null`() = runTest {
        viewModel.deleteCustomer()
        advanceUntilIdle()

        coVerify(exactly = 0) { customerRepo.deleteCustomer(any()) }
        assertFalse(viewModel.deleteSuccess.value)
    }

    @Test
    fun `deleteCustomer should set deleteSuccess when repository succeeds`() = runTest {
        coEvery { customerRepo.getCustomerById("C1") } returns Result.success(
            Customer("C1", "Corp A", null, "Owner", null, null, null, null, null)
        )
        coEvery { customerRepo.getContactPersons("C1") } returns Result.success(emptyList())
        every { projectRepo.getAllProjectsFlow() } returns flowOf(emptyList())
        coEvery { customerRepo.deleteCustomer("C1") } returns Result.success(Unit)

        viewModel.load("C1")
        advanceUntilIdle()
        viewModel.deleteCustomer()
        advanceUntilIdle()

        assertTrue(viewModel.deleteSuccess.value)
        assertFalse(viewModel.isLoading.value)
    }

    @Test
    fun `deleteCustomer should keep deleteSuccess false when repository fails`() = runTest {
        coEvery { customerRepo.getCustomerById("C1") } returns Result.success(
            Customer("C1", "Corp A", null, "Owner", null, null, null, null, null)
        )
        coEvery { customerRepo.getContactPersons("C1") } returns Result.success(emptyList())
        every { projectRepo.getAllProjectsFlow() } returns flowOf(emptyList())
        coEvery { customerRepo.deleteCustomer("C1") } returns Result.failure(Exception("delete failed"))

        viewModel.load("C1")
        advanceUntilIdle()
        viewModel.deleteCustomer()
        advanceUntilIdle()

        assertFalse(viewModel.deleteSuccess.value)
        assertFalse(viewModel.isLoading.value)
    }

    @Test
    fun `logout should delegate to auth repository`() = runTest {
        every { authRepo.currentUser() } returns AuthUser("U1", "x@test.com", "sale")

        viewModel.logout()
        advanceUntilIdle()

        coVerify(exactly = 1) { authRepo.logout() }
    }
}
