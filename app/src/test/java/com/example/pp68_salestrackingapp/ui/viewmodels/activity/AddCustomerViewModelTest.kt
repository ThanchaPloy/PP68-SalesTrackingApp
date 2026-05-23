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
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddCustomerViewModelTest {

    private val customerRepo \u003d mockk\u003cCustomerRepository\u003e(relaxed \u003d true)
    private val projectRepo  \u003d mockk\u003cProjectRepository\u003e(relaxed \u003d true)
    private val authRepo     \u003d mockk\u003cAuthRepository\u003e(relaxed \u003d true)
    private lateinit var viewModel: AddCustomerViewModel

    private val testDispatcher \u003d StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { projectRepo.getAllProjectsFlow() } returns flowOf(emptyList())
        viewModel \u003d AddCustomerViewModel(customerRepo, projectRepo, authRepo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `onEvent_whenLoadCustomer_updatesUiState`() \u003d runTest {
        val mockCustomer \u003d Customer(
            custId      \u003d \"C123\",
            companyName \u003d \"Test Corp\",
            branch      \u003d \"Main\",
            custType    \u003d \"Developer\",
            companyAddr \u003d \"123 St\",
            companyLat  \u003d 10.0,
            companyLong \u003d 20.0,
            companyStatus \u003d \"customer\",
            createdAt \u003d \"2024-01-01\"
        )
        coEvery { customerRepo.getCustomerById(\"C123\") } returns Result.success(mockCustomer)

        viewModel.onEvent(AddCustomerEvent.LoadCustomer(\"C123\"))
        advanceUntilIdle()

        val state \u003d viewModel.uiState.value
        assertEquals(\"Test Corp\", state.companyName)
        assertEquals(\"2024-01-01\", state.createdAt)
    }

    @Test
    fun `onEvent_whenSave_callsRepo`() \u003d runTest {
        every { authRepo.currentUser() } returns AuthUser(\"U1\", \"e@e.com\", \"admin\", \"B1\")
        
        viewModel.onEvent(AddCustomerEvent.CompanyNameChanged(\"New Co\"))
        viewModel.onEvent(AddCustomerEvent.CustTypeChanged(\"Owner\"))
        viewModel.onEvent(AddCustomerEvent.CreatedAtChanged(\"2026-01-01\"))
        
        viewModel.onEvent(AddCustomerEvent.Save)
        advanceUntilIdle()

        coVerify { 
            customerRepo.addCustomer(match { 
                it.companyName \u003d\u003d \"New Co\" \u0026\u0026 it.createdAt \u003d\u003d \"2026-01-01\"
            }) 
        }
    }

    @Test
    fun `onEvent_whenCreatedAtBlank_setsNullDate`() \u003d runTest {
        viewModel.onEvent(AddCustomerEvent.CreatedAtChanged(\"\"))
        assertNull(viewModel.uiState.value.createdAt)

        viewModel.onEvent(AddCustomerEvent.CreatedAtChanged(\"   \"))
        assertNull(viewModel.uiState.value.createdAt)
    }
}
