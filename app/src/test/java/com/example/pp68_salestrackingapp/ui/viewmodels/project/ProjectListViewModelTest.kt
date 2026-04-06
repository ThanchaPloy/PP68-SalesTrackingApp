package com.example.pp68_salestrackingapp.ui.viewmodels.project

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.example.pp68_salestrackingapp.data.model.AuthUser
import com.example.pp68_salestrackingapp.data.model.Project
import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import com.example.pp68_salestrackingapp.data.repository.ProjectRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectListViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val dispatcher = StandardTestDispatcher()
    private val projectRepo = mockk<ProjectRepository>()
    private val authRepo = mockk<AuthRepository>()

    private lateinit var viewModel: ProjectListViewModel

    private val pastDate = LocalDate.now().minusDays(1).toString()
    private val futureDate = LocalDate.now().plusDays(1).toString()

    private val allProjects = listOf(
        Project(projectId = "P1", projectName = "Active Quotation", projectStatus = "Quotation", custId = "C1", projectNumber = "PRJ-001"),
        Project(projectId = "P2", projectName = "Completed", projectStatus = "Completed", custId = "C1", projectNumber = "PRJ-002"),
        Project(projectId = "P3", projectName = "PO Closed", projectStatus = "PO", closingDate = pastDate, custId = "C1", projectNumber = "PRJ-003"),
        Project(projectId = "P4", projectName = "PO Active", projectStatus = "PO", closingDate = futureDate, custId = "C1", projectNumber = "PRJ-004"),
        Project(projectId = "P5", projectName = "Lost", projectStatus = "Lost", custId = "C1", projectNumber = "PRJ-005"),
        Project(projectId = "P6", projectName = "Failed", projectStatus = "Failed", custId = "C1", projectNumber = "PRJ-006"),
        Project(projectId = "P7", projectName = "Hot Quotation", projectStatus = "Quotation", opportunityScore = "hot", custId = "C1", projectNumber = "PRJ-007"),
        Project(projectId = "P8", projectName = "No Score Quotation", projectStatus = "Quotation", opportunityScore = null, custId = "C1", projectNumber = "PRJ-008")
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { authRepo.currentUser() } returns AuthUser("U1", "u@test.com", "sale", "T1")
        every { projectRepo.getAllProjectsFlow() } returns flowOf(allProjects)
        every { projectRepo.searchProjectsFlow(any()) } answers {
            val q = firstArg<String>()
            flowOf(allProjects.filter { it.projectName.contains(q, ignoreCase = true) })
        }
        coEvery { projectRepo.refreshProjects(any()) } returns Result.success(Unit)
        coEvery { authRepo.logout() } just Runs
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun initVm() {
        viewModel = ProjectListViewModel(projectRepo, authRepo)
    }

    @Test
    fun givenInit_whenCreated_thenExposesAuthUserAndTriggersRefreshLoadingCycle() = runTest {
        initVm()
        runCurrent()
        assertTrue(viewModel.isLoading.value)

        advanceUntilIdle()

        assertEquals("U1", viewModel.authUser.value?.userId)
        assertFalse(viewModel.isLoading.value)
        coVerify(exactly = 1) { projectRepo.refreshProjects("U1") }
    }

    @Test
    fun givenNoCurrentUser_whenRefreshFromInit_thenSetsReloginErrorAndSkipsRefreshCall() = runTest {
        every { authRepo.currentUser() } returns null

        initVm()
        advanceUntilIdle()

        assertEquals("กรุณาเข้าสู่ระบบใหม่", viewModel.error.value)
        coVerify(exactly = 0) { projectRepo.refreshProjects(any()) }
    }

    @Test
    fun givenRefreshFailure_whenRefreshDataFromApi_thenStopsLoadingAndSetsError() = runTest {
        coEvery { projectRepo.refreshProjects("U1") } returns Result.failure(Exception("refresh boom"))
        initVm()
        advanceUntilIdle()

        assertEquals("refresh boom", viewModel.error.value)
        assertFalse(viewModel.isLoading.value)
    }

    @Test
    fun givenActiveTabDefault_whenProjectsCollected_thenReturnsOnlyActiveItems() = runTest {
        initVm()

        viewModel.projects.test {
            assertEquals(emptyList<Project>(), awaitItem())
            advanceTimeBy(305)
            val result = awaitItem()

            assertTrue(result.any { it.projectId == "P1" })
            assertTrue(result.any { it.projectId == "P4" })
            assertTrue(result.any { it.projectId == "P7" })
            assertTrue(result.any { it.projectId == "P8" })
            assertFalse(result.any { it.projectId == "P2" || it.projectId == "P3" || it.projectId == "P5" || it.projectId == "P6" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenClosedTab_whenSelected_thenReturnsCompletedAndPastPoOnly() = runTest {
        initVm()
        viewModel.onSelectTab(1)

        viewModel.projects.test {
            awaitItem()
            advanceTimeBy(305)
            val result = awaitItem()

            assertTrue(result.any { it.projectId == "P2" })
            assertTrue(result.any { it.projectId == "P3" })
            assertFalse(result.any { it.projectId == "P4" || it.projectId == "P5" || it.projectId == "P6" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenInactiveTab_whenSelected_thenReturnsLostAndFailedOnly() = runTest {
        initVm()
        viewModel.onSelectTab(2)

        viewModel.projects.test {
            awaitItem()
            advanceTimeBy(305)
            val result = awaitItem()

            assertEquals(setOf("P5", "P6"), result.map { it.projectId }.toSet())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenSearchQuery_whenChanged_thenUsesSearchFlowAndReturnsMatchedItems() = runTest {
        initVm()
        viewModel.onSearchChange("Hot")

        viewModel.projects.test {
            awaitItem()
            advanceTimeBy(305)
            val result = awaitItem()

            assertEquals(1, result.size)
            assertEquals("P7", result.first().projectId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenStatusFilter_whenToggledTwice_thenAddsAndRemovesSelection() = runTest {
        initVm()
        assertTrue(viewModel.selectedStatuses.value.isEmpty())

        viewModel.toggleStatusFilter("Quotation")
        assertEquals(setOf("Quotation"), viewModel.selectedStatuses.value)

        viewModel.toggleStatusFilter("Quotation")
        assertTrue(viewModel.selectedStatuses.value.isEmpty())
    }

    @Test
    fun givenScoreFilterLowercase_whenToggled_thenStoresUppercaseAndFiltersCorrectly() = runTest {
        initVm()
        viewModel.toggleStatusFilter("Quotation")
        viewModel.toggleScoreFilter("hot")

        assertEquals(setOf("HOT"), viewModel.selectedScores.value)

        viewModel.projects.test {
            awaitItem()
            advanceTimeBy(305)
            val result = awaitItem()

            assertEquals(1, result.size)
            assertEquals("P7", result.first().projectId)
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.toggleScoreFilter("HOT")
        assertTrue(viewModel.selectedScores.value.isEmpty())
    }

    @Test
    fun givenFiltersSet_whenResetFilters_thenClearsStatusAndScoreSets() = runTest {
        initVm()
        viewModel.toggleStatusFilter("Quotation")
        viewModel.toggleScoreFilter("HOT")
        assertTrue(viewModel.selectedStatuses.value.isNotEmpty())
        assertTrue(viewModel.selectedScores.value.isNotEmpty())

        viewModel.resetFilters()

        assertTrue(viewModel.selectedStatuses.value.isEmpty())
        assertTrue(viewModel.selectedScores.value.isEmpty())
    }

    @Test
    fun givenFlowThrows_whenProjectsCollected_thenEmitsEmptyAndSetsError() = runTest {
        every { projectRepo.getAllProjectsFlow() } returns flow { throw IllegalStateException("db fail") }
        initVm()

        viewModel.projects.test {
            assertEquals(emptyList<Project>(), awaitItem())
            advanceTimeBy(305)
            assertEquals(emptyList<Project>(), awaitItem())
            assertEquals("db fail", viewModel.error.value)
            assertFalse(viewModel.isLoading.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenErrorSet_whenClearError_thenErrorBecomesNull() = runTest {
        coEvery { projectRepo.refreshProjects("U1") } returns Result.failure(Exception("x"))
        initVm()
        advanceUntilIdle()
        assertEquals("x", viewModel.error.value)

        viewModel.clearError()

        assertNull(viewModel.error.value)
    }

    @Test
    fun givenLogoutCalled_whenInvoked_thenCallsAuthRepositoryLogout() = runTest {
        initVm()

        viewModel.logout()
        advanceUntilIdle()

        coVerify(exactly = 1) { authRepo.logout() }
    }
}
