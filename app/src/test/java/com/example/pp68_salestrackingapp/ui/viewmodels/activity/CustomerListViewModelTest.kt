package com.example.pp68_salestrackingapp.ui.viewmodels.customer

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.example.pp68_salestrackingapp.data.model.*
import com.example.pp68_salestrackingapp.data.repository.*
import com.example.pp68_salestrackingapp.ui.screen.customer.CustomerListViewModel
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class CustomerListViewModelTest {

    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private val customerRepo = mockk<CustomerRepository>(relaxed = true)
    private val authRepo = mockk<AuthRepository>(relaxed = true)
    private lateinit var viewModel: CustomerListViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        every { authRepo.currentUser() } returns AuthUser("U1", "t@t.com", "sale", "T1")
        every { customerRepo.getAllCustomersFlow() } returns flowOf(emptyList())
        every { customerRepo.searchCustomersFlow(any()) } returns flowOf(emptyList())
        coEvery { customerRepo.refreshCustomers(any()) } returns Result.success(Unit)
    }

    @After
    fun tearDown() {
        clearAllMocks()
        Dispatchers.resetMain()
    }

    @Test
    fun `onSearchChange should update flow after debounce`() = runTest {
        val query = "Sansiri"
        val result = listOf(Customer("C1", "Sansiri", null, null, null, null, null, null, null))

        every { customerRepo.searchCustomersFlow(query) } returns flowOf(result)

        viewModel = CustomerListViewModel(customerRepo, authRepo)
        advanceUntilIdle()

        viewModel.customers.test {
            awaitItem()
            viewModel.onSearchChange(query)
            advanceUntilIdle()
            assertEquals(result, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `init should refresh by current user and clear loading`() = runTest {
        coEvery { customerRepo.refreshCustomers("U1") } returns Result.success(Unit)
        viewModel = CustomerListViewModel(customerRepo, authRepo)
        advanceUntilIdle()

        assertFalse(viewModel.isLoading.value)
        assertNull(viewModel.error.value)
        coVerify(exactly = 1) { customerRepo.refreshCustomers("U1") }
    }

    @Test
    fun `refresh failure should set error`() = runTest {
        coEvery { customerRepo.refreshCustomers("U1") } returns Result.failure(Exception("network down"))
        viewModel = CustomerListViewModel(customerRepo, authRepo)
        advanceUntilIdle()

        assertEquals("network down", viewModel.error.value)
        assertFalse(viewModel.isLoading.value)
    }

    @Test
    fun `clearError should set error to null`() = runTest {
        coEvery { customerRepo.refreshCustomers("U1") } returns Result.failure(Exception("boom"))
        viewModel = CustomerListViewModel(customerRepo, authRepo)
        advanceUntilIdle()
        assertEquals("boom", viewModel.error.value)

        viewModel.clearError()
        assertNull(viewModel.error.value)
    }

    @Test
    fun `logout should call auth repository logout`() = runTest {
        viewModel = CustomerListViewModel(customerRepo, authRepo)
        advanceUntilIdle()

        viewModel.logout()
        advanceUntilIdle()

        coVerify(exactly = 1) { authRepo.logout() }
    }

    @Test
    fun `blank search should use all customers flow`() = runTest {
        val all = listOf(Customer("C1", "Alpha Co", null, null, null, null, null, null, null))
        every { customerRepo.getAllCustomersFlow() } returns flowOf(all)

        viewModel = CustomerListViewModel(customerRepo, authRepo)
        advanceUntilIdle()

        viewModel.customers.test {
            assertEquals(all, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `refresh should return early when current user is null`() = runTest {
        every { authRepo.currentUser() } returns null

        viewModel = CustomerListViewModel(customerRepo, authRepo)
        advanceUntilIdle()

        coVerify(exactly = 0) { customerRepo.refreshCustomers(any()) }
        assertFalse(viewModel.isLoading.value)
    }
}
