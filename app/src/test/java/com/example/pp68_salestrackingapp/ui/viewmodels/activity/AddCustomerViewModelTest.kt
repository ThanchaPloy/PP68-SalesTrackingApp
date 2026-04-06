package com.example.pp68_salestrackingapp.ui.viewmodels.customer

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.pp68_salestrackingapp.data.model.Customer
import com.example.pp68_salestrackingapp.data.model.Project
import com.example.pp68_salestrackingapp.data.repository.CustomerRepository
import com.example.pp68_salestrackingapp.data.repository.ProjectRepository
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddCustomerViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()
    private val testDispatcher = StandardTestDispatcher()

    private val customerRepo = mockk<CustomerRepository>(relaxed = true)
    private val projectRepo = mockk<ProjectRepository>(relaxed = true)
    private lateinit var viewModel: AddCustomerViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { projectRepo.getAllProjectsFlow() } returns flowOf(emptyList())
        viewModel = AddCustomerViewModel(customerRepo, projectRepo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init_whenProjectOptionsLoadSuccess_updatesProjectOptions`() = runTest {
        val projects = listOf(
            Project("P1", "C1", projectName = "A"),
            Project("P2", "C1", projectName = "B")
        )
        every { projectRepo.getAllProjectsFlow() } returns flowOf(projects)
        viewModel = AddCustomerViewModel(customerRepo, projectRepo)
        advanceUntilIdle()

        assertEquals(listOf("P1" to "A", "P2" to "B"), viewModel.uiState.value.projectOptions)
    }

    @Test
    fun `onEvent_whenLoadCustomerSuccess_updatesStateWithMappedData`() = runTest {
        val customer = Customer(
            custId = "C1",
            companyName = "Corp A",
            branch = null,
            custType = "owner",
            companyAddr = null,
            companyLat = 13.5,
            companyLong = 100.5,
            companyStatus = null,
            firstCustomerDate = "2026-01-01"
        )
        coEvery { customerRepo.getCustomerById("C1") } returns Result.success(customer)

        viewModel.onEvent(AddCustomerEvent.LoadCustomer("C1"))
        advanceUntilIdle()

        with(viewModel.uiState.value) {
            assertEquals("C1", custId)
            assertEquals("Corp A", companyName)
            assertEquals("", branch)
            assertEquals("", address)
            assertEquals(13.5, selectedLat)
            assertEquals(100.5, selectedLng)
            assertEquals("owner", custType)
            assertEquals("new lead", companyStatus)
            assertEquals("2026-01-01", firstCustomerDate)
            assertFalse(isLoading)
        }
    }

    @Test
    fun `onEvent_whenLoadCustomerFailure_setsSaveErrorAndStopsLoading`() = runTest {
        coEvery { customerRepo.getCustomerById("C1") } returns Result.failure(Exception("fetch failed"))

        viewModel.onEvent(AddCustomerEvent.LoadCustomer("C1"))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals("fetch failed", viewModel.uiState.value.saveError)
    }

    @Test
    fun `onEvent_companyNameAndCustTypeChanged_clearsValidationErrors`() = runTest {
        viewModel.onEvent(AddCustomerEvent.Save)
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.companyNameError)
        assertNotNull(viewModel.uiState.value.custTypeError)

        viewModel.onEvent(AddCustomerEvent.CompanyNameChanged("New Co"))
        viewModel.onEvent(AddCustomerEvent.CustTypeChanged("owner"))

        assertNull(viewModel.uiState.value.companyNameError)
        assertNull(viewModel.uiState.value.custTypeError)
    }

    @Test
    fun `onEvent_whenUseCurrentLocation_setsDefaultCoordinates`() = runTest {
        viewModel.onEvent(AddCustomerEvent.UseCurrentLocation)

        assertEquals(13.7563, viewModel.uiState.value.selectedLat)
        assertEquals(100.5018, viewModel.uiState.value.selectedLng)
    }

    @Test
    fun `onEvent_whenFirstCustomerDateBlank_setsNullDate`() = runTest {
        viewModel.onEvent(AddCustomerEvent.FirstCustomerDateChanged("   "))

        assertNull(viewModel.uiState.value.firstCustomerDate)
    }

    @Test
    fun `onEvent_whenProjectSelected_updatesProjectSelection`() = runTest {
        viewModel.onEvent(AddCustomerEvent.ProjectSelected("P9", "Project Nine"))

        assertEquals("P9", viewModel.uiState.value.selectedProjectId)
        assertEquals("Project Nine", viewModel.uiState.value.selectedProjectName)
    }

    @Test
    fun `onEvent_whenSaveWithInvalidInput_setsValidationErrorsAndSkipsRepositoryCall`() = runTest {
        viewModel.onEvent(AddCustomerEvent.Save)
        advanceUntilIdle()

        assertEquals("กรุณากรอกชื่อบริษัท", viewModel.uiState.value.companyNameError)
        assertEquals("กรุณาเลือกประเภทลูกค้า", viewModel.uiState.value.custTypeError)
        coVerify(exactly = 0) { customerRepo.addCustomer(any()) }
    }

    @Test
    fun `onEvent_whenSaveSuccess_mapsDataAndUpdatesSavedState`() = runTest {
        coEvery { customerRepo.addCustomer(any()) } returns Result.success(Unit)

        viewModel.onEvent(AddCustomerEvent.CompanyNameChanged("Acme"))
        viewModel.onEvent(AddCustomerEvent.BranchChanged(""))
        viewModel.onEvent(AddCustomerEvent.AddressChanged(""))
        viewModel.onEvent(AddCustomerEvent.CustTypeChanged("customer"))
        viewModel.onEvent(AddCustomerEvent.StatusChanged("active"))
        viewModel.onEvent(AddCustomerEvent.FirstCustomerDateChanged(""))
        viewModel.onEvent(AddCustomerEvent.LocationPicked(10.0, 20.0))

        viewModel.onEvent(AddCustomerEvent.Save)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.isSaved)
        assertNull(viewModel.uiState.value.saveError)

        coVerify(exactly = 1) {
            customerRepo.addCustomer(withArg { customer ->
                assertTrue(customer.custId.startsWith("CUST-"))
                assertEquals("Acme", customer.companyName)
                assertNull(customer.branch)
                assertEquals("customer", customer.custType)
                assertNull(customer.companyAddr)
                assertEquals(10.0, customer.companyLat)
                assertEquals(20.0, customer.companyLong)
                assertEquals("active", customer.companyStatus)
                assertNull(customer.firstCustomerDate)
            })
        }
    }

    @Test
    fun `onEvent_whenSaveFailure_setsSaveErrorAndStopsLoading`() = runTest {
        coEvery { customerRepo.addCustomer(any()) } returns Result.failure(Exception("save failed"))
        viewModel.onEvent(AddCustomerEvent.CompanyNameChanged("Acme"))
        viewModel.onEvent(AddCustomerEvent.CustTypeChanged("customer"))

        viewModel.onEvent(AddCustomerEvent.Save)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.isSaved)
        assertEquals("save failed", viewModel.uiState.value.saveError)
    }
}
