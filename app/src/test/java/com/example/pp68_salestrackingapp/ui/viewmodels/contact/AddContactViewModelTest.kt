package com.example.pp68_salestrackingapp.ui.viewmodels.contact

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.pp68_salestrackingapp.data.model.ContactPerson
import com.example.pp68_salestrackingapp.data.model.Customer
import com.example.pp68_salestrackingapp.data.model.Project
import com.example.pp68_salestrackingapp.data.repository.ContactRepository
import com.example.pp68_salestrackingapp.data.repository.CustomerRepository
import com.example.pp68_salestrackingapp.data.repository.ProjectRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddContactViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private val contactRepository  = mockk<ContactRepository>(relaxed = true)
    private val customerRepository = mockk<CustomerRepository>(relaxed = true)
    private val projectRepository  = mockk<ProjectRepository>(relaxed = true)
    private lateinit var viewModel: AddContactViewModel

    private val mockCustomers = listOf(
        Customer(
            custId        = "CUST-01",
            companyName   = "Acme Corp",
            branch        = null,
            custType      = "CEO",
            companyAddr   = "Bangkok",
            companyLat    = 13.0,
            companyLong   = 100.0,
            companyStatus = "customer",
            firstCustomerDate = null
        ),
        Customer(
            custId        = "CUST-02",
            companyName   = "Globex",
            branch        = null,
            custType      = "CTO",
            companyAddr   = "Chiang Mai",
            companyLat    = 19.0,
            companyLong   = 99.0,
            companyStatus = "customer",
            firstCustomerDate = null
        )
    )

    private val mockProjects = listOf(
        Project(
            projectId     = "P01",
            custId        = "CUST-01",
            projectName   = "ProjectA",
            projectStatus = "New Project"
        ),
        Project(
            projectId     = "P02",
            custId        = "CUST-02",
            projectName   = "ProjectB",
            projectStatus = "Quotation"
        )
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() {
        viewModel = AddContactViewModel(contactRepository, customerRepository, projectRepository)
    }

    @Test
    fun `init should load company options from CustomerRepository`() = runTest {
        coEvery { customerRepository.getCustomers() } returns Result.success(mockCustomers)
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val options = viewModel.uiState.value.companyOptions
        assertEquals(2, options.size)
        assertEquals("CUST-01" to "Acme Corp", options[0])
        assertEquals("CUST-02" to "Globex", options[1])
    }

    @Test
    fun `init should have default state with empty fields`() = runTest {
        coEvery { customerRepository.getCustomers() } returns Result.success(emptyList())
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("", state.fullName)
        assertEquals("", state.email)
        assertEquals("", state.phoneNum)
        assertNull(state.selectedCompanyId)
        assertTrue(state.isActive)
        assertFalse(state.isDecisionMaker)
        assertFalse(state.isSaved)
    }

    @Test
    fun `FullNameChanged event should update fullName and clear error`() = runTest {
        coEvery { customerRepository.getCustomers() } returns Result.success(emptyList())
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(AddContactEvent.FullNameChanged("Jane Doe"))
        assertEquals("Jane Doe", viewModel.uiState.value.fullName)
        assertNull(viewModel.uiState.value.fullNameError)
    }

    @Test
    fun `Save success should set isSaved to true and clear loading`() = runTest {
        coEvery { customerRepository.getCustomers() } returns Result.success(emptyList())
        coEvery { contactRepository.addContact(any()) } returns Result.success(Unit)
        every { projectRepository.getAllProjectsFlow() } returns flowOf(emptyList())
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(AddContactEvent.CompanySelected("CUST-01", "Acme Corp"))
        viewModel.onEvent(AddContactEvent.FullNameChanged("Jane Doe"))
        viewModel.onEvent(AddContactEvent.Save)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSaved)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.saveError)
    }

    @Test
    fun `CompanySelected event should load projects for that company`() = runTest {
        coEvery { customerRepository.getCustomers() } returns Result.success(mockCustomers)
        every { projectRepository.getAllProjectsFlow() } returns flowOf(mockProjects)
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(AddContactEvent.CompanySelected("CUST-01", "Acme Corp"))
        testDispatcher.scheduler.advanceUntilIdle()

        val projects = viewModel.uiState.value.projectOptions
        assertEquals(1, projects.size)
        assertEquals("P01" to "ProjectA", projects[0])
    }
}