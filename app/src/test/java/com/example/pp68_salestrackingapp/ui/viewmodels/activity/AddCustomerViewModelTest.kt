package com.example.pp68_salestrackingapp.ui.viewmodels.customer

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.example.pp68_salestrackingapp.data.model.*
import com.example.pp68_salestrackingapp.data.repository.*
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class AddCustomerViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()
    private val testDispatcher = StandardTestDispatcher()

    private val customerRepo = mockk<CustomerRepository>(relaxed = true)
    private val projectRepo = mockk<ProjectRepository>(relaxed = true)
    private val authRepo = mockk<AuthRepository>(relaxed = true)

    // ⚠️ หมายเหตุ: ตอนนี้คุณกำลังเทสต์ CustomerDetailViewModel อยู่ในไฟล์ AddCustomer
    // ถ้าอยากเทสต์ AddCustomer จริงๆ ต้องเปลี่ยนตรงนี้เป็น AddCustomerViewModel ในอนาคตนะครับ
    private lateinit var viewModel: CustomerDetailViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        // ✅ เคลียร์ Dispatchers ให้สะอาด
        unmockkObject(Dispatchers)
        clearAllMocks()
        Dispatchers.resetMain()
    }

    @Test
    fun `load should fetch all data and filter projects accurately`() = runTest {
        val custId = "C1"
        val mockCustomer = Customer(custId, "Corp A", null, "Owner", "BKK", 13.0, 100.0, "customer", null)
        val mockContacts = listOf(ContactPerson("CP1", custId, "John", null, null, null))
        val mockProjects = listOf(
            Project("P1", custId, null, "PJ01", "Active", null, "Quotation", null, null, null, null, null, null, null),
            Project("P2", custId, null, "PJ02", "Done", null, "Completed", null, null, null, null, null, null, null)
        )

        coEvery { customerRepo.getCustomerById(any()) } returns Result.success(mockCustomer)
        coEvery { customerRepo.getContactPersons(any()) } returns Result.success(mockContacts)
        every { projectRepo.getAllProjectsFlow() } returns flowOf(mockProjects)

        viewModel = CustomerDetailViewModel(customerRepo, projectRepo, authRepo)

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

        viewModel = CustomerDetailViewModel(customerRepo, projectRepo, authRepo)

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

        viewModel = CustomerDetailViewModel(customerRepo, projectRepo, authRepo)

        viewModel.load("C1")
        advanceUntilIdle()

        assertEquals(mockCustomer, viewModel.customer.value)
        assertTrue(viewModel.contacts.value.isEmpty())
        assertFalse(viewModel.isLoading.value)
    }
}