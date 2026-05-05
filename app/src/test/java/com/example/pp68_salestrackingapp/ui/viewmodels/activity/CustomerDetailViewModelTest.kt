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
    private val projectRepo  = mockk<ProjectRepository>(relaxed = true)
    private val authRepo     = mockk<AuthRepository>(relaxed = true)
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
        val mockCustomer = Customer(
            custId         = custId,
            companyName    = "Corp A",
            branch         = null,
            custType       = "Owner",
            companyAddr    = "BKK",
            companyLat     = 13.0,
            companyLong    = 100.0,
            companyStatus  = "customer",
            firstCustomerDate = null
        )
        val mockContacts = listOf(
            ContactPerson(
                contactId = "CP1",
                custId    = custId,
                fullName  = "John"  // ✅ ใช้ named param ไม่ใช่ positional
            )
        )
        val mockProjects = listOf(
            Project(
                projectId     = "P1",
                custId        = custId,
                projectName   = "Active Project",
                projectStatus = "Quotation"
            ),
            Project(
                projectId     = "P2",
                custId        = custId,
                projectName   = "Done Project",
                projectStatus = "Completed"
            )
        )

        coEvery { customerRepo.getCustomerById(any()) } returns Result.success(mockCustomer)
        every { customerRepo.getContactsForCustomerFlow(any()) } returns flowOf(mockContacts)
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
        every { customerRepo.getContactsForCustomerFlow(any()) } returns flowOf(emptyList())
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
    fun `load should succeed even if customer load fails but still observe others`() = runTest {
        coEvery { customerRepo.getCustomerById(any()) } returns Result.failure(Exception("Database Error"))
        every { customerRepo.getContactsForCustomerFlow(any()) } returns flowOf(emptyList())
        every { projectRepo.getAllProjectsFlow() } returns flowOf(emptyList())

        viewModel.load("C1")
        advanceUntilIdle()

        assertNull(viewModel.customer.value)
        assertTrue(viewModel.contacts.value.isEmpty())
        assertFalse(viewModel.isLoading.value)
    }

    @Test
    fun `deleteContact should refresh contacts when delete succeeds`() = runTest {
        val contactsAfterDelete = listOf(
            ContactPerson(contactId = "CP2", custId = "C1", fullName = "Jane")
        )
        coEvery { customerRepo.deleteContact("CP1") } returns Result.success(Unit)
        every { customerRepo.getContactsForCustomerFlow("C1") } returns flowOf(contactsAfterDelete)
        coEvery { customerRepo.getCustomerById(any()) } returns Result.success(
            Customer(
                custId        = "C1",
                companyName   = "Corp A",
                branch        = null,
                custType      = "Owner",
                companyAddr   = null,
                companyLat    = null,
                companyLong   = null,
                companyStatus = null,
                firstCustomerDate = null
            )
        )
        every { projectRepo.getAllProjectsFlow() } returns flowOf(emptyList())

        viewModel.load("C1")
        advanceUntilIdle()
        viewModel.deleteContact("CP1")
        advanceUntilIdle()

        assertEquals(contactsAfterDelete, viewModel.contacts.value)
        coVerify(exactly = 1) { customerRepo.deleteContact("CP1") }
    }

    @Test
    fun `deleteCustomer should set deleteSuccess when repository succeeds`() = runTest {
        coEvery { customerRepo.getCustomerById("C1") } returns Result.success(
            Customer(
                custId        = "C1",
                companyName   = "Corp A",
                branch        = null,
                custType      = "Owner",
                companyAddr   = null,
                companyLat    = null,
                companyLong   = null,
                companyStatus = null,
                firstCustomerDate = null
            )
        )
        every { customerRepo.getContactsForCustomerFlow("C1") } returns flowOf(emptyList())
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
    fun `logout should delegate to auth repository`() = runTest {
        every { authRepo.currentUser() } returns AuthUser("U1", "x@test.com", "sale")

        viewModel.logout()
        advanceUntilIdle()

        coVerify(exactly = 1) { authRepo.logout() }
    }
}