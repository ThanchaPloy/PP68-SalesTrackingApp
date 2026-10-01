package com.example.pp68_salestrackingapp.ui.viewmodels.project

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.pp68_salestrackingapp.data.model.Project
import com.example.pp68_salestrackingapp.data.model.ProjectFactorLog
import com.example.pp68_salestrackingapp.data.model.ProjectFactorSnapshot
import com.example.pp68_salestrackingapp.data.repository.ProjectRepository
import com.example.pp68_salestrackingapp.utils.DealFactors
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditProjectFactorsViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private val projectRepo = mockk<ProjectRepository>(relaxed = true)
    private lateinit var viewModel: EditProjectFactorsViewModel

    // ป้ายจริงจาก DealFactors fallback — ใช้ค่าจริงแทน hardcode กันเพี้ยนเมื่อ master data เปลี่ยน
    private val dealPositionLabel     = DealFactors.labelToCode(DealFactors.DEAL_POSITION).keys.first()
    private val solutionLabel         = DealFactors.labelToCode(DealFactors.PREVIOUS_SOLUTION).keys.first()
    private val counterpartyLabel     = DealFactors.labelToCode(DealFactors.COUNTERPARTY_TYPE).keys.first()
    private val responseSpeedLabel    = DealFactors.labelToCode(DealFactors.RESPONSE_SPEED).keys.first()

    private val answeredProject = Project(
        projectId = "P01",
        projectName = "Alpha",
        dealPosition = DealFactors.labelToCode(DealFactors.DEAL_POSITION)[dealPositionLabel],
        previousSolution = DealFactors.labelToCode(DealFactors.PREVIOUS_SOLUTION)[solutionLabel],
        counterpartyType = DealFactors.labelToCode(DealFactors.COUNTERPARTY_TYPE)[counterpartyLabel],
        responseSpeed = DealFactors.labelToCode(DealFactors.RESPONSE_SPEED)[responseSpeedLabel],
        isProposalSent = true,
        proposalDate = "2026-04-06",
        competitorCount = 3
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { projectRepo.getFactorHistory(any()) } returns Result.success(emptyList())
        viewModel = EditProjectFactorsViewModel(projectRepo)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `load converts stored codes back into labels for the dropdowns`() = runTest(testDispatcher) {
        coEvery { projectRepo.getProjectById("P01") } returns Result.success(answeredProject)

        viewModel.load("P01")
        advanceUntilIdle()

        val s = viewModel.uiState.value
        assertEquals(dealPositionLabel, s.dealPosition)
        assertEquals(solutionLabel, s.previousSolution)
        assertEquals(counterpartyLabel, s.counterpartyType)
        assertEquals(responseSpeedLabel, s.responseSpeed)
        assertTrue(s.isProposalSent)
        assertEquals("2026-04-06", s.proposalDate)
        assertEquals("3", s.competitorCount)
    }

    @Test
    fun `save sends codes not labels, so the backend CHECK constraints still match`() = runTest(testDispatcher) {
        coEvery { projectRepo.getProjectById("P01") } returns Result.success(answeredProject)
        coEvery { projectRepo.updateProjectFields(any(), any()) } returns Result.success(Unit)

        viewModel.load("P01")
        advanceUntilIdle()
        viewModel.save()
        advanceUntilIdle()

        val fields = slot<Map<String, Any?>>()
        coVerify { projectRepo.updateProjectFields(eq("P01"), capture(fields)) }
        assertEquals(answeredProject.dealPosition, fields.captured["deal_position"])
        assertEquals(answeredProject.previousSolution, fields.captured["current_solution"])
        assertEquals(answeredProject.counterpartyType, fields.captured["counterparty_type"])
        assertEquals(answeredProject.responseSpeed, fields.captured["response_speed"])
        assertEquals(true, fields.captured["is_proposal_sent"])
        assertEquals(3, fields.captured["competitor_count"])
        assertTrue(viewModel.uiState.value.isSaved)
    }

    // เคสสำคัญ: Gson ตัดคีย์ที่ค่าเป็น null ออกจาก body ส่ง null มาล้างค่าไม่ได้
    // จึงต้องส่ง "" เพื่อบอก backend ให้ล้าง proposal_date เมื่อปิดสวิตช์ "ส่งใบเสนอราคาแล้ว"
    @Test
    fun `turning the proposal switch off clears the date with an empty string, not null`() = runTest(testDispatcher) {
        coEvery { projectRepo.getProjectById("P01") } returns Result.success(answeredProject)
        coEvery { projectRepo.updateProjectFields(any(), any()) } returns Result.success(Unit)

        viewModel.load("P01")
        advanceUntilIdle()
        viewModel.onProposalSentToggle(false)
        assertNull(viewModel.uiState.value.proposalDate)

        viewModel.save()
        advanceUntilIdle()

        val fields = slot<Map<String, Any?>>()
        coVerify { projectRepo.updateProjectFields(any(), capture(fields)) }
        assertEquals(false, fields.captured["is_proposal_sent"])
        assertEquals("", fields.captured["proposal_date"])
    }

    @Test
    fun `saving with the proposal switch on but no date is rejected before hitting the API`() = runTest(testDispatcher) {
        coEvery { projectRepo.getProjectById("P01") } returns
            Result.success(answeredProject.copy(proposalDate = null))

        viewModel.load("P01")
        advanceUntilIdle()
        viewModel.save()
        advanceUntilIdle()

        assertEquals("กรุณาระบุวันที่ส่งใบเสนอราคา", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isSaved)
        coVerify(exactly = 0) { projectRepo.updateProjectFields(any(), any()) }
    }

    @Test
    fun `saving with an unanswered factor is rejected before hitting the API`() = runTest(testDispatcher) {
        coEvery { projectRepo.getProjectById("P01") } returns
            Result.success(answeredProject.copy(dealPosition = null))

        viewModel.load("P01")
        advanceUntilIdle()
        viewModel.save()
        advanceUntilIdle()

        assertEquals("กรุณาเลือกปัจจัยของดีลให้ครบ", viewModel.uiState.value.error)
        coVerify(exactly = 0) { projectRepo.updateProjectFields(any(), any()) }
    }

    @Test
    fun `competitor count ignores non-digits so the int parse at save time cannot fail`() = runTest(testDispatcher) {
        coEvery { projectRepo.getProjectById("P01") } returns Result.success(answeredProject)

        viewModel.load("P01")
        advanceUntilIdle()
        viewModel.onCompetitorCountChange("1a2b9")

        assertEquals("12", viewModel.uiState.value.competitorCount)
    }

    // snapshot เรียงใหม่สุดก่อน แถวเก่าสุดไม่มีอะไรให้เทียบ = ค่าตั้งต้น (old เป็น null)
    @Test
    fun `history diffs consecutive snapshots into per-edit change lists`() = runTest(testDispatcher) {
        val older = ProjectFactorLog(
            logId = 1, projectCode = "P01", changedAt = "2026-04-01T10:00:00Z", changedBy = "EMP-1",
            factors = ProjectFactorSnapshot(
                dealPosition = "incumbent", previousSolution = "no_solution",
                counterpartyType = "direct_main_contractor", responseSpeed = "fast",
                isProposalSent = false, proposalDate = null, competitorCount = 1
            )
        )
        // ครั้งหลังแก้ 2 ฟิลด์: dealPosition กับ competitorCount
        val newer = older.copy(
            logId = 2, changedAt = "2026-04-05T10:00:00Z", changedBy = "EMP-2",
            factors = older.factors.copy(dealPosition = "invited_to_compare", competitorCount = 4)
        )

        val entries = viewModel.buildHistoryEntries(listOf(newer, older))

        assertEquals(2, entries.size)
        // รายการแรก (ใหม่สุด) ต้องมีเฉพาะ 2 ฟิลด์ที่เปลี่ยนจริง ไม่ใช่ทั้ง 7
        val latest = entries.first()
        assertEquals("EMP-2", latest.changedBy)
        assertEquals(setOf("deal_position", "competitor_count"), latest.changes.map { it.fieldKey }.toSet())
        val dealChange = latest.changes.first { it.fieldKey == "deal_position" }
        assertEquals("incumbent", dealChange.oldValue)
        assertEquals("invited_to_compare", dealChange.newValue)

        // รายการเก่าสุด = ค่าตั้งต้น old ต้องเป็น null ทุกฟิลด์ที่มีค่า
        val initial = entries.last()
        assertTrue(initial.changes.all { it.oldValue == null })
        assertEquals("fast", initial.changes.first { it.fieldKey == "response_speed" }.newValue)
    }

    // false กับ 0 คือคำตอบที่ถูกต้อง ไม่ใช่ "ไม่มีค่า" — diff ต้องจับการเปลี่ยนเป็น/จากค่าพวกนี้ได้
    @Test
    fun `history diff treats false and zero as real values, not as missing`() = runTest(testDispatcher) {
        val older = ProjectFactorLog(
            logId = 1, projectCode = "P01", changedAt = "2026-04-01T10:00:00Z",
            factors = ProjectFactorSnapshot(isProposalSent = true, competitorCount = 5)
        )
        val newer = older.copy(
            logId = 2, changedAt = "2026-04-02T10:00:00Z",
            factors = ProjectFactorSnapshot(isProposalSent = false, competitorCount = 0)
        )

        val changes = viewModel.buildHistoryEntries(listOf(newer, older)).first().changes

        assertEquals("false", changes.first { it.fieldKey == "is_proposal_sent" }.newValue)
        assertEquals("0", changes.first { it.fieldKey == "competitor_count" }.newValue)
    }

    // trigger กันแถวซ้ำอยู่แล้ว แต่ถ้ามีหลุดมา ต้องไม่โชว์เป็นรายการเปล่า
    @Test
    fun `history skips entries where nothing actually differs`() = runTest(testDispatcher) {
        val snap = ProjectFactorSnapshot(dealPosition = "incumbent", competitorCount = 2)
        val older = ProjectFactorLog(logId = 1, projectCode = "P01", changedAt = "2026-04-01T10:00:00Z", factors = snap)
        val duplicate = older.copy(logId = 2, changedAt = "2026-04-02T10:00:00Z", factors = snap)

        val entries = viewModel.buildHistoryEntries(listOf(duplicate, older))

        assertEquals(1, entries.size)
    }

    // ประวัติอ่านจาก server เท่านั้น ออฟไลน์ต้องยังแก้ค่าปัจจุบันได้ ไม่ใช่ทั้งหน้าพัง
    @Test
    fun `history failure leaves the form usable and only flags the history section`() = runTest(testDispatcher) {
        coEvery { projectRepo.getProjectById("P01") } returns Result.success(answeredProject)
        coEvery { projectRepo.getFactorHistory("P01") } returns Result.failure(Exception("offline"))

        viewModel.load("P01")
        advanceUntilIdle()

        val s = viewModel.uiState.value
        assertTrue(s.history.isEmpty())
        assertNotNull(s.historyError)
        assertNull(s.error)
        assertEquals(dealPositionLabel, s.dealPosition)
    }
}
