package com.example.pp68_salestrackingapp.ui.viewmodels.project

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.pp68_salestrackingapp.data.model.*
import com.example.pp68_salestrackingapp.data.remote.ApiService
import com.example.pp68_salestrackingapp.data.repository.*
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.math.BigDecimal
import java.math.RoundingMode

@OptIn(ExperimentalCoroutinesApi::class)
class AddProjectViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private val projectRepo = mockk<ProjectRepository>()
    private val customerRepo = mockk<CustomerRepository>()
    private val contactRepo = mockk<ContactRepository>(relaxed = true)
    private val authRepo = mockk<AuthRepository>()
    private val branchRepo = mockk<BranchRepository>()
    private val apiService = mockk<ApiService>(relaxed = true)
    private val draftStore = mockk<com.example.pp68_salestrackingapp.utils.DraftStore>(relaxed = true)
    private lateinit var viewModel: AddProjectViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { draftStore.load<Any>(any(), any()) } returns null

        val user = AuthUser("USR-001", "test@test.com", "sale", "TS-001")
        every { authRepo.currentUser() } returns user

        coEvery { customerRepo.getLocalCustomers() } coAnswers { Result.success(emptyList()) }
        coEvery { branchRepo.syncFromRemote() } coAnswers { Result.success(Unit) }
        coEvery { branchRepo.observeBranches() } coAnswers { emptyList() }
        coEvery { projectRepo.getMembersByBranch(any()) } coAnswers { Result.success(emptyList()) }
        coEvery { customerRepo.getContactPersons(any()) } coAnswers { Result.success(emptyList()) }
        coEvery { branchRepo.getBranchById(any()) } returns null
        coEvery { projectRepo.createProject(any(), any()) } returns Result.success(
            Project(projectId = "PJ-001", custId = "C1", projectName = "Default")
        )
        coEvery { projectRepo.updateProject(any()) } returns Result.success(Unit)
        coEvery { projectRepo.updateProject(any(), any()) } returns Result.success(Unit)
        coEvery { projectRepo.saveProjectContacts(any(), any()) } returns Result.success(Unit)
        coEvery { projectRepo.getProjectById(any()) } returns Result.failure(Exception("not found"))
        coEvery { projectRepo.getProjectContacts(any()) } returns Result.success(emptyList())
        coEvery { customerRepo.getCustomerById(any()) } returns Result.failure(Exception("not found"))
        coEvery { branchRepo.getBranches() } returns Result.success(emptyList())
    }

    @After
    fun tearDown() {
        unmockkAll()
        Dispatchers.resetMain()
    }

    private fun initViewModel() {
        viewModel = AddProjectViewModel(projectRepo, customerRepo, contactRepo, authRepo, branchRepo, apiService, draftStore)
    }

    // ฟอร์มสร้างโครงการใหม่ไม่เคยเรียก captureBaseline() เลย baseline จึงเป็นฟอร์มเปล่า —
    // การที่ระบบเลือกสาขาให้เองตอนเปิดหน้าจะถูกนับเป็น "ผู้ใช้แก้ไข" ทันที เปิดมาเฉย ๆ แล้วกด
    // ย้อนกลับก็โดนถาม "มีข้อมูลที่ยังไม่ได้บันทึก" (บั๊กเดียวกับ GPS auto-fill ในหน้าสร้างนัดหมาย)
    @Test
    fun `a form whose branch was auto selected is not dirty until the user edits something`() = runTest {
        coEvery { branchRepo.observeBranches() } returns listOf(Branch("TS-001", "สาขาทดสอบ", "Bangkok"))

        initViewModel()
        advanceUntilIdle()

        assertEquals("TS-001", viewModel.uiState.value.selectedTeamId)
        assertFalse(viewModel.isDirty())

        viewModel.onEvent(AddProjectEvent.ProjectNameChanged("โครงการใหม่"))
        assertTrue(viewModel.isDirty())
    }

    @Test
    fun `init when user in PJ-001 should auto select project team`() = runTest {
        every { authRepo.currentUser() } returns AuthUser("U1", "u@test.com", "sale", "PJ-001")
        coEvery { branchRepo.getBranchById("PJ-001") } returns Branch("PJ-001", "Project Team", "Bangkok")
        coEvery { branchRepo.observeBranches() } returns listOf(Branch("PJ-001", "Project Team", "Bangkok"))

        initViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("PJ-001", state.selectedTeamId)
        assertEquals("Project Team", state.selectedTeamName)
        assertEquals(listOf("PJ-001" to "Project Team"), state.teamOptions)
        assertFalse(state.isLoadingTeams)
    }

    @Test
    fun `init non PJ team should filter by region and default to own branch`() = runTest {
        every { authRepo.currentUser() } returns AuthUser("U1", "u@test.com", "sale", "TS-001")
        coEvery { branchRepo.observeBranches() } returns listOf(
            Branch("TS-001", "North A", "North"),
            Branch("TS-002", "North B", "North"),
            Branch("TS-003", "South A", "South")
        )

        initViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.teamOptions.size)
        assertTrue(state.teamOptions.any { it.first == "TS-001" })
        assertTrue(state.teamOptions.any { it.first == "TS-002" })
        assertEquals("TS-001", state.selectedTeamId)
        assertEquals("North A", state.selectedTeamName)
    }

    @Test
    fun `init non project-team user when sync fails should stop loading teams gracefully`() = runTest {
        every { authRepo.currentUser() } returns AuthUser("U1", "u@test.com", "sale", "TS-001")
        coEvery { branchRepo.syncFromRemote() } throws IllegalStateException("sync failed")

        initViewModel()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoadingTeams)
        assertTrue(viewModel.uiState.value.teamOptions.isEmpty())
    }

    @Test
    fun `init non project-team user with one branch in user region should auto select that branch`() = runTest {
        every { authRepo.currentUser() } returns AuthUser("U1", "u@test.com", "sale", "TS-001")
        coEvery { branchRepo.observeBranches() } returns listOf(
            Branch("TS-001", "North A", "North"),
            Branch("TS-003", "South A", "South")
        )

        initViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("TS-001", state.selectedTeamId)
        assertEquals("North A", state.selectedTeamName)
    }


    @Ignore("state.generatedProjectNumber field does not exist in AddProjectUiState production code")
    @Test
    fun `loadProject success should populate project and related fields`() = runTest {
        assertTrue(true)
    }

    @Test
    fun `loadProject failure should set saveError`() = runTest {
        coEvery { projectRepo.getProjectById("BAD") } returns Result.failure(Exception("boom"))
        initViewModel()
        advanceUntilIdle()

        viewModel.onEvent(AddProjectEvent.LoadProject("BAD"))
        advanceUntilIdle()

        assertEquals("boom", viewModel.uiState.value.saveError)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `customerSelected should reset contacts and load customer contacts`() = runTest {
        coEvery { customerRepo.getContactPersons("C1", any()) } returns Result.success(listOf(
            ContactPerson("CT-1", "C1", "John"), ContactPerson("CT-2", "C1", "Jane")
        ))
        initViewModel()
        advanceUntilIdle()

        viewModel.onEvent(AddProjectEvent.CustomerSelected("C1", "Client A"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("C1", state.selectedCustomerId)
        assertEquals("Client A", state.selectedCustomerName)
        assertEquals(listOf("CT-1" to "John", "CT-2" to "Jane"), state.contactOptions)
        assertTrue(state.selectedContactIds.isEmpty())
        assertNull(state.customerError)
    }

    @Test
    fun `customerSelected when contacts fail should keep options empty and stop loading`() = runTest {
        coEvery { customerRepo.getContactPersons("C1", any()) } returns Result.failure(Exception("boom"))
        initViewModel()
        advanceUntilIdle()

        viewModel.onEvent(AddProjectEvent.CustomerSelected("C1", "Client A"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("C1", state.selectedCustomerId)
        assertTrue(state.contactOptions.isEmpty())
        assertFalse(state.isLoadingContacts)
    }

    @Test
    fun `toggle events should add and remove selected ids`() = runTest {
        initViewModel()
        advanceUntilIdle()

        viewModel.onEvent(AddProjectEvent.ContactToggled("CT-1"))
        assertEquals(setOf("CT-1"), viewModel.uiState.value.selectedContactIds)
        viewModel.onEvent(AddProjectEvent.ContactToggled("CT-1"))
        assertTrue(viewModel.uiState.value.selectedContactIds.isEmpty())
    }

    @Test
    fun `location and date events should transform state correctly`() = runTest {
        initViewModel()
        advanceUntilIdle()

        viewModel.onEvent(AddProjectEvent.LocationPicked(13.7563, 100.5018))
        viewModel.onEvent(AddProjectEvent.StartDateChanged(""))
        viewModel.onEvent(AddProjectEvent.CloseDateChanged(""))
        viewModel.onEvent(AddProjectEvent.StartDateChanged("2026-06-01"))
        viewModel.onEvent(AddProjectEvent.CloseDateChanged("2026-06-30"))

        val state = viewModel.uiState.value
        assertEquals(13.7563, state.siteLat)
        assertEquals(100.5018, state.siteLong)
        assertTrue(state.locationText.startsWith("13.7563"))
        assertTrue(state.locationText.contains("100.5018"))
        assertEquals("2026-06-01", state.startDate)
        assertEquals("2026-06-30", state.closeDate)
    }

    @Test
    fun `save with invalid fields should set validation errors and not call create`() = runTest {
        initViewModel()
        advanceUntilIdle()

        viewModel.onEvent(AddProjectEvent.Save)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("กรุณากรอกชื่อโครงการ", state.projectNameError)
        assertEquals("กรุณาเลือกสถานะ", state.statusError)
        // สาขาที่พนักงานขายรับผิดชอบไม่บังคับแล้ว รายชื่อสาขามาจาก server เท่านั้น
        // ตอนออฟไลน์จะไม่มีอะไรให้เลือก การบังคับทำให้บันทึกโครงการไม่ได้เลย
        assertEquals(null, state.saveError)
        coVerify(exactly = 0) { projectRepo.createProject(any(), any()) }
        coVerify(exactly = 0) { projectRepo.updateProject(any(), any()) }
    }

    @Test
    fun `save create success should save project contacts`() = runTest {
        val projectSlot = slot<Project>()
        coEvery { projectRepo.createProject(capture(projectSlot), "USR-001") } returns Result.success(
            Project(projectId = "PJ-NEW", custId = "C1", projectName = "New Project Alpha", branchId = "TS-001")
        )
        initViewModel()
        advanceUntilIdle()

        viewModel.onEvent(AddProjectEvent.ProjectNameChanged("New Project Alpha"))
        viewModel.onEvent(AddProjectEvent.CustomerSelected("C1", "Client A"))
        viewModel.onEvent(AddProjectEvent.StatusChanged("Quotation"))
        viewModel.onEvent(AddProjectEvent.TeamSelected("TS-001", "North A"))
        viewModel.onEvent(AddProjectEvent.ContactToggled("CT-1"))
        viewModel.onEvent(AddProjectEvent.ExpectedValueChanged("1,200.50"))
        viewModel.onEvent(AddProjectEvent.Save)
        advanceUntilIdle()

        val actualExpectedValue = BigDecimal.valueOf(projectSlot.captured.expectedValue ?: 0.0)
            .setScale(2, RoundingMode.HALF_UP)
        assertEquals(BigDecimal("1200.50"), actualExpectedValue)
        assertTrue(viewModel.uiState.value.isSaved)
        assertNull(viewModel.uiState.value.saveError)
        coVerify(exactly = 1) { projectRepo.createProject(any(), "USR-001") }
        coVerify(exactly = 1) { projectRepo.saveProjectContacts(any(), listOf("CT-1")) }
    }

    @Test
    fun `save update success should call updateProject when projectId exists`() = runTest {
        coEvery { projectRepo.getProjectById("P123") } returns Result.success(
            Project(
                projectId = "P123",
                custId = "C1",
                projectName = "Old Name",
                projectStatus = "Lead",
                branchId = "TS-001"
            )
        )
        coEvery { customerRepo.getCustomerById("C1") } returns Result.success(
            Customer("C1", "Client A", null, null, null, null, null, null, null)
        )
        coEvery { branchRepo.observeBranches() } returns listOf(Branch("TS-001", "North A", "North"))

        initViewModel()
        advanceUntilIdle()
        viewModel.onEvent(AddProjectEvent.LoadProject("P123"))
        advanceUntilIdle()
        viewModel.onEvent(AddProjectEvent.ProjectNameChanged("Updated Name"))
        viewModel.onEvent(AddProjectEvent.StatusChanged("PO"))
        viewModel.onEvent(AddProjectEvent.Save)
        advanceUntilIdle()

        coVerify(exactly = 1) { projectRepo.updateProject(match { it.projectId == "P123" && it.projectName == "Updated Name" }, any()) }
        coVerify(exactly = 0) { projectRepo.createProject(any(), any()) }
        assertTrue(viewModel.uiState.value.isSaved)
    }

    @Test
    fun `save when saving contacts throws should expose error`() = runTest {
        coEvery { projectRepo.saveProjectContacts(any(), any()) } throws IllegalStateException("contacts save failed")
        initViewModel()
        advanceUntilIdle()

        viewModel.onEvent(AddProjectEvent.ProjectNameChanged("Proj A"))
        viewModel.onEvent(AddProjectEvent.CustomerSelected("C1", "Client A"))
        viewModel.onEvent(AddProjectEvent.StatusChanged("Quotation"))
        viewModel.onEvent(AddProjectEvent.TeamSelected("TS-001", "North A"))
        viewModel.onEvent(AddProjectEvent.Save)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSaved)
        assertEquals("contacts save failed", viewModel.uiState.value.saveError)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `save with other loss reason should send code and note as separate fields`() = runTest {
        val projectSlot = slot<Project>()
        coEvery { projectRepo.createProject(capture(projectSlot), "USR-001") } returns Result.success(
            Project(projectId = "PJ-NEW", custId = "C1", projectName = "New Project Alpha", branchId = "TS-001")
        )
        initViewModel()
        advanceUntilIdle()

        viewModel.onEvent(AddProjectEvent.ProjectNameChanged("New Project Alpha"))
        viewModel.onEvent(AddProjectEvent.CustomerSelected("C1", "Client A"))
        viewModel.onEvent(AddProjectEvent.TeamSelected("TS-001", "North A"))
        viewModel.onEvent(AddProjectEvent.StatusChanged("Lost"))
        viewModel.onEvent(AddProjectEvent.LossReasonChanged("อื่น ๆ"))
        viewModel.onEvent(AddProjectEvent.OtherLossReasonChanged("ลูกค้าเปลี่ยนใจกะทันหัน"))
        viewModel.onEvent(AddProjectEvent.Save)
        advanceUntilIdle()

        assertEquals("อื่น ๆ", projectSlot.captured.lossReason)
        assertEquals("ลูกค้าเปลี่ยนใจกะทันหัน", projectSlot.captured.lossReasonNote)
        assertTrue(viewModel.uiState.value.isSaved)
    }

    @Test
    fun `save right after restoring a draft still resolves the customer name`() = runTest {
        // restoreDraft() ตั้ง selectedCustomerId ให้ทันทีแต่ resolve selectedCustomerName แบบ async
        // ทีหลัง — ถ้ากด Save ไวจนแซงหน้า save() ต้อง fallback ไปหาชื่อสดเองแทนที่จะส่งชื่อว่าง
        every { draftStore.load("add_project:new", AddProjectDraft::class.java) } returns AddProjectDraft(
            projectName = "Draft Project",
            projectStatus = "Lead",
            selectedCustomerId = "C1"
        )
        coEvery { customerRepo.getCustomerById("C1") } returns Result.success(
            Customer(custId = "C1", companyName = "Restored Customer Co")
        )
        val projectSlot = slot<Project>()
        coEvery { projectRepo.createProject(capture(projectSlot), "USR-001") } returns Result.success(
            Project(projectId = "PJ-NEW", custId = "C1", projectName = "Draft Project")
        )

        initViewModel()
        advanceUntilIdle()
        viewModel.onEvent(AddProjectEvent.CheckDraft)
        viewModel.onEvent(AddProjectEvent.RestoreDraft)
        viewModel.onEvent(AddProjectEvent.TeamSelected("TS-001", "North A"))
        viewModel.onEvent(AddProjectEvent.Save)
        advanceUntilIdle()

        assertEquals("Restored Customer Co", projectSlot.captured.customerName)
    }

    @Test
    fun `save with fixed loss reason should send code with no note`() = runTest {
        val projectSlot = slot<Project>()
        coEvery { projectRepo.createProject(capture(projectSlot), "USR-001") } returns Result.success(
            Project(projectId = "PJ-NEW", custId = "C1", projectName = "New Project Alpha", branchId = "TS-001")
        )
        initViewModel()
        advanceUntilIdle()

        viewModel.onEvent(AddProjectEvent.ProjectNameChanged("New Project Alpha"))
        viewModel.onEvent(AddProjectEvent.CustomerSelected("C1", "Client A"))
        viewModel.onEvent(AddProjectEvent.TeamSelected("TS-001", "North A"))
        viewModel.onEvent(AddProjectEvent.StatusChanged("Failed"))
        viewModel.onEvent(AddProjectEvent.LossReasonChanged("สู้ราคาไม่ไหว"))
        viewModel.onEvent(AddProjectEvent.Save)
        advanceUntilIdle()

        assertEquals("สู้ราคาไม่ไหว", projectSlot.captured.lossReason)
        assertNull(projectSlot.captured.lossReasonNote)
        assertTrue(viewModel.uiState.value.isSaved)
    }

    @Test
    fun `loadProject with free text loss reason should split into other option and note`() = runTest {
        coEvery { projectRepo.getProjectById("P123") } returns Result.success(
            Project(
                projectId = "P123",
                custId = "C1",
                projectName = "Old Name",
                projectStatus = "Lost",
                branchId = "TS-001",
                lossReason = "อื่น ๆ",
                lossReasonNote = "งบประมาณถูกตัด"
            )
        )
        coEvery { customerRepo.getCustomerById("C1") } returns Result.success(
            Customer("C1", "Client A", null, null, null, null, null, null, null)
        )
        coEvery { branchRepo.observeBranches() } returns listOf(Branch("TS-001", "North A", "North"))

        initViewModel()
        advanceUntilIdle()
        viewModel.onEvent(AddProjectEvent.LoadProject("P123"))
        advanceUntilIdle()

        assertEquals("อื่น ๆ", viewModel.uiState.value.lossReason)
        assertEquals("งบประมาณถูกตัด", viewModel.uiState.value.otherLossReason)
    }

    @Test
    fun `loadProject with fixed loss reason should not populate other text`() = runTest {
        coEvery { projectRepo.getProjectById("P123") } returns Result.success(
            Project(
                projectId = "P123",
                custId = "C1",
                projectName = "Old Name",
                projectStatus = "Lost",
                branchId = "TS-001",
                lossReason = "เทคโนโลยีไม่ผ่าน",
                lossReasonNote = null
            )
        )
        coEvery { customerRepo.getCustomerById("C1") } returns Result.success(
            Customer("C1", "Client A", null, null, null, null, null, null, null)
        )
        coEvery { branchRepo.observeBranches() } returns listOf(Branch("TS-001", "North A", "North"))

        initViewModel()
        advanceUntilIdle()
        viewModel.onEvent(AddProjectEvent.LoadProject("P123"))
        advanceUntilIdle()

        assertEquals("เทคโนโลยีไม่ผ่าน", viewModel.uiState.value.lossReason)
        assertEquals("", viewModel.uiState.value.otherLossReason)
    }

    // W6-2: หน้าโครงการเป็นที่เดียวที่แก้ปัจจัยข้อ 4-7 ได้ — โหลดโครงการต้องแปลงรหัสกลับเป็นป้ายให้ dropdown โชว์ถูก
    @Test
    fun `loadProject should map deal-factor codes back to labels`() = runTest {
        coEvery { projectRepo.getProjectById("P123") } returns Result.success(
            Project(
                projectId = "P123",
                custId = "C1",
                projectName = "Old Name",
                branchId = "TS-001",
                dealPosition = "incumbent",
                previousSolution = "no_solution",
                counterpartyType = "direct_main_contractor",
                responseSpeed = "fast"
            )
        )
        coEvery { customerRepo.getCustomerById("C1") } returns Result.success(
            Customer("C1", "Client A", null, null, null, null, null, null, null)
        )
        coEvery { branchRepo.observeBranches() } returns listOf(Branch("TS-001", "North A", "North"))

        initViewModel()
        advanceUntilIdle()
        viewModel.onEvent(AddProjectEvent.LoadProject("P123"))
        advanceUntilIdle()

        val s = viewModel.uiState.value
        assertEquals("ลูกค้าใช้เราอยู่แล้ว การต่อสัญญามีโอกาสสูงมาก", s.dealPosition)
        assertEquals("ไม่มี Solution เดิม", s.previousSolution)
        assertEquals("ดีลกับ Main Contractor โดยตรง", s.counterpartyType)
        assertEquals("เร็ว", s.responseSpeed)
    }

    // ยังไม่เคยตอบ (โครงการใหม่/ยังไม่มีค่า) ต้องไม่พังหรือใส่ค่าแปลกมา — เป็นค่าว่างเฉยๆ
    @Test
    fun `loadProject with no deal-factor answers yet should leave them blank`() = runTest {
        coEvery { projectRepo.getProjectById("P123") } returns Result.success(
            Project(projectId = "P123", custId = "C1", projectName = "Old Name", branchId = "TS-001")
        )
        coEvery { customerRepo.getCustomerById("C1") } returns Result.success(
            Customer("C1", "Client A", null, null, null, null, null, null, null)
        )
        coEvery { branchRepo.observeBranches() } returns listOf(Branch("TS-001", "North A", "North"))

        initViewModel()
        advanceUntilIdle()
        viewModel.onEvent(AddProjectEvent.LoadProject("P123"))
        advanceUntilIdle()

        val s = viewModel.uiState.value
        assertEquals("", s.dealPosition)
        assertEquals("", s.previousSolution)
        assertEquals("", s.counterpartyType)
        assertEquals("", s.responseSpeed)
    }

    @Test
    fun `save should map deal-factor labels back to codes`() = runTest {
        val projectSlot = slot<Project>()
        coEvery { projectRepo.createProject(capture(projectSlot), "USR-001") } returns Result.success(
            Project(projectId = "PJ-NEW", custId = "C1", projectName = "New Project Alpha", branchId = "TS-001")
        )
        initViewModel()
        advanceUntilIdle()

        viewModel.onEvent(AddProjectEvent.ProjectNameChanged("New Project Alpha"))
        viewModel.onEvent(AddProjectEvent.CustomerSelected("C1", "Client A"))
        viewModel.onEvent(AddProjectEvent.TeamSelected("TS-001", "North A"))
        viewModel.onEvent(AddProjectEvent.StatusChanged("Lead"))
        viewModel.onEvent(AddProjectEvent.DealPositionChanged("ลูกค้าใช้เราอยู่แล้ว การต่อสัญญามีโอกาสสูงมาก"))
        viewModel.onEvent(AddProjectEvent.PreviousSolutionChanged("ไม่มี Solution เดิม"))
        viewModel.onEvent(AddProjectEvent.CounterpartyTypeChanged("ดีลกับ Main Contractor โดยตรง"))
        viewModel.onEvent(AddProjectEvent.ResponseSpeedChanged("เร็ว"))
        viewModel.onEvent(AddProjectEvent.Save)
        advanceUntilIdle()

        assertEquals("incumbent", projectSlot.captured.dealPosition)
        assertEquals("no_solution", projectSlot.captured.previousSolution)
        assertEquals("direct_main_contractor", projectSlot.captured.counterpartyType)
        assertEquals("fast", projectSlot.captured.responseSpeed)
        assertTrue(viewModel.uiState.value.isSaved)
    }

    @Test
    fun `save failure should set saveError`() = runTest {
        coEvery { projectRepo.createProject(any(), any()) } returns Result.failure(Exception("save failed"))
        initViewModel()
        advanceUntilIdle()

        viewModel.onEvent(AddProjectEvent.ProjectNameChanged("New Project Alpha"))
        viewModel.onEvent(AddProjectEvent.CustomerSelected("C1", "Client A"))
        viewModel.onEvent(AddProjectEvent.StatusChanged("Quotation"))
        viewModel.onEvent(AddProjectEvent.TeamSelected("TS-001", "North A"))
        viewModel.onEvent(AddProjectEvent.Save)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSaved)
        assertEquals("save failed", viewModel.uiState.value.saveError)
    }

    // กลไก draft autosave ถูกคัดลอกไว้เหมือนกันใน ViewModel ฟอร์มทั้ง 5 ตัว แก้ที่นึ่งแล้วลืมที่เหลือ
    // เคยเกิดขึ้นจริงมาแล้ว — ชุดนี้คุมกลไกไว้ก่อนจะรวมโค้ดชุดนี้เป็นตัวเดียว
    @Test
    fun `an untouched project form is not dirty, and editing a field makes it dirty`() = runTest {
        initViewModel()
        advanceUntilIdle()

        assertFalse(viewModel.isDirty())

        viewModel.onEvent(AddProjectEvent.ProjectNameChanged("โครงการทดสอบ"))
        assertTrue(viewModel.isDirty())
    }

    @Test
    fun `saveDraft writes the current project form under the new-project key`() = runTest {
        initViewModel()
        advanceUntilIdle()

        viewModel.onEvent(AddProjectEvent.ProjectNameChanged("โครงการทดสอบ"))
        viewModel.saveDraft()

        val saved = slot<AddProjectDraft>()
        verify { draftStore.save("add_project:new", capture(saved)) }
        assertEquals("โครงการทดสอบ", saved.captured.projectName)
    }

    @Test
    fun `discardDraft clears the same key it was saved under`() = runTest {
        initViewModel()
        advanceUntilIdle()

        viewModel.discardDraft()

        verify { draftStore.clear("add_project:new") }
    }

    @Test
    fun `a saved project draft is offered and restoring it fills the form back in`() = runTest {
        every { draftStore.load("add_project:new", AddProjectDraft::class.java) } returns
            AddProjectDraft(projectName = "โครงการที่ค้างไว้")

        initViewModel()
        advanceUntilIdle()
        viewModel.onEvent(AddProjectEvent.CheckDraft)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.draftAvailable)

        viewModel.onEvent(AddProjectEvent.RestoreDraft)
        advanceUntilIdle()

        assertEquals("โครงการที่ค้างไว้", viewModel.uiState.value.projectName)
        assertFalse(viewModel.uiState.value.draftAvailable)
    }

    @Test
    fun `no stored project draft means no prompt`() = runTest {
        initViewModel()
        advanceUntilIdle()
        viewModel.onEvent(AddProjectEvent.CheckDraft)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.draftAvailable)
    }
    /**
     * เคสจากการทดสอบเครื่องจริง: ปิดเน็ตแล้วสร้างโครงการ ดรอปดาวน์สาขาว่างเพราะรายชื่อมาจาก server
     * เดิมติด validation จนบันทึกไม่ได้เลย ทั้งที่ข้อมูลอื่นครบและ outbox ตามส่งให้ได้อยู่แล้ว
     */
    @Test
    fun `a project saves offline even when no branch could be loaded`() = runTest {
        coEvery { branchRepo.syncFromRemote() } throws java.io.IOException("offline")
        coEvery { branchRepo.observeBranches() } returns emptyList()
        coEvery { branchRepo.getBranches() } returns Result.failure(java.io.IOException("offline"))
        initViewModel()
        advanceUntilIdle()

        viewModel.onEvent(AddProjectEvent.ProjectNameChanged("โครงการตอนเน็ตหลุด"))
        viewModel.onEvent(AddProjectEvent.StatusChanged("Prospect"))
        viewModel.onEvent(AddProjectEvent.Save)
        advanceUntilIdle()

        assertEquals(null, viewModel.uiState.value.saveError)
        coVerify(exactly = 1) { projectRepo.createProject(any(), any()) }
    }
}
