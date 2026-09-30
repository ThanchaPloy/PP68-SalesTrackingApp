package com.example.pp68_salestrackingapp.ui.viewmodels.export

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.pp68_salestrackingapp.data.model.Project
import com.example.pp68_salestrackingapp.data.repository.ActivityRepository
import com.example.pp68_salestrackingapp.data.repository.ProjectRepository
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
    private val placeSearchRepo = mockk<com.example.pp68_salestrackingapp.data.repository.PlaceSearchRepository>(relaxed = true)
    private lateinit var viewModel: ExportViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { projectRepo.getAllProjectsFlow() } returns flowOf(emptyList())
        every { activityRepo.getAllResultsFlow() } returns flowOf(emptyList())
        viewModel = ExportViewModel(activityRepo, projectRepo, placeSearchRepo)
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

        viewModel.loadWeeklyData(LocalDate.parse("2026-04-08"), LocalDate.parse("2026-04-14"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(1, state.activities.size)
        assertEquals("2026-04-08", state.activities.first().date)
        assertEquals("P1", state.activities.first().projectName)
        assertEquals("Discuss", state.activities.first().topic)
    }

    // W6: รายงานต้องเห็น "missing" เหมือนที่แอปโชว์ "ขาดนัด" ไม่ใช่ raw status "planned" ดิบๆ
    @Test
    fun `loadWeeklyData should map an overdue planned activity to missing status`() = runTest {
        val yesterday = LocalDate.now().minusDays(1)
        val overdue = ActivityCard(
            activityId = "A1",
            // ต้องเป็น onsite: มีแค่ onsite ที่ต้องเช็คอิน จึงมีแค่ชนิดนี้ที่กลายเป็น "ขาดนัด" ได้
            // (ค่าชนิดจริงในแอปมี onsite/online/call เท่านั้น — "visit" เดิมในเทสต์นี้ไม่มีอยู่จริง)
            activityType = "onsite",
            projectName = "P1",
            companyName = "C1",
            contactName = null,
            objective = "Discuss",
            planStatus = "planned",
            plannedDate = yesterday.toString(),
            plannedTime = "10:00",
            plannedEndTime = "11:00"
        )
        coEvery { activityRepo.getMyActivitiesWithDetails() } returns Result.success(listOf(overdue))

        viewModel.loadWeeklyData(yesterday.minusDays(3), yesterday.plusDays(3))
        advanceUntilIdle()

        assertEquals("missing", viewModel.uiState.value.activities.first().status)
    }

    @Test
    fun `loadWeeklyData failure should set error`() = runTest {
        coEvery { activityRepo.getMyActivitiesWithDetails() } returns Result.failure(Exception("boom"))

        viewModel.loadWeeklyData(LocalDate.parse("2026-04-08"), LocalDate.parse("2026-04-14"))
        advanceUntilIdle()

        assertEquals("boom", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `loadWeeklyData should drop blank and invalid dates`() = runTest {
        val valid = ActivityCard(
            activityId = "A1",
            activityType = "visit",
            projectName = "P1",
            companyName = "C1",
            contactName = null,
            objective = "Discuss",
            planStatus = "completed",
            plannedDate = "2026-04-08",
            plannedTime = null,
            plannedEndTime = null
        )
        val blank = valid.copy(activityId = "A2", plannedDate = "")
        val invalid = valid.copy(activityId = "A3", plannedDate = "bad-date")
        coEvery { activityRepo.getMyActivitiesWithDetails() } returns Result.success(listOf(blank, invalid, valid))

        viewModel.loadWeeklyData(LocalDate.parse("2026-04-08"), LocalDate.parse("2026-04-14"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.activities.size)
        assertEquals("2026-04-08", state.activities.first().date)
    }

    // รายงานของเดือนที่เลือก = ที่ยังเดินอยู่ในเดือนนั้น + ที่แพ้ในเดือนนั้น
    // เดิมเงื่อนไข "สถานะยังไม่แพ้" ปล่อยโครงการที่ยังเดินผ่านทุกเดือน ตัวเลือกเดือนจึงแทบไม่มีผล
    private val alpha = Project("P1", "C1", projectName = "Alpha", startDate = "2026-04-01", projectStatus = "Lead", expectedValue = 100.0)
    private val beta  = Project("P2", "C1", projectName = "Beta", closingDate = "2026-04-30", projectStatus = "Quotation", expectedValue = 200.0)
    private val gamma = Project("P3", "C1", projectName = "Gamma", startDate = "2026-03-01", projectStatus = "Assured", expectedValue = 300.0)
    private val delta = Project("P4", "C1", projectName = "Delta", startDate = "2026-03-01", closingDate = "2026-03-20", projectStatus = "Lost", expectedValue = 400.0)

    @Test
    fun `monthly report for April skips a project lost back in March`() = runTest {
        every { projectRepo.getAllProjectsFlow() } returns flowOf(listOf(alpha, beta, gamma, delta))

        viewModel.loadMonthlyData(YearMonth.of(2026, 4))
        advanceUntilIdle()

        assertEquals(
            listOf("Alpha", "Beta", "Gamma"),
            viewModel.uiState.value.projects.map { it.projectName }
        )
    }

    // เลือกเดือนอื่นต้องได้รายการต่างกันจริง ไม่ใช่รายการเดิมทุกเดือน:
    // มี.ค. — Alpha ยังไม่เริ่ม (เริ่ม 1 เม.ย.), Delta แพ้เดือนนี้จึงต้องขึ้น
    @Test
    fun `monthly report for March shows the March loss and hides a project that starts in April`() = runTest {
        every { projectRepo.getAllProjectsFlow() } returns flowOf(listOf(alpha, beta, gamma, delta))

        viewModel.loadMonthlyData(YearMonth.of(2026, 3))
        advanceUntilIdle()

        assertEquals(
            listOf("Beta", "Gamma", "Delta"),
            viewModel.uiState.value.projects.map { it.projectName }
        )
    }

    // โครงการที่ปิดไปแล้วก่อนเดือนที่เลือก ต้องไม่ลอยมาอยู่ในรายงานเดือนหลัง ๆ
    @Test
    fun `a project already closed before the month is not listed in it`() = runTest {
        val done = Project("P5", "C1", projectName = "Done", startDate = "2026-01-05", closingDate = "2026-02-10", projectStatus = "PO")
        every { projectRepo.getAllProjectsFlow() } returns flowOf(listOf(done))

        viewModel.loadMonthlyData(YearMonth.of(2026, 4))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.projects.isEmpty())
    }

    // สถานะในรายงานต้องเป็นป้ายที่ผู้ใช้อ่านได้ (ผ่าน ProjectStages) ไม่ใช่รหัสที่เก็บใน DB
    // ไม่มี master data โหลดอยู่ในเทสต์ labelFor จึง fallback เป็นรหัสเดิม — ที่คุมคือ "ต้องผ่าน labelFor"
    @Test
    fun `monthly report exposes the stage label, not whatever raw code happens to be stored`() = runTest {
        every { projectRepo.getAllProjectsFlow() } returns flowOf(
            listOf(Project("P1", "C1", projectName = "Alpha", projectStatus = "Make a Decision"))
        )

        viewModel.loadMonthlyData(YearMonth.of(2026, 4))
        advanceUntilIdle()

        assertEquals(
            com.example.pp68_salestrackingapp.utils.ProjectStages.labelFor("Make a Decision"),
            viewModel.uiState.value.projects.first().status
        )
    }

    @Test
    fun `loadMonthlyData invalid date should still include project by fallback`() = runTest {
        every { projectRepo.getAllProjectsFlow() } returns flowOf(
            listOf(
                Project(
                    projectId = "P1",
                    custId = "C1",
                    projectName = "InvalidDateProject",
                    startDate = "not-a-date",
                    projectStatus = "PO"
                )
            )
        )

        viewModel.loadMonthlyData(YearMonth.of(2026, 4))
        advanceUntilIdle()

        assertEquals(listOf("InvalidDateProject"), viewModel.uiState.value.projects.map { it.projectName })
    }

    @Test
    fun `loadMonthlyData exception should set error`() = runTest {
        every { projectRepo.getAllProjectsFlow() } throws RuntimeException("repo down")

        viewModel.loadMonthlyData(YearMonth.of(2026, 4))
        advanceUntilIdle()

        assertEquals("repo down", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)
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
        viewModel.loadWeeklyData(LocalDate.parse("2026-04-08"), LocalDate.parse("2026-04-14"))
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
        assertTrue(csv.startsWith("\uFEFFProject Name,Expected Value,Status,Score,Close Date"))
        assertTrue(csv.contains("\"Name \"\"X\"\"\",50.0,Lead,HOT,2026-04-09"))
    }

    @Test
    fun `generate csv functions should return header only when empty`() = runTest {
        val activityCsv = viewModel.generateActivityCsvString()
        val projectCsv = viewModel.generateProjectCsvString()

        assertTrue(activityCsv.startsWith("\uFEFF\u0E27\u0E31\u0E19\u0E17\u0E35\u0E48 (Date)"))
        assertEquals("\uFEFFProject Name,Expected Value,Status,Score,Close Date\n", projectCsv)
    }

    @Test
    fun `loadWeeklyData should map post-sales record details and photos`() = runTest {
        val activity = ActivityCard(
            activityId = "A1",
            activityType = "visit",
            projectName = "Project Alpha",
            companyName = "Company A",
            contactName = null,
            objective = "Meeting",
            planStatus = "completed",
            plannedDate = "2026-04-08",
            plannedTime = null,
            plannedEndTime = null
        )
        val result = com.example.pp68_salestrackingapp.data.model.ActivityResult(
            resultId = "RES-01",
            activityId = "A1",
            reportDate = "2026-04-08",
            newStatus = "Quotation",
            opportunityScore = "80%",
            dmInvolved = true,
            isProposalSent = true,
            proposalDate = "2026-04-08",
            competitorCount = 2,
            summary = "Meeting summary note",
            photoUrl = "https://example.com/photo1.jpg"
        )
        coEvery { activityRepo.getMyActivitiesWithDetails() } returns Result.success(listOf(activity))
        every { activityRepo.getAllResultsFlow() } returns flowOf(listOf(result))
        coEvery { activityRepo.getResultPhotosBatch(any()) } returns mapOf("RES-01" to listOf("https://example.com/photo2.jpg"))

        viewModel.loadWeeklyData(LocalDate.parse("2026-04-08"), LocalDate.parse("2026-04-14"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.activities.size)
        val item = state.activities.first()
        assertEquals(1, item.resultDetails.size)
        val detail = item.resultDetails.first()
        assertEquals("Quotation", detail.newStatus)
        assertEquals("80%", detail.opportunityScore)
        assertTrue(detail.dmInvolved)
        assertTrue(detail.isProposalSent)
        assertEquals(2, detail.competitorCount)
        assertEquals("Meeting summary note", detail.summary)
        assertEquals(listOf("https://example.com/photo1.jpg", "https://example.com/photo2.jpg"), detail.photoUrls)
    }

    @Test
    fun `loadWeeklyData should take only the latest result version when duplicate results exist`() = runTest {
        val activity = ActivityCard(
            activityId = "A1",
            activityType = "visit",
            projectName = "Project Beta",
            companyName = "Company B",
            contactName = null,
            objective = "Discussion",
            planStatus = "completed",
            plannedDate = "2026-04-08",
            plannedTime = null,
            plannedEndTime = null
        )
        val oldResult = com.example.pp68_salestrackingapp.data.model.ActivityResult(
            resultId = "RES-01",
            activityId = "A1",
            reportDate = "2026-04-08",
            version = 1,
            isLatest = false,
            summary = "Old version summary"
        )
        val newResult = com.example.pp68_salestrackingapp.data.model.ActivityResult(
            resultId = "RES-02",
            activityId = "A1",
            reportDate = "2026-04-08",
            version = 2,
            isLatest = true,
            summary = "Latest version summary"
        )
        coEvery { activityRepo.getMyActivitiesWithDetails() } returns Result.success(listOf(activity))
        every { activityRepo.getAllResultsFlow() } returns flowOf(listOf(oldResult, newResult))

        viewModel.loadWeeklyData(LocalDate.parse("2026-04-08"), LocalDate.parse("2026-04-14"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.activities.size)
        val item = state.activities.first()
        assertEquals(1, item.resultDetails.size)
        assertEquals("Latest version summary", item.resultDetails.first().summary)
    }
}
