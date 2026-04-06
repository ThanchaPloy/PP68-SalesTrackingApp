package com.example.pp68_salestrackingapp.ui.viewmodels.export

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.pp68_salestrackingapp.data.model.Project
import com.example.pp68_salestrackingapp.data.repository.ActivityRepository
import com.example.pp68_salestrackingapp.data.repository.ProjectRepository
import com.example.pp68_salestrackingapp.ui.screen.export.ExportViewModel
import com.example.pp68_salestrackingapp.ui.viewmodels.activity.ActivityCard
import io.mockk.coEvery
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class ExportViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private val activityRepo = mockk<ActivityRepository>(relaxed = true)
    private val projectRepo = mockk<ProjectRepository>(relaxed = true)
    private lateinit var viewModel: ExportViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { projectRepo.getAllProjectsFlow() } returns flowOf(emptyList())
        viewModel = ExportViewModel(activityRepo, projectRepo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadWeeklyData should filter by selected week and map fields`() = runTest {
        val inWeek = ActivityCard(
            activityId = "A1",
            activityType = "visit",
            projectName = "P1",
            companyName = "C1",
            contactName = null,
            objective = "Discuss",
            planStatus = "completed",
            plannedDate = "2026-04-08",
            plannedTime = "10:00",
            plannedEndTime = "11:00"
        )
        val outWeek = inWeek.copy(activityId = "A2", plannedDate = "2026-04-20")
        coEvery { activityRepo.getMyActivitiesWithDetails() } returns Result.success(listOf(outWeek, inWeek))

        viewModel.loadWeeklyData(LocalDate.parse("2026-04-08"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(1, state.activities.size)
        assertEquals("2026-04-08", state.activities.first().date)
        assertEquals("P1", state.activities.first().projectName)
        assertEquals("Discuss", state.activities.first().topic)
    }

    @Test
    fun `loadWeeklyData failure should set error`() = runTest {
        coEvery { activityRepo.getMyActivitiesWithDetails() } returns Result.failure(Exception("boom"))

        viewModel.loadWeeklyData(LocalDate.parse("2026-04-08"))
        advanceUntilIdle()

        assertEquals("boom", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `loadMonthlyData should include matching month and active non final projects`() = runTest {
        val ym = YearMonth.of(2026, 4)
        val pStarted = Project("P1", "C1", projectName = "Alpha", startDate = "2026-04-01", projectStatus = "Lead", expectedValue = 100.0)
        val pClosing = Project("P2", "C1", projectName = "Beta", closingDate = "2026-04-30", projectStatus = "Quotation", expectedValue = 200.0)
        val pActiveOtherMonth = Project("P3", "C1", projectName = "Gamma", startDate = "2026-03-01", projectStatus = "Assured", expectedValue = 300.0)
        val pFinalOtherMonth = Project("P4", "C1", projectName = "Delta", startDate = "2026-03-01", projectStatus = "Completed", expectedValue = 400.0)
        every { projectRepo.getAllProjectsFlow() } returns flowOf(listOf(pStarted, pClosing, pActiveOtherMonth, pFinalOtherMonth))

        viewModel.loadMonthlyData(ym)
        advanceUntilIdle()

        val names = viewModel.uiState.value.projects.map { it.projectName }
        assertTrue(names.contains("Alpha"))
        assertTrue(names.contains("Beta"))
        assertTrue(names.contains("Gamma"))
        assertFalse(names.contains("Delta"))
    }

    @Test
    fun `generateActivityCsvString should escape quotes`() = runTest {
        coEvery { activityRepo.getMyActivitiesWithDetails() } returns Result.success(
            listOf(
                ActivityCard(
                    activityId = "A1",
                    activityType = "visit",
                    projectName = "Project \"A\"",
                    companyName = "Company \"B\"",
                    contactName = null,
                    objective = "Topic \"C\"",
                    planStatus = "completed",
                    plannedDate = "2026-04-08",
                    plannedTime = null,
                    plannedEndTime = null
                )
            )
        )
        viewModel.loadWeeklyData(LocalDate.parse("2026-04-08"))
        advanceUntilIdle()

        val csv = viewModel.generateActivityCsvString()
        assertTrue(csv.contains("\"Project \"\"A\"\"\""))
        assertTrue(csv.contains("\"Company \"\"B\"\"\""))
        assertTrue(csv.contains("\"Topic \"\"C\"\"\""))
    }

    @Test
    fun `generateProjectCsvString should include header and rows`() = runTest {
        every { projectRepo.getAllProjectsFlow() } returns flowOf(
            listOf(
                Project(
                    projectId = "P1",
                    custId = "C1",
                    projectName = "Name \"X\"",
                    expectedValue = 50.0,
                    projectStatus = "Lead",
                    closingDate = "2026-04-09",
                    opportunityScore = "HOT"
                )
            )
        )

        viewModel.loadMonthlyData(YearMonth.of(2026, 4))
        advanceUntilIdle()

        val csv = viewModel.generateProjectCsvString()
        assertTrue(csv.startsWith("Project Name,Expected Value,Status,Score,Close Date"))
        assertTrue(csv.contains("\"Name \"\"X\"\"\",50.0,Lead,HOT,2026-04-09"))
    }
}
