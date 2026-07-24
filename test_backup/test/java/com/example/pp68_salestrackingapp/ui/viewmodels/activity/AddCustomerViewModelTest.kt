package com.example.pp68_salestrackingapp.ui.viewmodels.activity

import com.example.pp68_salestrackingapp.data.model.AuthUser
import com.example.pp68_salestrackingapp.data.model.Customer
import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import com.example.pp68_salestrackingapp.data.repository.CustomerRepository
import com.example.pp68_salestrackingapp.data.repository.ProjectRepository
import com.example.pp68_salestrackingapp.ui.viewmodels.customer.AddCustomerEvent
import com.example.pp68_salestrackingapp.ui.viewmodels.customer.AddCustomerViewModel
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class AddCustomerViewModelTest {

    private val customerRepo = mockk<CustomerRepository>(relaxed = true)
    private val projectRepo  = mockk<ProjectRepository>(relaxed = true)
    private val authRepo     = mockk<AuthRepository>(relaxed = true)
    private lateinit var viewModel: AddCustomerViewModel

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { projectRepo.getAllProjectsFlow() } returns flowOf(emptyList())
        coEvery { customerRepo.addCustomer(any()) } returns Result.success(Unit)
        coEvery { customerRepo.updateCustomer(any(), any()) } returns Result.success(Unit)
        coEvery { customerRepo.getCustomerById(any()) } returns Result.success(Customer(custId = "C123", companyName = "Test Corp", companyStatus = 1))
        viewModel = AddCustomerViewModel(customerRepo, projectRepo, authRepo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `onEvent_whenLoadCustomer_updatesUiState`() = runTest {
        val mockCustomer = Customer(
            custId      = "C123",
            companyName = "Test Corp",
            branch      = "Main",
            custType    = "Developer",
            companyAddr = "123 St",
            companyLat  = 10.0,
            companyLong = 20.0,
            companyStatus = 1, // 1 -> "customer"
            createdAt = "2024-01-01"
        )
        coEvery { customerRepo.getCustomerById("C123") } returns Result.success(mockCustomer)

        viewModel.onEvent(AddCustomerEvent.LoadCustomer("C123"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Test Corp", state.companyName)
    }

    @Test
    fun `onEvent_whenSave_callsRepo`() = runTest {
        every { authRepo.currentUser() } returns AuthUser("U1", "e@e.com", "admin", "B1")
        
        viewModel.onEvent(AddCustomerEvent.CompanyNameChanged("New Co"))
        viewModel.onEvent(AddCustomerEvent.CustTypeChanged("Owner"))
        
        viewModel.onEvent(AddCustomerEvent.Save)
        advanceUntilIdle()

        coVerify { 
            customerRepo.addCustomer(match { 
                it.companyName == "New Co" && it.createdAt == LocalDate.now().toString()
            }) 
        }
    }
}
