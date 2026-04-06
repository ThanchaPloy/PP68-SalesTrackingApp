package com.example.pp68_salestrackingapp.ui.viewmodels.contact

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.pp68_salestrackingapp.data.model.ContactPerson
import com.example.pp68_salestrackingapp.data.model.Customer
import com.example.pp68_salestrackingapp.data.model.Project
import com.example.pp68_salestrackingapp.data.repository.ContactRepository
import com.example.pp68_salestrackingapp.data.repository.CustomerRepository
import com.example.pp68_salestrackingapp.data.repository.ProjectRepository
import io.mockk.coEvery
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
    private val contactRepository = mockk<ContactRepository>(relaxed = true)
    private val customerRepository = mockk<CustomerRepository>(relaxed = true)
    private val projectRepository = mockk<ProjectRepository>(relaxed = true)
    private lateinit var viewModel: AddContactViewModel

    private val mockCustomers = listOf(
        Customer("CUST-01", "Acme Corp", null, "CEO", "Bangkok", 13.0, 100.0, "customer", null),
        Customer("CUST-02", "Globex", null, "CTO", "Chiang Mai", 19.0, 99.0, "customer", null)
    )

    private val mockProjects = listOf(
        Project("P01", "CUST-01", null, "ProjectA", "Alpha", null, "New Project"),
        Project("P02", "CUST-02", null, "ProjectB", "Beta", null, "Quotation")
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

    // TC-UNIT-VM-ADDCNT-01
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

    // TC-UNIT-VM-ADDCNT-02
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

    // TC-UNIT-VM-ADDCNT-03
    @Test
    fun `FullNameChanged event should update fullName and clear error`() = runTest {
        coEvery { customerRepository.getCustomers() } returns Result.success(emptyList())
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(AddContactEvent.FullNameChanged("Jane Doe"))
        assertEquals("Jane Doe", viewModel.uiState.value.fullName)
        assertNull(viewModel.uiState.value.fullNameError)
    }

    // TC-UNIT-VM-ADDCNT-04
    @Test
    fun `EmailChanged event should update email and clear error`() = runTest {
        coEvery { customerRepository.getCustomers() } returns Result.success(emptyList())
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(AddContactEvent.EmailChanged("jane@example.com"))
        assertEquals("jane@example.com", viewModel.uiState.value.email)
        assertNull(viewModel.uiState.value.emailError)
    }

    // TC-UNIT-VM-ADDCNT-05
    @Test
    fun `PhoneChanged event should update phoneNum`() = runTest {
        coEvery { customerRepository.getCustomers() } returns Result.success(emptyList())
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(AddContactEvent.PhoneChanged("0812345678"))
        assertEquals("0812345678", viewModel.uiState.value.phoneNum)
    }

    // TC-UNIT-VM-ADDCNT-06
    @Test
    fun `NicknameChanged event should update nickname`() = runTest {
        coEvery { customerRepository.getCustomers() } returns Result.success(emptyList())
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(AddContactEvent.NicknameChanged("Jay"))
        assertEquals("Jay", viewModel.uiState.value.nickname)
    }

    // TC-UNIT-VM-ADDCNT-07
    @Test
    fun `PositionChanged event should update position`() = runTest {
        coEvery { customerRepository.getCustomers() } returns Result.success(emptyList())
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(AddContactEvent.PositionChanged("Manager"))
        assertEquals("Manager", viewModel.uiState.value.position)
    }

    // TC-UNIT-VM-ADDCNT-08
    @Test
    fun `LineIdChanged event should update lineId`() = runTest {
        coEvery { customerRepository.getCustomers() } returns Result.success(emptyList())
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(AddContactEvent.LineIdChanged("jane_line"))
        assertEquals("jane_line", viewModel.uiState.value.lineId)
    }

    // TC-UNIT-VM-ADDCNT-09
    @Test
    fun `IsActiveToggled event should flip isActive`() = runTest {
        coEvery { customerRepository.getCustomers() } returns Result.success(emptyList())
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isActive)
        viewModel.onEvent(AddContactEvent.IsActiveToggled)
        assertFalse(viewModel.uiState.value.isActive)
        viewModel.onEvent(AddContactEvent.IsActiveToggled)
        assertTrue(viewModel.uiState.value.isActive)
    }

    // TC-UNIT-VM-ADDCNT-10
    @Test
    fun `IsDecisionMakerToggled event should flip isDecisionMaker`() = runTest {
        coEvery { customerRepository.getCustomers() } returns Result.success(emptyList())
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isDecisionMaker)
        viewModel.onEvent(AddContactEvent.IsDecisionMakerToggled)
        assertTrue(viewModel.uiState.value.isDecisionMaker)
    }

    // TC-UNIT-VM-ADDCNT-11
    @Test
    fun `Save without company should set companyError`() = runTest {
        coEvery { customerRepository.getCustomers() } returns Result.success(emptyList())
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(AddContactEvent.FullNameChanged("Jane"))
        viewModel.onEvent(AddContactEvent.Save)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("กรุณาเลือกบริษัท", viewModel.uiState.value.companyError)
        assertFalse(viewModel.uiState.value.isSaved)
    }

    // TC-UNIT-VM-ADDCNT-12
    @Test
    fun `Save without fullName should set fullNameError`() = runTest {
        coEvery { customerRepository.getCustomers() } returns Result.success(emptyList())
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(AddContactEvent.CompanySelected("CUST-01", "Acme Corp"))
        viewModel.onEvent(AddContactEvent.FullNameChanged(""))
        viewModel.onEvent(AddContactEvent.Save)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("กรุณากรอกชื่อ", viewModel.uiState.value.fullNameError)
        assertFalse(viewModel.uiState.value.isSaved)
    }

    // TC-UNIT-VM-ADDCNT-13
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

    // TC-UNIT-VM-ADDCNT-14
    @Test
    fun `Save failure should set saveError`() = runTest {
        coEvery { customerRepository.getCustomers() } returns Result.success(emptyList())
        coEvery { contactRepository.addContact(any()) } returns
            Result.failure(Exception("Network Error"))
        every { projectRepository.getAllProjectsFlow() } returns flowOf(emptyList())
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(AddContactEvent.CompanySelected("CUST-01", "Acme Corp"))
        viewModel.onEvent(AddContactEvent.FullNameChanged("Jane Doe"))
        viewModel.onEvent(AddContactEvent.Save)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSaved)
        assertEquals("Network Error", viewModel.uiState.value.saveError)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    // TC-UNIT-VM-ADDCNT-15
    @Test
    fun `CompanySelected event should update company and clear previous project selection`() = runTest {
        coEvery { customerRepository.getCustomers() } returns Result.success(mockCustomers)
        every { projectRepository.getAllProjectsFlow() } returns flowOf(mockProjects)
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(AddContactEvent.CompanySelected("CUST-01", "Acme Corp"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("CUST-01", viewModel.uiState.value.selectedCompanyId)
        assertEquals("Acme Corp", viewModel.uiState.value.selectedCompanyName)
        assertNull(viewModel.uiState.value.selectedProjectId)
        assertNull(viewModel.uiState.value.companyError)
    }

    // TC-UNIT-VM-ADDCNT-16
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
        assertEquals("P01" to "Alpha", projects[0])
    }

    // TC-UNIT-VM-ADDCNT-17
    @Test
    fun `ProjectSelected event should update selectedProjectId and name`() = runTest {
        coEvery { customerRepository.getCustomers() } returns Result.success(emptyList())
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(AddContactEvent.ProjectSelected("P01", "Alpha Project"))
        assertEquals("P01", viewModel.uiState.value.selectedProjectId)
        assertEquals("Alpha Project", viewModel.uiState.value.selectedProjectName)
    }
}
