package com.example.pp68_salestrackingapp.ui.viewmodels.activity

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.pp68_salestrackingapp.data.model.*
import com.example.pp68_salestrackingapp.data.repository.*
import com.example.pp68_salestrackingapp.ui.screen.project.*
import com.example.pp68_salestrackingapp.ui.viewmodels.customer.AddCustomerEvent
import com.example.pp68_salestrackingapp.ui.viewmodels.customer.AddCustomerViewModel
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class AddProjectViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private val projectRepo = mockk<ProjectRepository>()
    private val customerRepo = mockk<CustomerRepository>()
    private val authRepo = mockk<AuthRepository>()
    private val branchRepo = mockk<BranchRepository>()
    private lateinit var viewModel: AddProjectViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(Dispatchers::class)
        every { Dispatchers.IO } returns testDispatcher

        val user = AuthUser("USR-001", "test@test.com", "sale", "TS-001")
        every { authRepo.currentUser() } returns user

        coEvery { customerRepo.getLocalCustomers() } coAnswers { Result.success(emptyList()) }
        coEvery { branchRepo.syncFromRemote() } coAnswers { Result.success(Unit) }
        coEvery { branchRepo.observeBranches() } coAnswers { emptyList() }
        coEvery { projectRepo.countProjectsByPrefix(any()) } coAnswers { 0 }
        coEvery { projectRepo.getBranches() } coAnswers { Result.success(emptyList()) }
        coEvery { projectRepo.getMembersByBranch(any()) } coAnswers { Result.success(emptyList()) }
        coEvery { customerRepo.getContactPersons(any()) } coAnswers { Result.success(emptyList()) }
    }

    @After
    fun tearDown() {
        unmockkAll()
        Dispatchers.resetMain()
    }

    private fun initViewModel() {
        viewModel = AddProjectViewModel(projectRepo, customerRepo, authRepo, branchRepo)
    }

    @Test
    fun `Save success should update isSaved to true`() = runTest {
        // Arrange
        val sampleProject = Project(projectId = "P1", custId = "C1", projectName = "New Project Alpha")
        coEvery { projectRepo.createProject(any(), any()) } coAnswers { Result.success(sampleProject) }
        coEvery { projectRepo.addProjectMembers(any(), any(), any(), any()) } coAnswers { Result.success(Unit) }

        // Act
        initViewModel()
        advanceUntilIdle()

        viewModel.onEvent(AddProjectEvent.ProjectNameChanged("New Project Alpha"))
        viewModel.onEvent(AddProjectEvent.CustomerSelected("C1", "Client A"))
        viewModel.onEvent(AddProjectEvent.StatusChanged("Quotation"))
        viewModel.onEvent(AddProjectEvent.Save)

        advanceUntilIdle()

        // Assert
        val state = viewModel.uiState.value
        assertNull("Should not have save error, but got: ${state.saveError}", state.saveError)
        assertTrue("isSaved should be true after successful save", state.isSaved)
    }
    @Test
    fun `LoadProject success should populate state accurately`() = runTest {
        val project = Project(
            projectId = "P123",
            custId = "C1",
            projectName = "Project X",
            projectStatus = "Bidding",
            projectNumber = "NUM-001"
        )
        coEvery { projectRepo.getProjectById("P123") } coAnswers { Result.success(project) }
        coEvery { customerRepo.getCustomerById(any()) } coAnswers { Result.success(mockk(relaxed = true)) }

        initViewModel()
        advanceUntilIdle()

        viewModel.onEvent(AddProjectEvent.LoadProject("P123"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Project X", state.projectName)
        assertEquals("NUM-001", state.generatedProjectNumber)
    }
}
