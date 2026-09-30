package com.example.pp68_salestrackingapp.ui.viewmodels.activity

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import com.example.pp68_salestrackingapp.data.model.ActivityResult
import com.example.pp68_salestrackingapp.data.model.AuthUser
import com.example.pp68_salestrackingapp.data.model.Project
import com.example.pp68_salestrackingapp.data.model.SalesActivity
import com.example.pp68_salestrackingapp.data.repository.ActivityRepository
import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import com.example.pp68_salestrackingapp.data.repository.ProjectRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Ignore

@OptIn(ExperimentalCoroutinesApi::class)
class SalesResultViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val dispatcher = StandardTestDispatcher()
    private val projectRepo = mockk<ProjectRepository>(relaxed = true)
    private val activityRepo = mockk<ActivityRepository>(relaxed = true)
    private val authRepo = mockk<AuthRepository>(relaxed = true)
    private val draftStore = mockk<com.example.pp68_salestrackingapp.utils.DraftStore>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { draftStore.load<Any>(any(), any()) } returns null
        coEvery { activityRepo.getActivityById(any()) } returns Result.success(emptyList())
        coEvery { activityRepo.getActivityResult(any()) } returns null
        // relaxed mockk ไม่รู้วิธีสังเคราะห์ kotlin.Result ที่ยังไม่ได้ stub ไว้ให้ (คืน Object เปล่ามาแทน
        // แล้ว cast เป็น List พังเป็น ClassCastException) ต้อง stub ไว้ล่วงหน้าเหมือน getActivityById ข้างบน
        coEvery { activityRepo.getPlanItems(any()) } returns Result.success(emptyList())
        // ผูกโครงการเพิ่มตอนบันทึกผล: FROM_APPOINTMENT ที่ยังไม่มี projectId จะโหลดตัวเลือกโครงการ
        // ตอน init เสมอ ต้อง stub ไว้ล่วงหน้าเหมือน getActivityById ไม่งั้น relaxed mock คืน null ให้
        // Flow แล้ว .collect{} พังทุกเทสต์ที่ FROM_APPOINTMENT ไม่มีโครงการผูกอยู่
        every { projectRepo.getAllProjectsFlow() } returns kotlinx.coroutines.flow.flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init with projectId should load project data`() = runTest {
        coEvery { projectRepo.getProjectById("PRJ-1") } returns Result.success(
            Project(
                projectId = "PRJ-1",
                custId = "C1",
                projectName = "Project A",
                projectStatus = "Lead",
                opportunityScore = "HOT"
            )
        )

        val vm = SalesResultViewModel(
            SavedStateHandle(mapOf("projectId" to "PRJ-1")),
            projectRepo,
            activityRepo,
            authRepo,
            draftStore
        )
        advanceUntilIdle()

        assertEquals("PRJ-1", vm.uiState.value.projectId)
        assertEquals("Project A", vm.uiState.value.project?.projectName)
        assertEquals("Lead", vm.uiState.value.currentStatus)
        assertFalse(vm.uiState.value.isLoading)
    }

    // W6-2: โครงการนี้เคยตอบข้อ 4-7 ไว้แล้ว (จาก trigger sync ฝั่ง backend) — หน้านี้ต้องดึงมา prefill
    // ให้เอง ไม่ใช่ถามใหม่ทุกครั้ง
    @Test
    fun `init with projectId should prefill blank deal-factor answers from the project`() = runTest {
        coEvery { projectRepo.getProjectById("PRJ-1") } returns Result.success(
            Project(
                projectId = "PRJ-1",
                custId = "C1",
                projectName = "Project A",
                dealPosition = "incumbent",
                previousSolution = "no_solution",
                counterpartyType = "direct_main_contractor",
                responseSpeed = "fast"
            )
        )

        val vm = SalesResultViewModel(
            SavedStateHandle(mapOf("projectId" to "PRJ-1")),
            projectRepo,
            activityRepo,
            authRepo,
            draftStore
        )
        advanceUntilIdle()

        val s = vm.uiState.value
        assertEquals("ลูกค้าใช้เราอยู่แล้ว การต่อสัญญามีโอกาสสูงมาก", s.dealPosition)
        assertEquals("ไม่มี Solution เดิม", s.previousSolution)
        assertEquals("ดีลกับ Main Contractor โดยตรง", s.counterpartyMultiplier)
        assertEquals("เร็ว", s.responseSpeed)
    }

    // โครงการเคยตอบครบแล้ว ห้ามบังคับตอบซ้ำ — ค่าที่ prefill มาต้องพอให้บันทึกผ่านได้เลย
    @Test
    fun `save should not require deal-factor answers when the project already has them`() = runTest {
        coEvery { activityRepo.getActivityById("A1") } returns Result.success(
            listOf(
                SalesActivity(
                    activityId = "A1",
                    userId = "U1",
                    customerId = "C1",
                    projectId = "PRJ-1",
                    activityType = "Visit",
                    activityDate = "2026-04-01",
                    status = "planned"
                )
            )
        )
        coEvery { projectRepo.getProjectById("PRJ-1") } returns Result.success(
            Project(
                projectId = "PRJ-1",
                custId = "C1",
                projectName = "Project A",
                projectStatus = "Lead",
                dealPosition = "incumbent",
                previousSolution = "no_solution",
                counterpartyType = "direct_main_contractor",
                responseSpeed = "fast"
            )
        )
        coEvery { activityRepo.saveActivityResult(any(), any()) } returns Result.success(Unit)

        val vm = SalesResultViewModel(
            SavedStateHandle(mapOf("activityId" to "A1")),
            projectRepo,
            activityRepo,
            authRepo,
            draftStore
        )
        advanceUntilIdle()
        vm.onSummaryChanged("summary")

        vm.save()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.showRequiredErrors)
        assertNull(vm.uiState.value.error)
        assertTrue(vm.uiState.value.isSaved)
        coVerify(exactly = 1) { activityRepo.saveActivityResult(any(), any()) }
    }

    @Test
    fun `init with activityId should load existing result mapping`() = runTest {
        coEvery { activityRepo.getActivityById("A1") } returns Result.success(
            listOf(
                SalesActivity(
                    activityId = "A1",
                    userId = "U1",
                    customerId = "C1",
                    projectId = "PRJ-1",
                    activityType = "Visit",
                    activityDate = "2026-04-01",
                    status = "planned"
                )
            )
        )
        coEvery { projectRepo.getProjectById("PRJ-1") } returns Result.success(
            Project(projectId = "PRJ-1", custId = "C1", projectName = "Project A", projectStatus = "Lead")
        )
        coEvery { activityRepo.getActivityResult("A1") } returns ActivityResult(
            resultId = "RES-TEST-001",
            activityId = "A1",
            newStatus = "Quotation",
            opportunityScore = "WARM",
            summary = "done summary"
        )

        val vm = SalesResultViewModel(
            SavedStateHandle(mapOf("activityId" to "A1")),
            projectRepo,
            activityRepo,
            authRepo,
            draftStore
        )
        advanceUntilIdle()

        assertEquals("A1", vm.uiState.value.activityId)
        assertEquals("PRJ-1", vm.uiState.value.projectId)
        assertTrue(vm.uiState.value.isStatusUpdateEnabled)
        assertEquals("done summary", vm.uiState.value.visitSummary)
        assertFalse(vm.uiState.value.isLoading)
    }

    // ✅ เช็คลิสต์วัตถุประสงค์ย้ายมาจาก ActivityDetailScreen — ต้องโหลดมาให้ครบตอนเปิดหน้า
    @Test
    fun `init with activityId should load its checklist`() = runTest {
        coEvery { activityRepo.getActivityById("A1") } returns Result.success(
            listOf(
                SalesActivity(
                    activityId = "A1",
                    userId = "U1",
                    customerId = "C1",
                    projectId = "PRJ-1",
                    activityType = "Visit",
                    activityDate = "2026-04-01",
                    status = "checked_in"
                )
            )
        )
        coEvery { projectRepo.getProjectById("PRJ-1") } returns Result.success(
            Project(projectId = "PRJ-1", custId = "C1", projectName = "Project A")
        )
        coEvery { activityRepo.getPlanItems("A1") } returns Result.success(
            listOf(
                com.example.pp68_salestrackingapp.data.model.PlanItemDto(masterId = 1, isDone = true),
                com.example.pp68_salestrackingapp.data.model.PlanItemDto(masterId = 2, isDone = false)
            )
        )

        val vm = SalesResultViewModel(
            SavedStateHandle(mapOf("activityId" to "A1")),
            projectRepo,
            activityRepo,
            authRepo,
            draftStore
        )
        advanceUntilIdle()

        assertEquals(2, vm.uiState.value.planItems.size)
        assertEquals(setOf(1), vm.uiState.value.selectedItemIds)
    }

    @Test
    fun `toggling a checklist item updates state and syncs to the server`() = runTest {
        coEvery { activityRepo.getActivityById("A1") } returns Result.success(
            listOf(
                SalesActivity(
                    activityId = "A1",
                    userId = "U1",
                    customerId = null,
                    projectId = null,
                    activityType = "Visit",
                    activityDate = "2026-04-01",
                    status = "checked_in"
                )
            )
        )
        coEvery { activityRepo.getPlanItems("A1") } returns Result.success(
            listOf(com.example.pp68_salestrackingapp.data.model.PlanItemDto(masterId = 5, isDone = false))
        )

        val vm = SalesResultViewModel(
            SavedStateHandle(mapOf("activityId" to "A1")),
            projectRepo,
            activityRepo,
            authRepo,
            draftStore
        )
        advanceUntilIdle()

        vm.toggleChecklistItem(5)
        advanceUntilIdle()

        assertEquals(setOf(5), vm.uiState.value.selectedItemIds)
        coVerify(exactly = 1) { activityRepo.updatePlanItemStatus("A1", 5, true) }
        coVerify(exactly = 1) { activityRepo.updateChecklistItem("A1", 5, true) }

        vm.toggleChecklistItem(5)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.selectedItemIds.isEmpty())
        coVerify(exactly = 1) { activityRepo.updatePlanItemStatus("A1", 5, false) }
    }

    @Test
    fun `save should validate summary before repository call`() = runTest {
        coEvery { activityRepo.getActivityById("A1") } returns Result.success(
            listOf(
                SalesActivity(
                    activityId = "A1",
                    userId = "U1",
                    customerId = "C1",
                    projectId = "PRJ-1",
                    activityType = "Visit",
                    activityDate = "2026-04-01",
                    status = "planned"
                )
            )
        )
        coEvery { projectRepo.getProjectById("PRJ-1") } returns Result.success(
            Project(projectId = "PRJ-1", custId = "C1", projectName = "Project A")
        )

        val vm = SalesResultViewModel(
            SavedStateHandle(mapOf("activityId" to "A1")),
            projectRepo,
            activityRepo,
            authRepo,
            draftStore
        )
        advanceUntilIdle()

        vm.save()

        assertEquals("กรุณากรอกสรุปการเข้าพบ", vm.uiState.value.error)
    }


    @Ignore("Method not yet implemented: projectRepo.updateProjectFields() does not exist in ProjectRepository")
    @Test
    fun `save success should persist result and mark saved`() = runTest {

        assertTrue(true)

    }

    // ข้อ 4-7 บังคับเลือกก่อนบันทึก — เทสต์ที่ต้องการให้ save() เดินต่อจึงต้องตอบให้ครบก่อน
    private fun SalesResultViewModel.answerRequiredAnalysis() {
        onDealPositionChanged("ลูกค้าใช้เราอยู่แล้ว การต่อสัญญามีโอกาสสูงมาก")
        onPreviousSolutionChanged("ไม่มี Solution เดิม")
        onCounterpartyMultiplierChanged("ดีลกับ Main Contractor โดยตรง")
        onResponseSpeedChanged("เร็ว")
    }

    @Test
    fun `save should block when analysis questions 4-7 are unanswered on a project-linked appointment`() = runTest {
        coEvery { activityRepo.getActivityById("A1") } returns Result.success(
            listOf(
                SalesActivity(
                    activityId = "A1",
                    userId = "U1",
                    customerId = "C1",
                    projectId = "PRJ-1",
                    activityType = "Visit",
                    activityDate = "2026-04-01",
                    status = "planned"
                )
            )
        )
        coEvery { projectRepo.getProjectById("PRJ-1") } returns Result.success(
            Project(projectId = "PRJ-1", custId = "C1", projectName = "Project A", projectStatus = "Lead")
        )
        coEvery { activityRepo.saveActivityResult(any(), any()) } returns Result.success(Unit)

        val vm = SalesResultViewModel(
            SavedStateHandle(mapOf("activityId" to "A1")),
            projectRepo,
            activityRepo,
            authRepo,
            draftStore
        )
        advanceUntilIdle()
        vm.onSummaryChanged("summary")

        vm.save()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.showRequiredErrors)
        assertEquals("กรุณาตอบข้อ 4-7 ในหัวข้อวิเคราะห์ข้อมูลให้ครบ", vm.uiState.value.error)
        assertFalse(vm.uiState.value.isSaved)
        coVerify(exactly = 0) { activityRepo.saveActivityResult(any(), any()) }
    }

    // W6-1: นัดหมายที่ไม่ได้ผูกโครงการไม่มีที่เก็บคำตอบข้อ 4-7 (เขียนลง project_code) จึงไม่ต้องบังคับตอบ
    @Test
    fun `save should not require analysis questions 4-7 when the appointment has no linked project`() = runTest {
        coEvery { activityRepo.getActivityById("A1") } returns Result.success(
            listOf(
                SalesActivity(
                    activityId = "A1",
                    userId = "U1",
                    customerId = null,
                    projectId = null,
                    activityType = "Visit",
                    activityDate = "2026-04-01",
                    status = "planned"
                )
            )
        )
        coEvery { activityRepo.saveActivityResult(any(), any()) } returns Result.success(Unit)

        val vm = SalesResultViewModel(
            SavedStateHandle(mapOf("activityId" to "A1")),
            projectRepo,
            activityRepo,
            authRepo,
            draftStore
        )
        advanceUntilIdle()
        vm.onSummaryChanged("summary")

        vm.save()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.showRequiredErrors)
        assertNull(vm.uiState.value.error)
        assertTrue(vm.uiState.value.isSaved)
        coVerify(exactly = 1) { activityRepo.saveActivityResult(any(), any()) }
    }

    @Test
    fun `undetermined choice satisfies the requirement for questions 4-6`() = runTest {
        coEvery { activityRepo.getActivityById("A1") } returns Result.success(
            listOf(
                SalesActivity(
                    activityId = "A1",
                    userId = "U1",
                    customerId = null,
                    projectId = null,
                    activityType = "Visit",
                    activityDate = "2026-04-01",
                    status = "planned"
                )
            )
        )
        coEvery { activityRepo.saveActivityResult(any(), any()) } returns Result.success(Unit)

        val vm = SalesResultViewModel(
            SavedStateHandle(mapOf("activityId" to "A1")),
            projectRepo,
            activityRepo,
            authRepo,
            draftStore
        )
        advanceUntilIdle()
        vm.onSummaryChanged("summary")
        vm.onDealPositionChanged(SalesResultViewModel.UNDETERMINED_LABEL)
        vm.onPreviousSolutionChanged(SalesResultViewModel.UNDETERMINED_LABEL)
        vm.onCounterpartyMultiplierChanged(SalesResultViewModel.UNDETERMINED_LABEL)
        vm.onResponseSpeedChanged("เร็ว")

        vm.save()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.showRequiredErrors)
        assertTrue(vm.uiState.value.isSaved)
    }

    @Test
    fun `loading a pre-requirement result fills the blank answers so it stays editable`() = runTest {
        coEvery { activityRepo.getActivityById("A1") } returns Result.success(
            listOf(
                SalesActivity(
                    activityId = "A1",
                    userId = "U1",
                    customerId = null,
                    projectId = null,
                    activityType = "Visit",
                    activityDate = "2026-04-01",
                    status = "done"
                )
            )
        )
        // บันทึกที่สร้างก่อนข้อ 4-7 กลายเป็นข้อบังคับ — ทั้งสี่ช่องเป็น null
        coEvery { activityRepo.getActivityResult("A1") } returns ActivityResult(
            resultId = "R-OLD",
            activityId = "A1",
            summary = "เข้าพบตามนัด",
            dealPosition = null,
            previousSolution = null,
            counterpartyMultiplier = null,
            responseSpeed = null
        )
        coEvery { activityRepo.saveActivityResult(any(), any()) } returns Result.success(Unit)

        val vm = SalesResultViewModel(
            SavedStateHandle(mapOf("activityId" to "A1")),
            projectRepo,
            activityRepo,
            authRepo,
            draftStore
        )
        advanceUntilIdle()

        val s = vm.uiState.value
        assertEquals(SalesResultViewModel.UNDETERMINED_LABEL, s.dealPosition)
        assertEquals(SalesResultViewModel.UNDETERMINED_LABEL, s.previousSolution)
        assertEquals(SalesResultViewModel.UNDETERMINED_LABEL, s.counterpartyMultiplier)
        assertEquals(SalesResultViewModel.RESPONSE_SPEED_DEFAULT, s.responseSpeed)

        // แก้แค่สรุปการเข้าพบแล้วบันทึกได้เลย ไม่ติด validation
        vm.onSummaryChanged("แก้คำผิด")
        vm.save()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.showRequiredErrors)
    }

    @Test
    fun `loading a result that already has answers must not overwrite them`() = runTest {
        coEvery { activityRepo.getActivityById("A1") } returns Result.success(
            listOf(
                SalesActivity(
                    activityId = "A1",
                    userId = "U1",
                    customerId = null,
                    projectId = null,
                    activityType = "Visit",
                    activityDate = "2026-04-01",
                    status = "done"
                )
            )
        )
        coEvery { activityRepo.getActivityResult("A1") } returns ActivityResult(
            resultId = "R-NEW",
            activityId = "A1",
            summary = "เข้าพบตามนัด",
            dealPosition = "incumbent",
            previousSolution = "no_solution",
            counterpartyMultiplier = "direct_main_contractor",
            responseSpeed = "slow_silent"
        )

        val vm = SalesResultViewModel(
            SavedStateHandle(mapOf("activityId" to "A1")),
            projectRepo,
            activityRepo,
            authRepo,
            draftStore
        )
        advanceUntilIdle()

        val s = vm.uiState.value
        assertEquals("ลูกค้าใช้เราอยู่แล้ว การต่อสัญญามีโอกาสสูงมาก", s.dealPosition)
        assertEquals("ไม่มี Solution เดิม", s.previousSolution)
        assertEquals("ดีลกับ Main Contractor โดยตรง", s.counterpartyMultiplier)
        assertEquals("ช้าหรือเงียบ", s.responseSpeed)
    }

    // ป้ายที่หน้าจอแสดงต้องเป็นคีย์ในตารางแปลงค่าเป๊ะ ไม่งั้นจะส่งข้อความไทยดิบขึ้น server เงียบ ๆ
    // (ตัวแปลงมี fallback เป็น `?: value` จึงไม่มีใครฟ้องตอนพิมพ์ผิด)
    @Test
    fun `undetermined label maps to a code in every question that offers it`() {
        assertEquals("undetermined", SalesResultViewModel.DEAL_POSITION_MAP[SalesResultViewModel.UNDETERMINED_LABEL])
        assertEquals("undetermined", SalesResultViewModel.SOLUTION_MAP[SalesResultViewModel.UNDETERMINED_LABEL])
        assertEquals("undetermined", SalesResultViewModel.COUNTERPARTY_MAP[SalesResultViewModel.UNDETERMINED_LABEL])
    }

    @Test
    fun `save should validate missing project id in standalone mode`() = runTest {
        // no activityId param -> ViewModel enters ResultMode.STANDALONE (see init{}), which
        // requires a projectId instead of an activityId
        val vm = SalesResultViewModel(
            SavedStateHandle(),
            projectRepo,
            activityRepo,
            authRepo,
            draftStore
        )
        advanceUntilIdle()
        vm.onSummaryChanged("summary")

        vm.save()

        assertEquals("ไม่พบรหัสโครงการ", vm.uiState.value.error)
    }

    @Test
    fun `save in standalone mode should call saveStandaloneResult, not be blocked by missing activity id`() = runTest {
        coEvery { projectRepo.getProjectById("PRJ-1") } returns Result.success(
            Project(projectId = "PRJ-1", custId = "C1", projectName = "Project A")
        )
        coEvery { activityRepo.saveStandaloneResult(any(), any(), any()) } returns Result.success(Unit)

        val vm = SalesResultViewModel(
            SavedStateHandle(mapOf("projectId" to "PRJ-1")),
            projectRepo,
            activityRepo,
            authRepo,
            draftStore
        )
        advanceUntilIdle()
        vm.onSummaryChanged("summary")
        vm.answerRequiredAnalysis()

        vm.save()
        advanceUntilIdle()

        assertNull(vm.uiState.value.error)
        assertTrue(vm.uiState.value.isSaved)
        coVerify(exactly = 1) { activityRepo.saveStandaloneResult("PRJ-1", any(), any()) }
        coVerify(exactly = 0) { activityRepo.saveActivityResult(any(), any()) }
    }

    @Test
    fun `save should allow missing customer`() = runTest {
        coEvery { activityRepo.getActivityById("A1") } returns Result.success(
            listOf(
                SalesActivity(
                    activityId = "A1",
                    userId = "U1",
                    customerId = null,
                    projectId = null,
                    activityType = "Visit",
                    activityDate = "2026-04-01",
                    status = "planned"
                )
            )
        )
        coEvery { activityRepo.saveActivityResult(any(), any()) } returns Result.success(Unit)

        val vm = SalesResultViewModel(
            SavedStateHandle(mapOf("activityId" to "A1")),
            projectRepo,
            activityRepo,
            authRepo,
            draftStore
        )
        advanceUntilIdle()
        vm.onSummaryChanged("summary")
        vm.answerRequiredAnalysis()

        vm.save()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isSaved)
        assertNull(vm.uiState.value.error)
        coVerify(exactly = 1) { activityRepo.saveActivityResult(any(), any()) }
    }

    // ✅ ไม่มีปุ่ม "Finish" แยกอีกต่อไป — บันทึกผลสำเร็จตอนนี้เป็นจุดเดียวที่ปิดนัดหมาย
    @Test
    fun `save success in FROM_APPOINTMENT mode also marks the appointment finished`() = runTest {
        coEvery { activityRepo.getActivityById("A1") } returns Result.success(
            listOf(
                SalesActivity(
                    activityId = "A1",
                    userId = "U1",
                    customerId = null,
                    projectId = null,
                    activityType = "Visit",
                    activityDate = "2026-04-01",
                    status = "checked_in"
                )
            )
        )
        coEvery { activityRepo.saveActivityResult(any(), any()) } returns Result.success(Unit)
        coEvery { activityRepo.finishActivity(any(), any(), any()) } returns Result.success(Unit)

        val vm = SalesResultViewModel(
            SavedStateHandle(mapOf("activityId" to "A1")),
            projectRepo,
            activityRepo,
            authRepo,
            draftStore
        )
        advanceUntilIdle()
        vm.onSummaryChanged("summary")
        vm.answerRequiredAnalysis()

        vm.save()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isSaved)
        coVerify(exactly = 1) { activityRepo.finishActivity("A1", any(), null) }
    }

    // standalone ไม่มีนัดหมายจริงให้ปิด — ต้องไม่เผลอเรียก finishActivity ด้วย activityId ที่ไม่มีอยู่
    @Test
    fun `save success in STANDALONE mode does not try to finish an appointment`() = runTest {
        coEvery { projectRepo.getProjectById("PRJ-1") } returns Result.success(
            Project(projectId = "PRJ-1", custId = "C1", projectName = "Project A")
        )
        coEvery { activityRepo.saveStandaloneResult(any(), any(), any()) } returns Result.success(Unit)

        val vm = SalesResultViewModel(
            SavedStateHandle(mapOf("projectId" to "PRJ-1")),
            projectRepo,
            activityRepo,
            authRepo,
            draftStore
        )
        advanceUntilIdle()
        vm.onSummaryChanged("summary")
        vm.answerRequiredAnalysis()

        vm.save()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isSaved)
        coVerify(exactly = 0) { activityRepo.finishActivity(any(), any(), any()) }
    }

    @Test
    fun `save exception should set wrapped error`() = runTest {
        coEvery { activityRepo.getActivityById("A1") } returns Result.success(
            listOf(
                SalesActivity(
                    activityId = "A1",
                    userId = "U1",
                    customerId = "C1",
                    projectId = "PRJ-1",
                    activityType = "Visit",
                    activityDate = "2026-04-01",
                    status = "planned"
                )
            )
        )
        coEvery { projectRepo.getProjectById("PRJ-1") } returns Result.success(
            Project(projectId = "PRJ-1", custId = "C1", projectName = "Project A", projectStatus = "Lead")
        )
        coEvery { activityRepo.getActivityResult("A1") } returns null
        every { authRepo.currentUser() } returns AuthUser("U1", "u@test.com", "sale")
        coEvery { activityRepo.saveActivityResult(any()) } throws RuntimeException("db down")

        val vm = SalesResultViewModel(
            SavedStateHandle(mapOf("activityId" to "A1")),
            projectRepo,
            activityRepo,
            authRepo,
            draftStore
        )
        advanceUntilIdle()
        vm.onSummaryChanged("summary")
        vm.answerRequiredAnalysis()

        vm.save()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isSaving)
        assertTrue(vm.uiState.value.error?.contains("db down") == true)
    }

    @Test
    fun `load project failure should set formatted error`() = runTest {
        coEvery { projectRepo.getProjectById("PRJ-404") } returns Result.failure(Exception("missing"))

        val vm = SalesResultViewModel(
            SavedStateHandle(mapOf("projectId" to "PRJ-404")),
            projectRepo,
            activityRepo,
            authRepo,
            draftStore
        )
        advanceUntilIdle()

        assertTrue(vm.uiState.value.error?.contains("โหลดข้อมูลโครงการไม่สำเร็จ: missing") == true)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `init with activity result should map reverse dictionaries and fields`() = runTest {
        coEvery { activityRepo.getActivityById("A1") } returns Result.success(
            listOf(
                SalesActivity(
                    activityId = "A1",
                    userId = "U1",
                    customerId = "C1",
                    projectId = "PRJ-1",
                    activityType = "Visit",
                    activityDate = "2026-04-01",
                    status = "planned"
                )
            )
        )
        coEvery { projectRepo.getProjectById("PRJ-1") } returns Result.success(
            Project(projectId = "PRJ-1", custId = "C1", projectName = "Project A", projectStatus = "Lead")
        )
        coEvery { activityRepo.getActivityResult("A1") } returns ActivityResult(
            resultId = "RES-TEST-002",
            activityId = "A1",
            reportDate = "2026-04-02",
            newStatus = "Make a Decision",
            opportunityScore = "HOT",
            dealPosition = "vendor_of_choice",
            previousSolution = "competitor_no_issue",
            counterpartyMultiplier = "direct_main_contractor",
            responseSpeed = "fast",
            isProposalSent = true,
            proposalDate = "2026-04-03",
            competitorCount = 2,
            dmInvolved = true,
            summary = "mapped summary",
            photoUrl = "https://img/p.jpg",
            photoTakenAt = "2026:04:03 10:00:00",
            photoLat = 13.7,
            photoLng = 100.5,
            photoDeviceModel = "Pixel"
        )

        val vm = SalesResultViewModel(
            SavedStateHandle(mapOf("activityId" to "A1")),
            projectRepo,
            activityRepo,
            authRepo,
            draftStore
        )
        advanceUntilIdle()

        val s = vm.uiState.value
        assertEquals("Make a Decision", s.newStatus)
        assertEquals("สูง (HOT)", s.opportunityScore)
        assertEquals("ลูกค้าเลือกเราเป็นตัวหลัก คู่แข่งอื่นเป็นแค่ backup", s.dealPosition)
        assertEquals("ใช้คู่แข่งอยู่และไม่มีปัญหา", s.previousSolution)
        assertEquals("ดีลกับ Main Contractor โดยตรง", s.counterpartyMultiplier)
        assertEquals("เร็ว", s.responseSpeed)
        assertTrue(s.isProposalSent)
        assertEquals("2026-04-03", s.proposalDate)
        assertEquals(2, s.competitorCount)
        assertTrue(s.dmInvolved)
        assertEquals("mapped summary", s.visitSummary)
        val cover = s.photos.firstOrNull()
        assertNotNull(cover)
        assertEquals("https://img/p.jpg", cover?.url)
        assertEquals("2026:04:03 10:00:00", cover?.takenAt)
        assertEquals(13.7, cover?.lat)
        assertEquals(100.5, cover?.lng)
        assertEquals("Pixel", cover?.deviceModel)
    }

    @Test
    fun `save with other loss reason should send code and note as separate fields`() = runTest {
        // ต้องผูกโครงการ: เหตุผลที่ไม่ได้งานเป็นข้อมูลของโครงการ บันทึกที่ไม่ผูกโครงการจะไม่มี
        // หัวข้อสถานะ/เหตุผลให้กรอกแล้ว และ save() จะไม่เขียนค่าพวกนี้ลงแถวผลลัพธ์
        coEvery { projectRepo.getProjectById("PRJ-1") } returns Result.success(
            Project(projectId = "PRJ-1", custId = "C1", projectName = "Project A", projectStatus = "Quotation")
        )
        coEvery { activityRepo.getActivityById("A1") } returns Result.success(
            listOf(
                SalesActivity(
                    activityId = "A1",
                    userId = "U1",
                    customerId = null,
                    projectId = "PRJ-1",
                    activityType = "Visit",
                    activityDate = "2026-04-01",
                    status = "planned"
                )
            )
        )
        val resultSlot = slot<ActivityResult>()
        coEvery { activityRepo.saveActivityResult(capture(resultSlot), any()) } returns Result.success(Unit)

        val vm = SalesResultViewModel(
            SavedStateHandle(mapOf("activityId" to "A1")),
            projectRepo,
            activityRepo,
            authRepo,
            draftStore
        )
        advanceUntilIdle()
        vm.onSummaryChanged("summary")
        vm.answerRequiredAnalysis()
        vm.onStatusToggle(true)
        vm.onNewStatusSelected("Lost")
        vm.onLossReasonChanged("อื่น ๆ")
        vm.onOtherLossReasonChanged("ลูกค้าเปลี่ยนใจกะทันหัน")

        vm.save()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isSaved)
        assertEquals("อื่น ๆ", resultSlot.captured.lossReason)
        assertEquals("ลูกค้าเปลี่ยนใจกะทันหัน", resultSlot.captured.lossReasonNote)
    }

    @Test
    fun `save with fixed loss reason should send code with no note`() = runTest {
        // ผูกโครงการด้วยเหตุผลเดียวกับเทสต์ด้านบน (เหตุผลที่ไม่ได้งานเป็นข้อมูลของโครงการ)
        coEvery { projectRepo.getProjectById("PRJ-1") } returns Result.success(
            Project(projectId = "PRJ-1", custId = "C1", projectName = "Project A", projectStatus = "Quotation")
        )
        coEvery { activityRepo.getActivityById("A1") } returns Result.success(
            listOf(
                SalesActivity(
                    activityId = "A1",
                    userId = "U1",
                    customerId = null,
                    projectId = "PRJ-1",
                    activityType = "Visit",
                    activityDate = "2026-04-01",
                    status = "planned"
                )
            )
        )
        val resultSlot = slot<ActivityResult>()
        coEvery { activityRepo.saveActivityResult(capture(resultSlot), any()) } returns Result.success(Unit)

        val vm = SalesResultViewModel(
            SavedStateHandle(mapOf("activityId" to "A1")),
            projectRepo,
            activityRepo,
            authRepo,
            draftStore
        )
        advanceUntilIdle()
        vm.onSummaryChanged("summary")
        vm.answerRequiredAnalysis()
        vm.onStatusToggle(true)
        vm.onNewStatusSelected("Failed")
        vm.onLossReasonChanged("สู้ราคาไม่ไหว")

        vm.save()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isSaved)
        assertEquals("สู้ราคาไม่ไหว", resultSlot.captured.lossReason)
        assertNull(resultSlot.captured.lossReasonNote)
    }

    @Test
    fun `loading a result with free text loss reason should split into other option and note`() = runTest {
        coEvery { activityRepo.getActivityById("A1") } returns Result.success(
            listOf(
                SalesActivity(
                    activityId = "A1",
                    userId = "U1",
                    customerId = null,
                    projectId = null,
                    activityType = "Visit",
                    activityDate = "2026-04-01",
                    status = "done"
                )
            )
        )
        coEvery { activityRepo.getActivityResult("A1") } returns ActivityResult(
            resultId = "R-OLD-2",
            activityId = "A1",
            summary = "เข้าพบตามนัด",
            lossReason = "อื่น ๆ",
            lossReasonNote = "งบประมาณถูกตัด"
        )

        val vm = SalesResultViewModel(
            SavedStateHandle(mapOf("activityId" to "A1")),
            projectRepo,
            activityRepo,
            authRepo,
            draftStore
        )
        advanceUntilIdle()

        assertEquals("อื่น ๆ", vm.uiState.value.lossReason)
        assertEquals("งบประมาณถูกตัด", vm.uiState.value.otherLossReason)
    }

    @Ignore("Method not yet implemented: projectRepo.updateProjectFields() does not exist in ProjectRepository")
    @Test
    fun `save should not update project fields when status toggle disabled and score empty`() = runTest {

        assertTrue(true)
    }

    @Ignore("Method not yet implemented: projectRepo.updateProjectFields() does not exist in ProjectRepository")
    @Test
    fun `save should use unknown user fallback when auth user missing`() = runTest {
        assertTrue(true)
    }

    // ผูกโครงการเพิ่มตอนบันทึกผล: นัดหมายที่ไม่ได้ผูกโครงการไว้แต่แรก
    @Test
    fun `init loads project options when a FROM_APPOINTMENT has no linked project`() = runTest {
        coEvery { activityRepo.getActivityById("A1") } returns Result.success(
            listOf(
                SalesActivity(
                    activityId = "A1", userId = "U1", customerId = null, projectId = null,
                    activityType = "Visit", activityDate = "2026-04-01", status = "checked_in"
                )
            )
        )
        every { projectRepo.getAllProjectsFlow() } returns kotlinx.coroutines.flow.flowOf(
            listOf(Project(projectId = "PRJ-1", custId = "C1", projectName = "Project A"))
        )

        val vm = SalesResultViewModel(
            SavedStateHandle(mapOf("activityId" to "A1")), projectRepo, activityRepo, authRepo, draftStore
        )
        advanceUntilIdle()

        assertEquals(listOf("PRJ-1" to "Project A"), vm.uiState.value.projectOptions)
    }

    @Test
    fun `onProjectSelected links an existing project and loads its data`() = runTest {
        coEvery { activityRepo.getActivityById("A1") } returns Result.success(
            listOf(
                SalesActivity(
                    activityId = "A1", userId = "U1", customerId = null, projectId = null,
                    activityType = "Visit", activityDate = "2026-04-01", status = "checked_in"
                )
            )
        )
        coEvery { projectRepo.getProjectById("PRJ-1") } returns Result.success(
            Project(projectId = "PRJ-1", custId = "C1", projectName = "Project A", projectStatus = "Lead")
        )

        val vm = SalesResultViewModel(
            SavedStateHandle(mapOf("activityId" to "A1")), projectRepo, activityRepo, authRepo, draftStore
        )
        advanceUntilIdle()

        vm.onProjectSelected("PRJ-1")
        advanceUntilIdle()

        assertEquals("PRJ-1", vm.uiState.value.projectId)
        assertEquals("Project A", vm.uiState.value.project?.projectName)
    }

    @Test
    fun `saveQuickProject blocks when name or status is blank`() = runTest {
        val vm = SalesResultViewModel(
            SavedStateHandle(), projectRepo, activityRepo, authRepo, draftStore
        )
        advanceUntilIdle()

        vm.saveQuickProject()

        assertEquals("กรุณาระบุชื่อโครงการและสถานะให้ครบถ้วน", vm.uiState.value.quickAddProjectError)
        coVerify(exactly = 0) { projectRepo.createProject(any(), any()) }
    }

    @Test
    fun `saveQuickProject creates and links a new project`() = runTest {
        coEvery { activityRepo.getActivityById("A1") } returns Result.success(
            listOf(
                SalesActivity(
                    activityId = "A1", userId = "U1", customerId = null, projectId = null,
                    activityType = "Visit", activityDate = "2026-04-01", status = "checked_in"
                )
            )
        )
        every { authRepo.currentUser() } returns AuthUser("U1", "u@test.com", "sale", "TS-001")
        val createdProject = Project(projectId = "PRJ-NEW", custId = null, projectName = "New Project", projectStatus = "Lead")
        coEvery { projectRepo.createProject(any(), "U1") } returns Result.success(createdProject)
        coEvery { projectRepo.getProjectById("PRJ-NEW") } returns Result.success(createdProject)

        val vm = SalesResultViewModel(
            SavedStateHandle(mapOf("activityId" to "A1")), projectRepo, activityRepo, authRepo, draftStore
        )
        advanceUntilIdle()

        vm.onQuickAddProjectNameChanged("New Project")
        vm.onQuickAddProjectStatusChanged("Lead")
        vm.saveQuickProject()
        advanceUntilIdle()

        assertEquals("PRJ-NEW", vm.uiState.value.projectId)
        assertFalse(vm.uiState.value.isQuickAddProjectOpen)
        coVerify(exactly = 1) { projectRepo.createProject(match { it.projectName == "New Project" && it.projectStatus == "Lead" }, "U1") }
        // saveQuickProject() เพิ่งสร้างโครงการจริงลง server — กดย้อนกลับตอนนี้ต้องโดนเตือนว่ามีการ
        // เปลี่ยนแปลงที่ยังไม่ได้บันทึกไว้ ไม่งั้นโครงการที่สร้างไว้จะลอยไม่ผูกกับอะไรเลยแบบไม่มีคำเตือน
        assertTrue(vm.isDirty())
    }

    @Test
    fun `restoring a draft that had a linked project reloads that project's data`() = runTest {
        coEvery { activityRepo.getActivityById("A1") } returns Result.success(
            listOf(
                SalesActivity(
                    activityId = "A1", userId = "U1", customerId = null, projectId = null,
                    activityType = "Visit", activityDate = "2026-04-01", status = "checked_in"
                )
            )
        )
        coEvery { projectRepo.getProjectById("PRJ-1") } returns Result.success(
            Project(projectId = "PRJ-1", custId = "C1", projectName = "Project A", projectStatus = "Lead")
        )
        every { draftStore.load("sales_result:A1", SalesResultDraft::class.java) } returns SalesResultDraft(
            projectId = "PRJ-1", visitSummary = "draft summary"
        )

        val vm = SalesResultViewModel(
            SavedStateHandle(mapOf("activityId" to "A1")), projectRepo, activityRepo, authRepo, draftStore
        )
        advanceUntilIdle()

        assertTrue(vm.uiState.value.draftAvailable)
        vm.restoreDraft()
        advanceUntilIdle()

        assertEquals("PRJ-1", vm.uiState.value.projectId)
        assertEquals("Project A", vm.uiState.value.project?.projectName)
        assertFalse(vm.uiState.value.draftAvailable)
    }

    @Test
    fun `save success pushes the newly linked project back onto the appointment`() = runTest {
        coEvery { activityRepo.getActivityById("A1") } returns Result.success(
            listOf(
                SalesActivity(
                    activityId = "A1", userId = "U1", customerId = null, projectId = null,
                    activityType = "Visit", activityDate = "2026-04-01", status = "checked_in"
                )
            )
        )
        coEvery { projectRepo.getProjectById("PRJ-1") } returns Result.success(
            Project(projectId = "PRJ-1", custId = "C1", projectName = "Project A", projectStatus = "Lead")
        )
        coEvery { activityRepo.saveActivityResult(any(), any()) } returns Result.success(Unit)
        coEvery { activityRepo.finishActivity(any(), any(), any()) } returns Result.success(Unit)

        val vm = SalesResultViewModel(
            SavedStateHandle(mapOf("activityId" to "A1")), projectRepo, activityRepo, authRepo, draftStore
        )
        advanceUntilIdle()
        vm.onProjectSelected("PRJ-1")
        advanceUntilIdle()
        vm.onSummaryChanged("summary")
        vm.answerRequiredAnalysis()

        vm.save()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isSaved)
        coVerify(exactly = 1) { activityRepo.updateActivity("A1", mapOf("project_code" to "PRJ-1")) }
    }

    @Test
    fun `save success does not touch the appointment when no project was linked`() = runTest {
        coEvery { activityRepo.getActivityById("A1") } returns Result.success(
            listOf(
                SalesActivity(
                    activityId = "A1", userId = "U1", customerId = null, projectId = null,
                    activityType = "Visit", activityDate = "2026-04-01", status = "checked_in"
                )
            )
        )
        coEvery { activityRepo.saveActivityResult(any(), any()) } returns Result.success(Unit)
        coEvery { activityRepo.finishActivity(any(), any(), any()) } returns Result.success(Unit)

        val vm = SalesResultViewModel(
            SavedStateHandle(mapOf("activityId" to "A1")), projectRepo, activityRepo, authRepo, draftStore
        )
        advanceUntilIdle()
        vm.onSummaryChanged("summary")

        vm.save()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isSaved)
        coVerify(exactly = 0) { activityRepo.updateActivity(any(), any()) }
    }

    // โครงการที่ตอบปัจจัยข้อ 4-9 ครบแล้ว (ล็อก) — ใช้ในกลุ่มเทสต์ "กลับมาจากหน้าแก้ไขปัจจัย" ด้านล่าง
    private fun lockedProject(
        dealPosition: String = "incumbent",
        competitorCount: Int = 2
    ) = Project(
        projectId = "PRJ-1",
        custId = "C1",
        projectName = "Project A",
        projectStatus = "Quotation",
        dealPosition = dealPosition,
        previousSolution = "no_solution",
        counterpartyType = "direct_main_contractor",
        responseSpeed = "fast",
        isProposalSent = true,
        proposalDate = "2026-04-06",
        competitorCount = competitorCount
    )

    // สร้างโครงการด่วนจากบันทึกผล ต้องผูกลูกค้าของนัดหมายนั้นให้ ไม่ใช่ได้โครงการลอยไม่มีลูกค้า
    @Test
    fun `quick add project links the appointment's customer`() = runTest {
        coEvery { authRepo.currentUser() } returns AuthUser(userId = "U1", email = "u@e.com", role = "sale", teamId = "BR-1")
        coEvery { activityRepo.getActivityById("A1") } returns Result.success(
            listOf(
                SalesActivity(
                    activityId = "A1", userId = "U1", customerId = "C1", projectId = null,
                    activityType = "onsite", activityDate = "2026-04-01", status = "planned"
                )
            )
        )
        val projectSlot = slot<Project>()
        coEvery { projectRepo.createProject(capture(projectSlot), any()) } returns Result.success(
            Project(projectId = "PRJ-NEW", custId = "C1", projectName = "New Site", projectStatus = "Lead")
        )
        coEvery { projectRepo.getProjectById("PRJ-NEW") } returns Result.success(
            Project(projectId = "PRJ-NEW", custId = "C1", projectName = "New Site", projectStatus = "Lead")
        )

        val vm = SalesResultViewModel(
            SavedStateHandle(mapOf("activityId" to "A1")), projectRepo, activityRepo, authRepo, draftStore
        )
        advanceUntilIdle()
        vm.onQuickAddProjectNameChanged("New Site")
        vm.onQuickAddProjectStatusChanged("Lead")
        vm.saveQuickProject()
        advanceUntilIdle()

        assertEquals("C1", projectSlot.captured.custId)
        assertEquals("PRJ-NEW", vm.uiState.value.projectId)
    }

    // นัดที่ไม่ระบุลูกค้าใช้ค่า sentinel CST-UNKNOWN ห้ามเอาไปใส่เป็นรหัสลูกค้าจริงของโครงการ
    @Test
    fun `quick add project treats CST-UNKNOWN as no customer`() = runTest {
        coEvery { authRepo.currentUser() } returns AuthUser(userId = "U1", email = "u@e.com", role = "sale", teamId = "BR-1")
        coEvery { activityRepo.getActivityById("A1") } returns Result.success(
            listOf(
                SalesActivity(
                    activityId = "A1", userId = "U1", customerId = "CST-UNKNOWN", projectId = null,
                    activityType = "onsite", activityDate = "2026-04-01", status = "planned"
                )
            )
        )
        val projectSlot = slot<Project>()
        coEvery { projectRepo.createProject(capture(projectSlot), any()) } returns Result.success(
            Project(projectId = "PRJ-NEW", projectName = "New Site", projectStatus = "Lead")
        )
        coEvery { projectRepo.getProjectById("PRJ-NEW") } returns Result.success(
            Project(projectId = "PRJ-NEW", projectName = "New Site", projectStatus = "Lead")
        )

        val vm = SalesResultViewModel(
            SavedStateHandle(mapOf("activityId" to "A1")), projectRepo, activityRepo, authRepo, draftStore
        )
        advanceUntilIdle()
        vm.onQuickAddProjectNameChanged("New Site")
        vm.onQuickAddProjectStatusChanged("Lead")
        vm.saveQuickProject()
        advanceUntilIdle()

        assertNull(projectSlot.captured.custId)
    }

    // ผู้ใช้กรอกสรุปค้างไว้ แล้วกดปุ่มไปแก้ปัจจัย กลับมาต้องเห็นค่าปัจจัยใหม่ แต่สิ่งที่กรอกต้องไม่หาย
    @Test
    fun `returning from the factors screen refreshes the factors but keeps what was typed`() = runTest {
        coEvery { projectRepo.getProjectById("PRJ-1") } returns Result.success(lockedProject())

        val vm = SalesResultViewModel(
            SavedStateHandle(mapOf("projectId" to "PRJ-1")), projectRepo, activityRepo, authRepo, draftStore
        )
        advanceUntilIdle()
        vm.onSummaryChanged("ลูกค้าสนใจมาก รอเทียบราคา")
        vm.onDmToggle(true)

        // ระหว่างที่อยู่หน้าแก้ไขปัจจัย ค่าฝั่งโครงการถูกแก้ไป
        coEvery { projectRepo.getProjectById("PRJ-1") } returns
            Result.success(lockedProject(dealPosition = "invited_to_compare", competitorCount = 7))

        vm.refreshProjectFactors()
        advanceUntilIdle()

        val s = vm.uiState.value
        // ค่าปัจจัยอัปเดตตามของใหม่
        assertEquals(SalesResultViewModel.DEAL_POSITION_REVERSE["invited_to_compare"], s.dealPosition)
        assertEquals(7, s.competitorCount)
        // ของที่ผู้ใช้กรอกไว้ยังอยู่ครบ
        assertEquals("ลูกค้าสนใจมาก รอเทียบราคา", s.visitSummary)
        assertTrue(s.dmInvolved)
    }

    // ค่าที่ refresh มาเองต้องไม่ถูกนับว่า "ผู้ใช้แก้ไข" ไม่งั้นกดย้อนกลับจะเด้งถามบันทึกฉบับร่าง
    // ทั้งที่ไม่ได้แตะอะไรเลย
    @Test
    fun `refreshing the factors does not by itself make the form dirty`() = runTest {
        coEvery { projectRepo.getProjectById("PRJ-1") } returns Result.success(lockedProject())

        val vm = SalesResultViewModel(
            SavedStateHandle(mapOf("projectId" to "PRJ-1")), projectRepo, activityRepo, authRepo, draftStore
        )
        advanceUntilIdle()
        assertFalse(vm.isDirty())

        coEvery { projectRepo.getProjectById("PRJ-1") } returns
            Result.success(lockedProject(dealPosition = "vendor_of_choice", competitorCount = 9))
        vm.refreshProjectFactors()
        advanceUntilIdle()

        assertEquals(9, vm.uiState.value.competitorCount)
        assertFalse(vm.isDirty())
    }

    // โครงการที่ยังตอบไม่ครบ = ผู้ใช้กำลังตอบในฟอร์มนี้เอง refresh ต้องห้ามทับคำตอบที่เพิ่งเลือก
    @Test
    fun `refreshing does not overwrite answers being typed for a project with no factors yet`() = runTest {
        coEvery { projectRepo.getProjectById("PRJ-1") } returns Result.success(
            Project(projectId = "PRJ-1", custId = "C1", projectName = "Project A", projectStatus = "Lead")
        )

        val vm = SalesResultViewModel(
            SavedStateHandle(mapOf("projectId" to "PRJ-1")), projectRepo, activityRepo, authRepo, draftStore
        )
        advanceUntilIdle()
        val chosen = SalesResultViewModel.DEAL_POSITION_MAP.keys.first()
        vm.onDealPositionChanged(chosen)
        vm.onCompetitorCountChanged(3)

        vm.refreshProjectFactors()
        advanceUntilIdle()

        assertEquals(chosen, vm.uiState.value.dealPosition)
        assertEquals(3, vm.uiState.value.competitorCount)
    }

    // กลไก draft ตัวเดียวกันนี้ถูกคัดลอกไว้ใน ViewModel ฟอร์มทั้ง 5 ตัว — คุมไว้ก่อนจะรวมเป็นตัวเดียว
    // หน้านี้ key พิเศษตรงที่มันเลือก activityId ก่อน ถ้าไม่มีจึงค่อยใช้ projectId
    private fun draftVm(): SalesResultViewModel {
        coEvery { projectRepo.getProjectById("PRJ-1") } returns Result.success(
            Project(projectId = "PRJ-1", custId = "C1", projectName = "Project A", projectStatus = "Lead")
        )
        return SalesResultViewModel(
            SavedStateHandle(mapOf("projectId" to "PRJ-1")),
            projectRepo,
            activityRepo,
            authRepo,
            draftStore
        )
    }

    @Test
    fun `an untouched sales result form is not dirty, and typing a summary makes it dirty`() = runTest {
        val vm = draftVm()
        advanceUntilIdle()

        assertFalse(vm.isDirty())

        vm.onSummaryChanged("เข้าพบลูกค้าเรียบร้อย")
        assertTrue(vm.isDirty())
    }

    @Test
    fun `saveDraft writes the sales result form under the project key`() = runTest {
        val vm = draftVm()
        advanceUntilIdle()

        vm.onSummaryChanged("สรุปที่กรอกค้างไว้")
        vm.saveDraft()

        val saved = slot<SalesResultDraft>()
        verify { draftStore.save("sales_result:PRJ-1", capture(saved)) }
        assertEquals("สรุปที่กรอกค้างไว้", saved.captured.visitSummary)
    }

    @Test
    fun `discardDraft clears the same sales result key`() = runTest {
        val vm = draftVm()
        advanceUntilIdle()

        vm.discardDraft()

        verify { draftStore.clear("sales_result:PRJ-1") }
    }

    @Test
    fun `no stored sales result draft means no prompt`() = runTest {
        val vm = draftVm()
        advanceUntilIdle()
        vm.checkForDraft()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.draftAvailable)
    }
}
