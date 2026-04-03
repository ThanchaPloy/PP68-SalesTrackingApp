package com.example.pp68_salestrackingapp.ui.viewmodels.activity

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.example.pp68_salestrackingapp.data.model.AuthUser
import com.example.pp68_salestrackingapp.data.model.Project
import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import com.example.pp68_salestrackingapp.data.repository.ProjectRepository
import com.example.pp68_salestrackingapp.ui.screen.project.ProjectListViewModel
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectListViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private val projectRepo = mockk<ProjectRepository>()
    private val authRepo    = mockk<AuthRepository>()
    private lateinit var viewModel: ProjectListViewModel

    private val today = LocalDate.now().toString()
    private val pastDate = LocalDate.now().minusDays(1).toString()
    private val futureDate = LocalDate.now().plusDays(1).toString()

    private val sampleProjects = listOf(
        Project(projectId = "P1", projectName = "Active", projectStatus = "Quotation", custId = "C1", projectNumber = "PRJ-001"),
        Project(projectId = "P2", projectName = "Completed", projectStatus = "Completed", custId = "C1", projectNumber = "PRJ-002"),
        Project(projectId = "P3", projectName = "PO Closed", projectStatus = "PO", closingDate = pastDate, custId = "C1", projectNumber = "PRJ-003"),
        Project(projectId = "P4", projectName = "PO Active", projectStatus = "PO", closingDate = futureDate, custId = "C1", projectNumber = "PRJ-004"),
        Project(projectId = "P5", projectName = "Lost", projectStatus = "Lost", custId = "C1", projectNumber = "PRJ-005"),
        Project(projectId = "P6", projectName = "Failed", projectStatus = "Failed", custId = "C1", projectNumber = "PRJ-006")
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { authRepo.currentUser() } returns AuthUser("U1", "t@t.com", "sale", "T1")
        every { projectRepo.getAllProjectsFlow() } returns flowOf(sampleProjects)
        every { projectRepo.searchProjectsFlow(any()) } returns flowOf(emptyList())
        coEvery { projectRepo.refreshProjects(any()) } coAnswers { Result.success(Unit) }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun initViewModel() {
        viewModel = ProjectListViewModel(projectRepo, authRepo)
    }

    @Test
    fun `projects flow error should be caught and set error state`() = runTest {
        val errorMsg = "Database Failure"
        every { projectRepo.getAllProjectsFlow() } returns flow { throw Exception(errorMsg) }

        initViewModel()

        viewModel.projects.test {
            assertEquals(emptyList<Project>(), awaitItem()) 
            advanceTimeBy(305)
            runCurrent()
            assertEquals(errorMsg, viewModel.error.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onSelectTab 0 should show Active projects only`() = runTest {
        initViewModel()
        viewModel.onSelectTab(0)

        viewModel.projects.test {
            awaitItem() // initial
            advanceTimeBy(305)
            val result = awaitItem()
            
            // P1 (Quotation), P4 (PO future) should be Active
            assertTrue(result.any { it.projectId == "P1" })
            assertTrue(result.any { it.projectId == "P4" })
            assertFalse(result.any { it.projectId == "P2" || it.projectId == "P3" || it.projectId == "P5" || it.projectId == "P6" })
        }
    }

    @Test
    fun `onSelectTab 1 should show Closed projects (Completed or PO past date)`() = runTest {
        initViewModel()
        viewModel.onSelectTab(1)

        viewModel.projects.test {
            awaitItem()
            advanceTimeBy(305)
            val result = awaitItem()
            
            assertTrue(result.any { it.projectId == "P2" }) // Completed
            assertTrue(result.any { it.projectId == "P3" }) // PO past
            assertFalse(result.any { it.projectId == "P1" || it.projectId == "P4" || it.projectId == "P5" || it.projectId == "P6" })
        }
    }

    @Test
    fun `onSelectTab 2 should show Inactive projects (Lost or Failed)`() = runTest {
        initViewModel()
        viewModel.onSelectTab(2)

        viewModel.projects.test {
            awaitItem()
            advanceTimeBy(305)
            val result = awaitItem()
            
            assertTrue(result.any { it.projectId == "P5" }) // Lost
            assertTrue(result.any { it.projectId == "P6" }) // Failed
            assertFalse(result.any { it.projectId == "P1" || it.projectId == "P2" || it.projectId == "P3" || it.projectId == "P4" })
        }
    }

    @Test
    fun `toggleStatusFilter and toggleScoreFilter should work together`() = runTest {
        initViewModel()
        
        val pWithHot = Project(projectId = "P7", projectName = "Hot Proj", projectStatus = "Quotation", opportunityScore = "HOT", custId = "C1", projectNumber = "PRJ-007")
        every { projectRepo.getAllProjectsFlow() } returns flowOf(sampleProjects + pWithHot)

        viewModel.toggleStatusFilter("Quotation")
        viewModel.toggleScoreFilter("HOT")

        viewModel.projects.test {
            awaitItem()
            advanceTimeBy(305)
            val result = awaitItem()
            
            assertTrue(result.all { it.projectStatus == "Quotation" && it.opportunityScore?.uppercase() == "HOT" })
            assertEquals(1, result.size)
            assertEquals("P7", result[0].projectId)
        }
    }

    @Test
    fun `logout should call auth repository`() = runTest {
        coEvery { authRepo.logout() } just Runs
        initViewModel()
        
        viewModel.logout()
        advanceUntilIdle()
        
        coVerify { authRepo.logout() }
    }
}
