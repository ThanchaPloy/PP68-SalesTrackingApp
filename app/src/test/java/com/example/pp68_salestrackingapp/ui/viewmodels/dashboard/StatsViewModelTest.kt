package com.example.pp68_salestrackingapp.ui.viewmodels.dashboard

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.pp68_salestrackingapp.data.model.AuthUser
import com.example.pp68_salestrackingapp.data.model.Project
import com.example.pp68_salestrackingapp.data.repository.ActivityRepository
import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import com.example.pp68_salestrackingapp.data.repository.ProjectRepository
import com.example.pp68_salestrackingapp.ui.screen.dashboard.StatsViewModel
import com.example.pp68_salestrackingapp.ui.viewmodels.activity.ActivityCard
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val dispatcher = StandardTestDispatcher()
    private val projectRepo = mockk<ProjectRepository>(relaxed = true)
    private val activityRepo = mockk<ActivityRepository>(relaxed = true)
    private val authRepo = mockk<AuthRepository>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `load success should compute stats and clear loading`() = runTest {
        val today = LocalDate.now().toString()
        val user = AuthUser("U1", "u@test.com", "sale")

        every { authRepo.currentUser() } returns user
        every { projectRepo.getAllProjectsFlow() } returns flowOf(
            listOf(
                Project(
                    projectId = "P1",
                    custId = "C1",
                    projectName = "Lead",
                    projectStatus = "Lead",
                    createdAt = "${today}T08:00:00",
                    expectedValue = 100.0,
                    opportunityScore = "HOT",
                    closingDate = today
                ),
                Project(
                    projectId = "P2",
                    custId = "C1",
                    projectName = "PO",
                    projectStatus = "PO",
                    createdAt = "${today}T09:00:00",
                    expectedValue = 200.0,
                    opportunityScore = "WARM",
                    closingDate = today
                ),
                Project(
                    projectId = "P3",
                    custId = "C1",
                    projectName = "Lost",
                    projectStatus = "Lost",
                    createdAt = "${today}T10:00:00",
                    expectedValue = 300.0,
                    opportunityScore = "COLD"
                )
            )
        )
        coEvery { activityRepo.getMyActivitiesWithDetails() } returns Result.success(
            listOf(
                ActivityCard(
                    activityId = "A1",
                    activityType = "visit",
                    projectName = "Lead",
                    companyName = "Company",
                    contactName = null,
                    objective = null,
                    planStatus = "completed",
                    plannedDate = today,
                    plannedTime = "09:00",
                    plannedEndTime = "10:00"
                ),
                ActivityCard(
                    activityId = "A2",
                    activityType = "visit",
                    projectName = "Lead",
                    companyName = "Company",
                    contactName = null,
                    objective = null,
                    planStatus = "planned",
                    plannedDate = today,
                    plannedTime = "11:00",
                    plannedEndTime = "12:00"
                )
            )
        )

        val vm = StatsViewModel(projectRepo, activityRepo, authRepo)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals(user, state.authUser)
        assertTrue(state.weeklyNewLeads >= 1)
        assertTrue(state.weeklyVisitCount >= 1)
        assertTrue(state.monthlyClosedSales >= 200.0)
        assertTrue(state.totalProjectValue >= 300.0)
        assertTrue(state.pipelineStages.isNotEmpty())
        assertNotNull(state.opportunityGroups.find { it.score == "HOT" })
    }

    @Test
    fun `load failure should expose error`() = runTest {
        every { authRepo.currentUser() } returns null
        every { projectRepo.getAllProjectsFlow() } returns flowOf(emptyList())
        coEvery { activityRepo.getMyActivitiesWithDetails() } returns Result.failure(Exception("load fail"))

        val vm = StatsViewModel(projectRepo, activityRepo, authRepo)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals("load fail", state.error)
    }

    @Test
    fun `logout should call auth repository`() = runTest {
        every { authRepo.currentUser() } returns null
        every { projectRepo.getAllProjectsFlow() } returns flowOf(emptyList())
        coEvery { activityRepo.getMyActivitiesWithDetails() } returns Result.success(emptyList())
        coEvery { authRepo.logout() } returns Unit
        val vm = StatsViewModel(projectRepo, activityRepo, authRepo)
        advanceUntilIdle()

        vm.logout()
        advanceUntilIdle()

        coVerify(exactly = 1) { authRepo.logout() }
    }

    @Test
    fun `closing month logic should include only same month active project closings`() = runTest {
        val today = LocalDate.now()
        val thisMonthDate = today.withDayOfMonth(1).toString()
        val nextMonthDate = today.plusMonths(1).withDayOfMonth(1).toString()

        every { authRepo.currentUser() } returns AuthUser("U1", "u@test.com", "sale")
        every { projectRepo.getAllProjectsFlow() } returns flowOf(
            listOf(
                Project(
                    projectId = "P1",
                    custId = "C1",
                    projectName = "Active this month",
                    projectStatus = "Quotation",
                    createdAt = "${today}T08:00:00",
                    closingDate = thisMonthDate
                ),
                Project(
                    projectId = "P2",
                    custId = "C1",
                    projectName = "Completed this month",
                    projectStatus = "Completed",
                    createdAt = "${today}T09:00:00",
                    closingDate = thisMonthDate
                ),
                Project(
                    projectId = "P3",
                    custId = "C1",
                    projectName = "Next month",
                    projectStatus = "Assured",
                    createdAt = "${today}T10:00:00",
                    closingDate = nextMonthDate
                )
            )
        )
        coEvery { activityRepo.getMyActivitiesWithDetails() } returns Result.success(
            listOf(
                ActivityCard(
                    activityId = "A1",
                    activityType = "visit",
                    projectName = "P",
                    companyName = "C",
                    contactName = null,
                    objective = null,
                    planStatus = "completed",
                    plannedDate = today.toString(),
                    plannedTime = "09:00",
                    plannedEndTime = "10:00"
                )
            )
        )

        val vm = StatsViewModel(projectRepo, activityRepo, authRepo)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.closingThisMonth >= 1)
    }

    @Test
    fun `load should compute weeklyNewProjects and filter pipeline zero-count stages`() = runTest {
        val today = LocalDate.now().toString()
        every { authRepo.currentUser() } returns AuthUser("U1", "u@test.com", "sale")
        every { projectRepo.getAllProjectsFlow() } returns flowOf(
            listOf(
                Project(projectId = "P1", custId = "C1", projectName = "N1", projectStatus = "Quotation", createdAt = "${today}T01:00:00", expectedValue = 150.0),
                Project(projectId = "P2", custId = "C1", projectName = "Lead", projectStatus = "Lead", createdAt = "${today}T02:00:00", expectedValue = 50.0),
                Project(projectId = "P3", custId = "C1", projectName = "Lost", projectStatus = "Lost", createdAt = "${today}T03:00:00", expectedValue = 10.0)
            )
        )
        coEvery { activityRepo.getMyActivitiesWithDetails() } returns Result.success(emptyList())

        val vm = StatsViewModel(projectRepo, activityRepo, authRepo)
        advanceUntilIdle()
        val state = vm.uiState.value

        assertEquals(1, state.weeklyNewProjects)
        assertTrue(state.pipelineStages.any { it.stage == "Quotation" && it.count == 1 })
        assertTrue(state.pipelineStages.any { it.stage == "Lead" && it.count == 1 })
        assertTrue(state.pipelineStages.none { it.stage == "Bidding" })
        assertEquals(200.0, state.totalProjectValue, 0.0001)
    }

    @Test
    fun `load should ignore invalid or null dates and keep date-based counts at zero`() = runTest {
        every { authRepo.currentUser() } returns AuthUser("U1", "u@test.com", "sale")
        every { projectRepo.getAllProjectsFlow() } returns flowOf(
            listOf(
                Project(projectId = "P1", custId = "C1", projectName = "Lead invalid", projectStatus = "Lead", createdAt = "INVALID_DATE", closingDate = "INVALID_DATE"),
                Project(projectId = "P2", custId = "C1", projectName = "Lead null", projectStatus = "Lead", createdAt = null, closingDate = null)
            )
        )
        coEvery { activityRepo.getMyActivitiesWithDetails() } returns Result.success(
            listOf(
                ActivityCard(
                    activityId = "A1",
                    activityType = "visit",
                    projectName = "X",
                    companyName = "C",
                    contactName = null,
                    objective = null,
                    planStatus = "completed",
                    plannedDate = "BAD_DATE",
                    plannedTime = "09:00",
                    plannedEndTime = "10:00"
                )
            )
        )

        val vm = StatsViewModel(projectRepo, activityRepo, authRepo)
        advanceUntilIdle()
        val state = vm.uiState.value

        assertEquals(0, state.weeklyNewLeads)
        assertEquals(0, state.weeklyVisitCount)
        assertEquals(0, state.monthlyNewLeads)
        assertEquals(0, state.closingThisMonth)
    }

    @Test
    fun `load should exclude closed statuses from opportunity groups`() = runTest {
        val today = LocalDate.now().toString()
        every { authRepo.currentUser() } returns AuthUser("U1", "u@test.com", "sale")
        every { projectRepo.getAllProjectsFlow() } returns flowOf(
            listOf(
                Project(projectId = "P1", custId = "C1", projectName = "Hot Active", projectStatus = "Quotation", opportunityScore = "hot", expectedValue = 100.0, createdAt = today),
                Project(projectId = "P2", custId = "C1", projectName = "Hot Completed", projectStatus = "Completed", opportunityScore = "HOT", expectedValue = 999.0, createdAt = today),
                Project(projectId = "P3", custId = "C1", projectName = "Warm Lost", projectStatus = "Lost", opportunityScore = "WARM", expectedValue = 200.0, createdAt = today),
                Project(projectId = "P4", custId = "C1", projectName = "Cold Active", projectStatus = "Assured", opportunityScore = "COLD", expectedValue = 50.0, createdAt = today)
            )
        )
        coEvery { activityRepo.getMyActivitiesWithDetails() } returns Result.success(emptyList())

        val vm = StatsViewModel(projectRepo, activityRepo, authRepo)
        advanceUntilIdle()
        val groups = vm.uiState.value.opportunityGroups.associateBy { it.score }

        assertEquals(1, groups["HOT"]?.count)
        assertEquals(100.0, groups["HOT"]?.totalValue ?: 0.0, 0.0001)
        assertEquals(0, groups["WARM"]?.count)
        assertEquals(1, groups["COLD"]?.count)
        assertEquals(50.0, groups["COLD"]?.totalValue ?: 0.0, 0.0001)
    }

    @Test
    fun `load should expose error when project flow throws`() = runTest {
        every { authRepo.currentUser() } returns null
        every { projectRepo.getAllProjectsFlow() } returns flow { throw IllegalStateException("project flow error") }
        coEvery { activityRepo.getMyActivitiesWithDetails() } returns Result.success(emptyList())

        val vm = StatsViewModel(projectRepo, activityRepo, authRepo)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isLoading)
        assertEquals("project flow error", vm.uiState.value.error)
    }
}
