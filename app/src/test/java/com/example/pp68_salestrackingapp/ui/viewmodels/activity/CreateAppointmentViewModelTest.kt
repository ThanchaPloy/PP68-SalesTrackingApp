package com.example.pp68_salestrackingapp.ui.viewmodels.activity

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.pp68_salestrackingapp.data.model.ActivityMaster
import com.example.pp68_salestrackingapp.data.model.AuthUser
import com.example.pp68_salestrackingapp.data.model.ContactPerson
import com.example.pp68_salestrackingapp.data.model.SalesActivity
import com.example.pp68_salestrackingapp.data.model.Project
import com.example.pp68_salestrackingapp.data.model.Customer
import com.example.pp68_salestrackingapp.data.repository.ActivityRepository
import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import com.example.pp68_salestrackingapp.data.repository.CustomerRepository
import com.example.pp68_salestrackingapp.data.repository.ProjectRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import com.example.pp68_salestrackingapp.data.repository.DraftSaveResult
import io.mockk.slot
import io.mockk.verify
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CreateAppointmentViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val dispatcher = StandardTestDispatcher()
    private val context = mockk<android.content.Context>(relaxed = true)
    private val activityRepo = mockk<ActivityRepository>(relaxed = true)
    private val projectRepo = mockk<ProjectRepository>(relaxed = true)
    private val customerRepo = mockk<CustomerRepository>(relaxed = true)
    private val authRepo = mockk<AuthRepository>(relaxed = true)
    private val contactRepo = mockk<com.example.pp68_salestrackingapp.data.repository.ContactRepository>(relaxed = true)
    private val draftStore = mockk<com.example.pp68_salestrackingapp.utils.DraftStore>(relaxed = true)
    private val serverTimeAnchor: com.example.pp68_salestrackingapp.utils.ServerTimeAnchor = mockk(relaxed = true)
    private val draftRepository: com.example.pp68_salestrackingapp.data.repository.AppointmentDraftRepository = mockk(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { draftStore.load<Any>(any(), any()) } returns null
        every { customerRepo.getAllContacts() } returns flowOf(emptyList())
        coEvery { customerRepo.getCustomers() } returns Result.success(emptyList())
        coEvery { customerRepo.getCustomerById(any()) } returns Result.success(Customer(custId = "C1", companyName = "Company A", companyStatus = 1))
        coEvery { customerRepo.getContactPersons(any(), null) } returns Result.success(emptyList())
        every { projectRepo.getAllProjectsFlow() } returns flowOf(emptyList())
        coEvery { projectRepo.getProjectById(any()) } returns Result.success(Project(projectId = "", custId = "", projectName = ""))
        coEvery { projectRepo.getProjectContacts(any()) } returns Result.success(emptyList())
        coEvery { activityRepo.getMasterActivities() } returns emptyList()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun configureBaseData() {
        every { projectRepo.getAllProjectsFlow() } returns flowOf(
            listOf(
                Project(
                    projectId = "PRJ-1",
                    custId = "C1",
                    projectName = "Project A",
                    projectStatus = "Lead"
                )
            )
        )
        coEvery { projectRepo.getProjectById("PRJ-1") } returns Result.success(
            Project(
                projectId = "PRJ-1",
                custId = "C1",
                projectName = "Project A",
                projectStatus = "Lead"
            )
        )
        coEvery { customerRepo.getContactPersons("C1", null) } returns Result.success(
            listOf(
                ContactPerson(
                    contactId = "CT-1",
                    custId = "C1",
                    fullName = "John",
                    isActive = true
                )
            )
        )
    }

    @Test
    fun `init should load project options and master objectives`() = runTest {
        configureBaseData()
        coEvery { activityRepo.getMasterActivities() } returns listOf(
            ActivityMaster(1, "Lead", "สำรวจความต้องการ")
        )

        val vm = CreateAppointmentViewModel(context, activityRepo, projectRepo, customerRepo, contactRepo, authRepo, draftStore, java.time.Clock.system(java.time.ZoneId.of("Asia/Bangkok")), serverTimeAnchor, draftRepository)
        advanceUntilIdle()

        assertEquals(1, vm.uiState.value.projectOptions.size)
        assertEquals("Project A", vm.uiState.value.projectOptions.first().name)
        assertEquals(1, vm.uiState.value.allMasterOptions.size)
        assertFalse(vm.uiState.value.isLoadingProjects)
        assertFalse(vm.uiState.value.isLoadingMasters)
    }

    // นัดหมายใหม่แบบ onsite (ค่าเริ่มต้น) ตั้งพิกัดให้อัตโนมัติจาก GPS หลังเปิดหน้าจอ — การ auto-fill
    // นี้ไม่ควรทำให้ isDirty() เป็น true ทั้งที่ผู้ใช้ยังไม่ได้แตะอะไรเลย (ไม่งั้นกดย้อนกลับจะโดนถาม
    // "มีข้อมูลยังไม่ได้บันทึก" ทุกครั้งที่เปิดนัดหมายใหม่)
    @Test
    fun `GPS auto-fill on a fresh onsite appointment does not mark the form dirty`() = runTest {
        configureBaseData()
        coEvery { activityRepo.getMasterActivities() } returns emptyList()

        val vm = CreateAppointmentViewModel(context, activityRepo, projectRepo, customerRepo, contactRepo, authRepo, draftStore, java.time.Clock.system(java.time.ZoneId.of("Asia/Bangkok")), serverTimeAnchor, draftRepository)
        advanceUntilIdle()
        vm.onEvent(CreateAppointmentEvent.CheckDraft)
        advanceUntilIdle()

        // จำลองการที่หน้าจอดึง GPS มาได้แล้วยิง event เดียวกับที่ผู้ใช้ปักหมุดเองใช้
        vm.onEvent(CreateAppointmentEvent.LocationPicked(13.7563, 100.5018))

        assertFalse(vm.isDirty())
    }

    // แต่ถ้าปักหมุดซ้ำอีกครั้ง (ผู้ใช้แก้ตำแหน่งเองจริงๆ) ต้องนับเป็นแก้ไขตามปกติ
    @Test
    fun `manually re-picking the location after the GPS auto-fill still marks the form dirty`() = runTest {
        configureBaseData()
        coEvery { activityRepo.getMasterActivities() } returns emptyList()

        val vm = CreateAppointmentViewModel(context, activityRepo, projectRepo, customerRepo, contactRepo, authRepo, draftStore, java.time.Clock.system(java.time.ZoneId.of("Asia/Bangkok")), serverTimeAnchor, draftRepository)
        advanceUntilIdle()
        vm.onEvent(CreateAppointmentEvent.CheckDraft)
        advanceUntilIdle()

        vm.onEvent(CreateAppointmentEvent.LocationPicked(13.7563, 100.5018)) // absorbed into baseline
        vm.onEvent(CreateAppointmentEvent.LocationPicked(14.0, 101.0))       // a real manual change

        assertTrue(vm.isDirty())
    }

    @Test
    fun `master load failure should fallback to default masters`() = runTest {
        configureBaseData()
        coEvery { activityRepo.getMasterActivities() } throws RuntimeException("network")

        val vm = CreateAppointmentViewModel(context, activityRepo, projectRepo, customerRepo, contactRepo, authRepo, draftStore, java.time.Clock.system(java.time.ZoneId.of("Asia/Bangkok")), serverTimeAnchor, draftRepository)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.allMasterOptions.isNotEmpty())
        assertTrue(vm.uiState.value.allMasterOptions.any { it.category == "Lead" })
    }

    @Test
    fun `ProjectSelected should set project and filter masters by status`() = runTest {
        configureBaseData()
        coEvery { activityRepo.getMasterActivities() } returns listOf(
            ActivityMaster(1, "Lead", "Lead item"),
            ActivityMaster(2, "Quotation", "Quotation item")
        )
        val vm = CreateAppointmentViewModel(context, activityRepo, projectRepo, customerRepo, contactRepo, authRepo, draftStore, java.time.Clock.system(java.time.ZoneId.of("Asia/Bangkok")), serverTimeAnchor, draftRepository)
        advanceUntilIdle()

        vm.onEvent(CreateAppointmentEvent.ProjectSelected("PRJ-1", "Project A", "Lead"))
        advanceUntilIdle()

        assertEquals("PRJ-1", vm.uiState.value.selectedProjectId)
        assertEquals("Project A", vm.uiState.value.selectedProjectName)
        assertEquals("C1", vm.uiState.value.selectedCustomerId)
        assertTrue(vm.uiState.value.masterOptions.all { it.category == "Lead" })
        assertEquals(1, vm.uiState.value.contactOptions.size)
    }


    @Test
    fun `save should fail when user is missing`() = runTest {
        configureBaseData()
        coEvery { activityRepo.getMasterActivities() } returns listOf(ActivityMaster(1, "Lead", "L"))
        every { authRepo.currentUser() } returns null
        val vm = CreateAppointmentViewModel(context, activityRepo, projectRepo, customerRepo, contactRepo, authRepo, draftStore, java.time.Clock.system(java.time.ZoneId.of("Asia/Bangkok")), serverTimeAnchor, draftRepository)
        advanceUntilIdle()
        vm.onEvent(CreateAppointmentEvent.LoadInitialProject("PRJ-1"))
        vm.onEvent(CreateAppointmentEvent.TypeChanged("onsite"))
        vm.onEvent(CreateAppointmentEvent.TitleChanged("Visit"))
        vm.onEvent(CreateAppointmentEvent.DateChanged("Apr 06, 2099"))
        vm.onEvent(CreateAppointmentEvent.StartTimeSelected("10:00 AM"))
        vm.onEvent(CreateAppointmentEvent.LocationPicked(13.7563, 100.5018))
        advanceUntilIdle()

        vm.onEvent(CreateAppointmentEvent.Save)
        advanceUntilIdle()

        assertEquals("ไม่พบข้อมูล User กรุณา Login ใหม่", vm.uiState.value.saveError)
    }

    // นัด onsite ที่ไม่มีพิกัด ทำให้การเช็คอินบันทึกว่า "ยืนยันตำแหน่งแล้ว ห่าง 0 เมตร"
    // ทั้งที่ไม่มีจุดให้เทียบ จึงต้องกันไว้ตั้งแต่ตอนสร้างนัด
    @Test
    fun `onsite appointment without a pinned location must not save`() = runTest {
        configureBaseData()
        coEvery { activityRepo.getMasterActivities() } returns listOf(ActivityMaster(1, "Lead", "L"))
        every { authRepo.currentUser() } returns AuthUser("U1", "u@test.com", "sale")
        val vm = CreateAppointmentViewModel(context, activityRepo, projectRepo, customerRepo, contactRepo, authRepo, draftStore, java.time.Clock.system(java.time.ZoneId.of("Asia/Bangkok")), serverTimeAnchor, draftRepository)
        advanceUntilIdle()
        vm.onEvent(CreateAppointmentEvent.LoadInitialProject("PRJ-1"))
        vm.onEvent(CreateAppointmentEvent.TypeChanged("onsite"))
        vm.onEvent(CreateAppointmentEvent.TitleChanged("Visit"))
        vm.onEvent(CreateAppointmentEvent.DateChanged("Apr 06, 2099"))
        vm.onEvent(CreateAppointmentEvent.StartTimeSelected("10:00 AM"))
        advanceUntilIdle()

        vm.onEvent(CreateAppointmentEvent.Save)
        advanceUntilIdle()

        assertEquals(
            "กรุณาปักหมุดตำแหน่งนัดหมาย (จำเป็นสำหรับนัดแบบ On-site)",
            vm.uiState.value.saveError
        )
        assertFalse(vm.uiState.value.isSaved)
        coVerify(exactly = 0) { activityRepo.addActivity(any()) }
    }

    // สร้างนัดหมายใหม่เลือกวันที่ผ่านมาแล้วต้องเซฟไม่ได้
    @Test
    fun `creating a new appointment with a past date must not save`() = runTest {
        configureBaseData()
        coEvery { activityRepo.getMasterActivities() } returns listOf(ActivityMaster(1, "Lead", "L"))
        every { authRepo.currentUser() } returns AuthUser("U1", "u@test.com", "sale")
        val vm = CreateAppointmentViewModel(context, activityRepo, projectRepo, customerRepo, contactRepo, authRepo, draftStore, java.time.Clock.system(java.time.ZoneId.of("Asia/Bangkok")), serverTimeAnchor, draftRepository)
        advanceUntilIdle()
        vm.onEvent(CreateAppointmentEvent.LoadInitialProject("PRJ-1"))
        vm.onEvent(CreateAppointmentEvent.TypeChanged("online"))
        vm.onEvent(CreateAppointmentEvent.TitleChanged("Visit"))
        vm.onEvent(CreateAppointmentEvent.DateChanged("Apr 06, 2026"))
        vm.onEvent(CreateAppointmentEvent.StartTimeSelected("10:00 AM"))
        advanceUntilIdle()

        vm.onEvent(CreateAppointmentEvent.Save)
        advanceUntilIdle()

        assertEquals("ไม่สามารถสร้างนัดหมายย้อนหลังได้", vm.uiState.value.saveError)
        assertFalse(vm.uiState.value.isSaved)
        coVerify(exactly = 0) { activityRepo.addActivity(any()) }
    }

    // online และ call ไม่มีการเช็คอินด้วยพิกัด จึงต้องไม่ถูกบังคับตาม
    @Test
    fun `online and call appointments still save without a location`() = runTest {
        configureBaseData()
        coEvery { activityRepo.getMasterActivities() } returns listOf(ActivityMaster(1, "Lead", "L"))
        every { authRepo.currentUser() } returns AuthUser("U1", "u@test.com", "sale")
        coEvery { activityRepo.addActivity(any()) } returns Result.success("ACT-001")
        coEvery { activityRepo.saveAppointmentContacts(any(), any()) } returns Unit
        coEvery { activityRepo.savePlanItems(any(), any()) } returns Unit

        listOf("online", "call").forEach { type ->
            val vm = CreateAppointmentViewModel(context, activityRepo, projectRepo, customerRepo, contactRepo, authRepo, draftStore, java.time.Clock.system(java.time.ZoneId.of("Asia/Bangkok")), serverTimeAnchor, draftRepository)
            advanceUntilIdle()
            vm.onEvent(CreateAppointmentEvent.LoadInitialProject("PRJ-1"))
            vm.onEvent(CreateAppointmentEvent.TypeChanged(type))
            vm.onEvent(CreateAppointmentEvent.TitleChanged("Call topic"))
            vm.onEvent(CreateAppointmentEvent.DateChanged("Apr 06, 2099"))
            vm.onEvent(CreateAppointmentEvent.StartTimeSelected("10:00 AM"))
            advanceUntilIdle()

            vm.onEvent(CreateAppointmentEvent.Save)
            advanceUntilIdle()

            assertNull("type=$type ไม่ควรติด validation", vm.uiState.value.saveError)
            assertTrue("type=$type ควรบันทึกได้", vm.uiState.value.isSaved)
        }
    }

    @Test
    fun `save create success should persist activity contacts and plan items`() = runTest {
        configureBaseData()
        coEvery { activityRepo.getMasterActivities() } returns listOf(ActivityMaster(1, "Lead", "Lead objective"))
        every { authRepo.currentUser() } returns AuthUser("U1", "u@test.com", "sale")
        coEvery { activityRepo.addActivity(any()) } returns Result.success("ACT-001")
        coEvery { activityRepo.saveAppointmentContacts(any(), any()) } returns Unit
        coEvery { activityRepo.savePlanItems(any(), any()) } returns Unit

        val vm = CreateAppointmentViewModel(context, activityRepo, projectRepo, customerRepo, contactRepo, authRepo, draftStore, java.time.Clock.system(java.time.ZoneId.of("Asia/Bangkok")), serverTimeAnchor, draftRepository)
        advanceUntilIdle()
        vm.onEvent(CreateAppointmentEvent.LoadInitialProject("PRJ-1"))
        vm.onEvent(CreateAppointmentEvent.TypeChanged("onsite"))
        vm.onEvent(CreateAppointmentEvent.TitleChanged("Visit topic"))
        vm.onEvent(CreateAppointmentEvent.DateChanged("Apr 06, 2099"))
        vm.onEvent(CreateAppointmentEvent.StartTimeSelected("09:00 AM"))
        vm.onEvent(CreateAppointmentEvent.EndTimeSelected("10:00 AM"))
        vm.onEvent(CreateAppointmentEvent.LocationPicked(13.7563, 100.5018))
        vm.onEvent(CreateAppointmentEvent.ContactToggled("CT-1"))
        vm.onEvent(CreateAppointmentEvent.MasterToggled(1))
        vm.onEvent(CreateAppointmentEvent.OtherToggled)
        vm.onEvent(CreateAppointmentEvent.OtherObjectiveTextChanged("Custom objective"))
        advanceUntilIdle()

        vm.onEvent(CreateAppointmentEvent.Save)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isSaved)
        assertFalse(vm.uiState.value.isLoading)
        coVerify(exactly = 1) { activityRepo.addActivity(any()) }
        coVerify(exactly = 1) { activityRepo.saveAppointmentContacts(any(), listOf("CT-1")) }
        coVerify(exactly = 1) { activityRepo.savePlanItems(any(), any()) }
    }

    @Test
    fun `picker events and toggles should update state deterministically`() = runTest {
        configureBaseData()
        coEvery { activityRepo.getMasterActivities() } returns listOf(ActivityMaster(1, "Lead", "L"))
        val vm = CreateAppointmentViewModel(context, activityRepo, projectRepo, customerRepo, contactRepo, authRepo, draftStore, java.time.Clock.system(java.time.ZoneId.of("Asia/Bangkok")), serverTimeAnchor, draftRepository)
        advanceUntilIdle()

        vm.onEvent(CreateAppointmentEvent.ShowStartTimePicker)
        assertTrue(vm.uiState.value.showStartTimePicker)
        vm.onEvent(CreateAppointmentEvent.ShowEndTimePicker)
        assertTrue(vm.uiState.value.showEndTimePicker)
        vm.onEvent(CreateAppointmentEvent.DismissTimePicker)
        assertFalse(vm.uiState.value.showStartTimePicker)
        assertFalse(vm.uiState.value.showEndTimePicker)

        vm.onEvent(CreateAppointmentEvent.ContactToggled("CT-1"))
        assertTrue(vm.uiState.value.selectedContactIds.contains("CT-1"))
        vm.onEvent(CreateAppointmentEvent.ContactToggled("CT-1"))
        assertTrue(vm.uiState.value.selectedContactIds.isEmpty())

        vm.onEvent(CreateAppointmentEvent.MasterToggled(1))
        assertTrue(vm.uiState.value.selectedMasterIds.contains(1))
        vm.onEvent(CreateAppointmentEvent.MasterToggled(1))
        assertTrue(vm.uiState.value.selectedMasterIds.isEmpty())

        vm.onEvent(CreateAppointmentEvent.OtherToggled)
        assertTrue(vm.uiState.value.isOtherSelected)
        vm.onEvent(CreateAppointmentEvent.OtherToggled)
        assertFalse(vm.uiState.value.isOtherSelected)
    }

    @Test
    fun `project selected with unknown status should keep all masters and clear contacts`() = runTest {
        configureBaseData()
        coEvery { projectRepo.getProjectById("PRJ-UNKNOWN") } returns Result.success(
            Project(
                projectId = "PRJ-UNKNOWN",
                custId = "C1",
                projectName = "Project A",
                projectStatus = "unknown_status"
            )
        )
        coEvery { activityRepo.getMasterActivities() } returns listOf(
            ActivityMaster(1, "Lead", "Lead item"),
            ActivityMaster(2, "Quotation", "Quotation item")
        )
        val vm = CreateAppointmentViewModel(context, activityRepo, projectRepo, customerRepo, contactRepo, authRepo, draftStore, java.time.Clock.system(java.time.ZoneId.of("Asia/Bangkok")), serverTimeAnchor, draftRepository)
        advanceUntilIdle()

        vm.onEvent(CreateAppointmentEvent.ContactToggled("CT-1"))
        vm.onEvent(CreateAppointmentEvent.ProjectSelected("PRJ-UNKNOWN", "Project A", "unknown_status"))
        advanceUntilIdle()

        assertTrue(vm.uiState.value.selectedContactIds.isEmpty())
        assertEquals(2, vm.uiState.value.masterOptions.size)
    }

    @Test
    fun `load initial project failure should keep project selection unchanged`() = runTest {
        configureBaseData()
        coEvery { activityRepo.getMasterActivities() } returns emptyList()
        coEvery { projectRepo.getProjectById("PRJ-X") } returns Result.failure(Exception("not found"))
        val vm = CreateAppointmentViewModel(context, activityRepo, projectRepo, customerRepo, contactRepo, authRepo, draftStore, java.time.Clock.system(java.time.ZoneId.of("Asia/Bangkok")), serverTimeAnchor, draftRepository)
        advanceUntilIdle()

        vm.onEvent(CreateAppointmentEvent.LoadInitialProject("PRJ-X"))
        advanceUntilIdle()

        assertNull(vm.uiState.value.selectedProjectId)
        assertNull(vm.uiState.value.selectedProjectName)
    }

    @Test
    fun `project selected should stop loading contacts when contact fetch fails`() = runTest {
        every { projectRepo.getAllProjectsFlow() } returns flowOf(
            listOf(Project(projectId = "PRJ-2", custId = "C2", projectName = "Project B", projectStatus = "Lead"))
        )
        coEvery { projectRepo.getProjectById("PRJ-2") } returns Result.success(
            Project(projectId = "PRJ-2", custId = "C2", projectName = "Project B", projectStatus = "Lead")
        )
        coEvery { customerRepo.getContactPersons("C2") } returns Result.failure(Exception("contact failed"))
        coEvery { activityRepo.getMasterActivities() } returns listOf(ActivityMaster(1, "Lead", "Lead item"))
        val vm = CreateAppointmentViewModel(context, activityRepo, projectRepo, customerRepo, contactRepo, authRepo, draftStore, java.time.Clock.system(java.time.ZoneId.of("Asia/Bangkok")), serverTimeAnchor, draftRepository)
        advanceUntilIdle()

        vm.onEvent(CreateAppointmentEvent.ProjectSelected("PRJ-2", "Project B", "Lead"))
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isLoadingContacts)
    }
    
    @Test
    fun `project selected should fetch project contacts and show them without pre-selecting`() = runTest {
        val sampleProject = Project(projectId = "PRJ-2", custId = "C2", projectName = "Project B", projectStatus = "Lead")
        every { projectRepo.getAllProjectsFlow() } returns flowOf(listOf(sampleProject))
        coEvery { projectRepo.getProjectById("PRJ-2") } returns Result.success(sampleProject)
        
        // Mock getProjectContacts to return contact CT-1
        coEvery { projectRepo.getProjectContacts("PRJ-2") } returns Result.success(
            listOf(ContactPerson(contactId = "CT-1", custId = "C2", fullName = "Contact One"))
        )
        // Mock getContactPersons to return all customer contacts
        coEvery { customerRepo.getContactPersons("C2", null) } returns Result.success(
            listOf(ContactPerson(contactId = "CT-1", custId = "C2", fullName = "Contact One"))
        )
        coEvery { activityRepo.getMasterActivities() } returns listOf(ActivityMaster(1, "Lead", "Lead item"))
        val vm = CreateAppointmentViewModel(context, activityRepo, projectRepo, customerRepo, contactRepo, authRepo, draftStore, java.time.Clock.system(java.time.ZoneId.of("Asia/Bangkok")), serverTimeAnchor, draftRepository)
        advanceUntilIdle()

        vm.onEvent(CreateAppointmentEvent.ProjectSelected("PRJ-2", "Project B", "Lead"))
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isLoadingContacts)
        assertTrue(vm.uiState.value.selectedContactIds.isEmpty())
        assertEquals(1, vm.uiState.value.contactOptions.size)
        assertEquals("CT-1", vm.uiState.value.contactOptions.first().id)
    }


    @Test
    fun `save should surface addActivity failure message`() = runTest {
        configureBaseData()
        coEvery { activityRepo.getMasterActivities() } returns emptyList()
        every { authRepo.currentUser() } returns AuthUser("U1", "u@test.com", "sale")
        coEvery { activityRepo.addActivity(any()) } returns Result.failure(Exception("insert fail"))
        val vm = CreateAppointmentViewModel(context, activityRepo, projectRepo, customerRepo, contactRepo, authRepo, draftStore, java.time.Clock.system(java.time.ZoneId.of("Asia/Bangkok")), serverTimeAnchor, draftRepository)
        advanceUntilIdle()
        vm.onEvent(CreateAppointmentEvent.LoadInitialProject("PRJ-1"))
        vm.onEvent(CreateAppointmentEvent.TypeChanged("onsite"))
        vm.onEvent(CreateAppointmentEvent.TitleChanged("topic"))
        vm.onEvent(CreateAppointmentEvent.DateChanged("Apr 06, 2099"))
        vm.onEvent(CreateAppointmentEvent.StartTimeSelected("10:00 AM"))
        vm.onEvent(CreateAppointmentEvent.LocationPicked(13.7563, 100.5018))
        advanceUntilIdle()

        vm.onEvent(CreateAppointmentEvent.Save)
        advanceUntilIdle()

        assertEquals("insert fail", vm.uiState.value.saveError)
        assertFalse(vm.uiState.value.isSaved)
    }

    @Test
    fun `save edit mode should call update then add and skip plan items when none selected`() = runTest {
        configureBaseData()
        coEvery { activityRepo.getMasterActivities() } returns emptyList()
        every { authRepo.currentUser() } returns AuthUser("U1", "u@test.com", "sale")
        coEvery { activityRepo.updateActivity(any(), any(), any(), any()) } returns Result.success(Unit)
        coEvery { activityRepo.addActivity(any()) } returns Result.success("ACT-001")
        coEvery { activityRepo.saveAppointmentContacts(any(), any()) } returns Unit

        val vm = CreateAppointmentViewModel(context, activityRepo, projectRepo, customerRepo, contactRepo, authRepo, draftStore, java.time.Clock.system(java.time.ZoneId.of("Asia/Bangkok")), serverTimeAnchor, draftRepository)
        advanceUntilIdle()
        vm.onEvent(CreateAppointmentEvent.LoadInitialProject("PRJ-1"))
        vm.onEvent(CreateAppointmentEvent.TitleChanged("edited"))
        advanceUntilIdle()

        // force edit mode directly in state via repository-mocked data path
        val activitySlot = slot<SalesActivity>()
        coEvery { activityRepo.addActivity(capture(activitySlot)) } returns Result.success("ACT-001")
        coEvery { activityRepo.getActivityById("A-EDIT") } returns Result.success(
            listOf(
                SalesActivity(
                    activityId = "A-EDIT",
                    userId = "U1",
                    customerId = "C1",
                    projectId = "PRJ-1",
                    activityType = "onsite",
                    detail = "old",
                    // วันอนาคตแบบสัมพัทธ์ — วันที่ hardcode ไว้จะกลายเป็นอดีตแล้วไปติดล็อก "ขาดนัด" ในอนาคต
                    activityDate = java.time.LocalDate.now().plusDays(30).toString(),
                    plannedTime = "10:00 AM",
                    status = "planned"
                )
            )
        )
        coEvery { activityRepo.getPlanItems("A-EDIT") } returns Result.success(emptyList())
        coEvery { activityRepo.getAppointmentContacts("A-EDIT") } returns emptyList()

        vm.onEvent(CreateAppointmentEvent.LoadActivity("A-EDIT"))
        advanceUntilIdle()
        // นัดเก่าตัวนี้เป็น onsite แต่ไม่มีพิกัด — กฎใหม่บังคับให้ปักหมุดก่อนถึงจะบันทึกได้
        vm.onEvent(CreateAppointmentEvent.LocationPicked(13.7563, 100.5018))
        vm.onEvent(CreateAppointmentEvent.Save)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isSaved)
        coVerify(exactly = 1) { activityRepo.updateActivity("A-EDIT", any(), any(), any()) }
        coVerify(exactly = 0) { activityRepo.addActivity(any()) }
        // ต้องเรียกด้วยลิสต์ว่าง ไม่ใช่ข้ามไปเลย — เดิมข้าม ทำให้ "ติ๊กเช็คลิสต์ออกจนหมด" ไม่เคย
        // ถูกส่งไปไหน ของเก่ายังค้างทั้งในเครื่องและบน server (savePlanItems ลบก่อนเสมอ)
        coVerify(exactly = 1) { activityRepo.savePlanItems("A-EDIT", emptyList()) }
    }

    // ถอดโครงการออกจากนัดหมาย (เลือก "ไม่ระบุโครงการ") ต้องส่ง project_code = null ขึ้นไป
    // เดิมใช้ ?.let จึงไม่ส่ง key เลย ทั้ง Room และ server เข้าใจว่า "ไม่ได้แก้ฟิลด์นี้" แล้วคงค่าเดิม
    // กลับมาเปิดดูก็ยังผูกโครงการเดิมอยู่เหมือนไม่ได้กดอะไรเลย
    @Test
    fun `clearing the project sends an explicit null so the link is actually removed`() = runTest {
        configureBaseData()
        coEvery { activityRepo.getMasterActivities() } returns emptyList()
        every { authRepo.currentUser() } returns AuthUser("U1", "u@test.com", "sale")
        val updates = slot<Map<String, Any?>>()
        coEvery { activityRepo.updateActivity("A-EDIT", capture(updates), any(), any()) } returns Result.success(Unit)
        coEvery { activityRepo.getActivityById("A-EDIT") } returns Result.success(
            listOf(
                SalesActivity(
                    activityId = "A-EDIT",
                    userId = "U1",
                    customerId = "C1",
                    projectId = "PRJ-1",
                    activityType = "onsite",
                    detail = "old",
                    activityDate = java.time.LocalDate.now().plusDays(30).toString(),
                    plannedTime = "10:00 AM",
                    plannedLat = 13.0,
                    plannedLong = 100.0,
                    status = "planned"
                )
            )
        )
        coEvery { activityRepo.getPlanItems("A-EDIT") } returns Result.success(emptyList())
        coEvery { activityRepo.getAppointmentContacts("A-EDIT") } returns emptyList()

        val vm = CreateAppointmentViewModel(context, activityRepo, projectRepo, customerRepo, contactRepo, authRepo, draftStore, java.time.Clock.system(java.time.ZoneId.of("Asia/Bangkok")), serverTimeAnchor, draftRepository)
        advanceUntilIdle()
        vm.onEvent(CreateAppointmentEvent.LoadActivity("A-EDIT"))
        advanceUntilIdle()
        vm.onEvent(CreateAppointmentEvent.ProjectSelected(null, null, null))
        vm.onEvent(CreateAppointmentEvent.Save)
        advanceUntilIdle()

        assertTrue(updates.captured.containsKey("project_code"))
        assertEquals(null, updates.captured["project_code"])
    }

    // แก้ไขนัดหมายแล้วไม่มีผู้ติดต่อเลย (ถอดออกหมด หรือไม่เคยมี) ต้องไม่ทำให้นัดหมายกลายเป็น
    // "ไม่ใช่นัดหมาย" — is_appointment ต้องเป็น true เสมอเหมือนตอนสร้างใหม่ ไม่ขึ้นกับผู้ติดต่อที่เลือก
    @Test
    fun `save edit mode always sends is_appointment true even with no contacts selected`() = runTest {
        configureBaseData()
        coEvery { activityRepo.getMasterActivities() } returns emptyList()
        every { authRepo.currentUser() } returns AuthUser("U1", "u@test.com", "sale")
        val updatesSlot = slot<Map<String, Any>>()
        coEvery { activityRepo.updateActivity("A-EDIT", capture(updatesSlot), any(), any()) } returns Result.success(Unit)
        coEvery { activityRepo.getActivityById("A-EDIT") } returns Result.success(
            listOf(
                SalesActivity(
                    activityId = "A-EDIT", userId = "U1", customerId = "C1", projectId = "PRJ-1",
                    activityType = "onsite", detail = "old", // วันอนาคตแบบสัมพัทธ์ — วันที่ hardcode ไว้จะกลายเป็นอดีตแล้วไปติดล็อก "ขาดนัด" ในอนาคต
                    activityDate = java.time.LocalDate.now().plusDays(30).toString(),
                    plannedTime = "10:00 AM", status = "planned"
                )
            )
        )
        coEvery { activityRepo.getPlanItems("A-EDIT") } returns Result.success(emptyList())
        coEvery { activityRepo.getAppointmentContacts("A-EDIT") } returns emptyList()

        val vm = CreateAppointmentViewModel(context, activityRepo, projectRepo, customerRepo, contactRepo, authRepo, draftStore, java.time.Clock.system(java.time.ZoneId.of("Asia/Bangkok")), serverTimeAnchor, draftRepository)
        advanceUntilIdle()
        vm.onEvent(CreateAppointmentEvent.LoadActivity("A-EDIT"))
        advanceUntilIdle()
        vm.onEvent(CreateAppointmentEvent.LocationPicked(13.7563, 100.5018))
        vm.onEvent(CreateAppointmentEvent.Save)
        advanceUntilIdle()

        assertEquals(true, updatesSlot.captured["is_appointment"])
    }

    // แผนงาน B.1: แก้แผนได้จนถึงก่อนเวลาเริ่มนัด พ้นไปแล้วแก้ไม่ได้
    // (กฎเดิมคือห้ามแก้เมื่อเหลือไม่ถึง 7 วัน ซึ่งกลับกัน: นัดพรุ่งนี้เคยแก้ไม่ได้ แต่นัดเมื่อวานเคยแก้ได้)
    @Test
    fun `save edit mode is blocked once the original appointment has started`() = runTest {
        configureBaseData()
        coEvery { activityRepo.getMasterActivities() } returns emptyList()
        every { authRepo.currentUser() } returns AuthUser("U1", "u@test.com", "sale")
        val vm = CreateAppointmentViewModel(context, activityRepo, projectRepo, customerRepo, contactRepo, authRepo, draftStore, java.time.Clock.system(java.time.ZoneId.of("Asia/Bangkok")), serverTimeAnchor, draftRepository)
        advanceUntilIdle()

        val started = java.time.LocalDate.now().minusDays(1).toString()
        coEvery { activityRepo.getActivityById("A-SOON") } returns Result.success(
            listOf(
                SalesActivity(
                    activityId = "A-SOON",
                    userId = "U1",
                    customerId = "C1",
                    projectId = "PRJ-1",
                    activityType = "onsite",
                    detail = "old",
                    activityDate = started,
                    plannedTime = "10:00",
                    status = "planned"
                )
            )
        )
        coEvery { activityRepo.getPlanItems("A-SOON") } returns Result.success(emptyList())
        coEvery { activityRepo.getAppointmentContacts("A-SOON") } returns emptyList()

        vm.onEvent(CreateAppointmentEvent.LoadActivity("A-SOON"))
        advanceUntilIdle()
        vm.onEvent(CreateAppointmentEvent.LocationPicked(13.7563, 100.5018))
        vm.onEvent(CreateAppointmentEvent.Save)
        advanceUntilIdle()

        // ขาดนัดไปแล้ว (onsite + พ้นวันนัด + ไม่มีเช็คอิน) เหตุผลจึงเป็นเรื่องสถานะ ไม่ใช่แค่หมดเวลา
        assertEquals(
            "นัดนี้ขาดนัดไปแล้ว จึงแก้ไขแผนไม่ได้ ให้บันทึกผลย้อนหลังแทน",
            vm.uiState.value.saveError
        )
        assertFalse(vm.uiState.value.isSaved)
        coVerify(exactly = 0) { activityRepo.updateActivity(any(), any(), any(), any()) }
    }

    @Test
    fun `save edit mode proceeds while the original appointment is still ahead`() = runTest {
        configureBaseData()
        coEvery { activityRepo.getMasterActivities() } returns emptyList()
        every { authRepo.currentUser() } returns AuthUser("U1", "u@test.com", "sale")
        coEvery { activityRepo.updateActivity(any(), any(), any(), any()) } returns Result.success(Unit)
        val vm = CreateAppointmentViewModel(context, activityRepo, projectRepo, customerRepo, contactRepo, authRepo, draftStore, java.time.Clock.system(java.time.ZoneId.of("Asia/Bangkok")), serverTimeAnchor, draftRepository)
        advanceUntilIdle()

        val farOut = java.time.LocalDate.now().plusDays(30).toString()
        coEvery { activityRepo.getActivityById("A-FAR") } returns Result.success(
            listOf(
                SalesActivity(
                    activityId = "A-FAR",
                    userId = "U1",
                    customerId = "C1",
                    projectId = "PRJ-1",
                    activityType = "onsite",
                    detail = "old",
                    activityDate = farOut,
                    plannedTime = "10:00 AM",
                    status = "planned"
                )
            )
        )
        coEvery { activityRepo.getPlanItems("A-FAR") } returns Result.success(emptyList())
        coEvery { activityRepo.getAppointmentContacts("A-FAR") } returns emptyList()

        vm.onEvent(CreateAppointmentEvent.LoadActivity("A-FAR"))
        advanceUntilIdle()
        vm.onEvent(CreateAppointmentEvent.LocationPicked(13.7563, 100.5018))
        vm.onEvent(CreateAppointmentEvent.Save)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isSaved)
        coVerify(exactly = 1) { activityRepo.updateActivity("A-FAR", any(), any(), any()) }
    }

    // เลือกบริษัทแล้วต้องได้รายชื่อผู้ติดต่อของบริษัทนั้นมาเลย ไม่ต้องพิมพ์ค้นหาก่อน
    @Test
    fun `selecting a company loads that company's contacts right away`() = runTest {
        coEvery { customerRepo.getContactPersons("C1", null) } returns Result.success(
            listOf(
                ContactPerson(contactId = "CT-1", custId = "C1", fullName = "John", isActive = true),
                ContactPerson(contactId = "CT-2", custId = "C1", fullName = "Jane", isActive = true)
            )
        )

        val vm = CreateAppointmentViewModel(context, activityRepo, projectRepo, customerRepo, contactRepo, authRepo, draftStore, java.time.Clock.system(java.time.ZoneId.of("Asia/Bangkok")), serverTimeAnchor, draftRepository)
        advanceUntilIdle()
        vm.onEvent(CreateAppointmentEvent.CompanySelected("C1", "Company A"))
        advanceUntilIdle()

        assertEquals(listOf("John", "Jane"), vm.uiState.value.contactOptions.map { it.name })
        assertFalse(vm.uiState.value.isLoadingContacts)
    }

    // ผู้ติดต่อที่ปิดใช้งานแล้วไม่ควรโผล่มาให้เลือก
    @Test
    fun `inactive contacts are left out when loading a company`() = runTest {
        coEvery { customerRepo.getContactPersons("C1", null) } returns Result.success(
            listOf(
                ContactPerson(contactId = "CT-1", custId = "C1", fullName = "Active", isActive = true),
                ContactPerson(contactId = "CT-2", custId = "C1", fullName = "Resigned", isActive = false)
            )
        )

        val vm = CreateAppointmentViewModel(context, activityRepo, projectRepo, customerRepo, contactRepo, authRepo, draftStore, java.time.Clock.system(java.time.ZoneId.of("Asia/Bangkok")), serverTimeAnchor, draftRepository)
        advanceUntilIdle()
        vm.onEvent(CreateAppointmentEvent.CompanySelected("C1", "Company A"))
        advanceUntilIdle()

        assertEquals(listOf("Active"), vm.uiState.value.contactOptions.map { it.name })
    }

    // เปลี่ยนบริษัทแล้วผู้ติดต่อที่เลือกไว้ของบริษัทเดิมต้องถูกล้าง ไม่งั้นนัดจะผูกคนของบริษัทอื่นไป
    @Test
    fun `changing company clears contacts picked for the previous one`() = runTest {
        coEvery { customerRepo.getContactPersons("C1", null) } returns Result.success(
            listOf(ContactPerson(contactId = "CT-1", custId = "C1", fullName = "John", isActive = true))
        )
        coEvery { customerRepo.getContactPersons("C2", null) } returns Result.success(emptyList())

        val vm = CreateAppointmentViewModel(context, activityRepo, projectRepo, customerRepo, contactRepo, authRepo, draftStore, java.time.Clock.system(java.time.ZoneId.of("Asia/Bangkok")), serverTimeAnchor, draftRepository)
        advanceUntilIdle()
        vm.onEvent(CreateAppointmentEvent.CompanySelected("C1", "Company A"))
        advanceUntilIdle()
        vm.onEvent(CreateAppointmentEvent.ContactToggled("CT-1"))
        assertTrue(vm.uiState.value.selectedContactIds.contains("CT-1"))

        vm.onEvent(CreateAppointmentEvent.CompanySelected("C2", "Company B"))
        advanceUntilIdle()

        assertTrue(vm.uiState.value.selectedContactIds.isEmpty())
        assertEquals("C2", vm.uiState.value.selectedCustomerId)
    }

    @Test
    fun `quick add customer creates a lead and selects it`() = runTest {
        coEvery { authRepo.currentUser() } returns AuthUser(userId = "U1", email = "u@e.com", role = "sale", teamId = "BR-1")
        val custSlot = slot<Customer>()
        coEvery { customerRepo.addCustomer(capture(custSlot)) } returns Result.success("C-NEW")

        val vm = CreateAppointmentViewModel(context, activityRepo, projectRepo, customerRepo, contactRepo, authRepo, draftStore, java.time.Clock.system(java.time.ZoneId.of("Asia/Bangkok")), serverTimeAnchor, draftRepository)
        advanceUntilIdle()
        vm.onEvent(CreateAppointmentEvent.ToggleQuickAddCustomer(true))
        vm.onEvent(CreateAppointmentEvent.QuickAddCustomerChanged("Acme", "Owner"))
        vm.onEvent(CreateAppointmentEvent.SaveQuickAddCustomer)
        advanceUntilIdle()

        // ต้องถูกสร้างเป็น Lead ไม่ใช่ลูกค้าเต็มตัว
        assertTrue(custSlot.captured.isLead)
        assertEquals("Acme", custSlot.captured.companyName)
        assertEquals("Owner", custSlot.captured.custType)

        val st = vm.uiState.value
        assertEquals("C-NEW", st.selectedCustomerId)
        assertEquals("Acme", st.selectedCompanyName)
        assertFalse(st.isQuickAddCustomerOpen)
        assertTrue(st.companyOptions.any { it.first == "C-NEW" })
    }

    @Test
    fun `quick add customer with missing fields does not hit the repository`() = runTest {
        val vm = CreateAppointmentViewModel(context, activityRepo, projectRepo, customerRepo, contactRepo, authRepo, draftStore, java.time.Clock.system(java.time.ZoneId.of("Asia/Bangkok")), serverTimeAnchor, draftRepository)
        advanceUntilIdle()
        vm.onEvent(CreateAppointmentEvent.ToggleQuickAddCustomer(true))
        vm.onEvent(CreateAppointmentEvent.QuickAddCustomerChanged("Acme", ""))
        vm.onEvent(CreateAppointmentEvent.SaveQuickAddCustomer)
        advanceUntilIdle()

        assertEquals("กรุณาระบุชื่อบริษัทและประเภทลูกค้าให้ครบ", vm.uiState.value.quickAddCustomerError)
        assertTrue(vm.uiState.value.isQuickAddCustomerOpen)
        coVerify(exactly = 0) { customerRepo.addCustomer(any()) }
    }

    // โครงการด่วนต้องผูกกับบริษัทที่เลือกไว้แล้ว ไม่งั้นได้โครงการลอยที่ไม่มีลูกค้า
    @Test
    fun `quick add project links to the already selected company and is selected`() = runTest {
        coEvery { authRepo.currentUser() } returns AuthUser(userId = "U1", email = "u@e.com", role = "sale", teamId = "BR-1")
        coEvery { customerRepo.getContactPersons("C1", null) } returns Result.success(emptyList())
        val projectSlot = slot<Project>()
        coEvery { projectRepo.createProject(capture(projectSlot), any()) } returns Result.success(
            Project(projectId = "PRJ-NEW", custId = "C1", projectName = "New Site", projectStatus = "Lead")
        )
        coEvery { projectRepo.getProjectById("PRJ-NEW") } returns Result.success(
            Project(projectId = "PRJ-NEW", custId = "C1", projectName = "New Site", projectStatus = "Lead")
        )

        val vm = CreateAppointmentViewModel(context, activityRepo, projectRepo, customerRepo, contactRepo, authRepo, draftStore, java.time.Clock.system(java.time.ZoneId.of("Asia/Bangkok")), serverTimeAnchor, draftRepository)
        advanceUntilIdle()
        vm.onEvent(CreateAppointmentEvent.CompanySelected("C1", "Company A"))
        advanceUntilIdle()
        vm.onEvent(CreateAppointmentEvent.ToggleQuickAddProject(true))
        vm.onEvent(CreateAppointmentEvent.QuickAddProjectNameChanged("New Site"))
        vm.onEvent(CreateAppointmentEvent.QuickAddProjectStatusChanged("Lead"))
        vm.onEvent(CreateAppointmentEvent.SaveQuickAddProject)
        advanceUntilIdle()

        assertEquals("C1", projectSlot.captured.custId)
        assertEquals("New Site", projectSlot.captured.projectName)

        val st = vm.uiState.value
        assertEquals("PRJ-NEW", st.selectedProjectId)
        assertEquals("New Site", st.selectedProjectName)
        assertFalse(st.isQuickAddProjectOpen)
    }

    @Test
    fun `quick add project with missing fields does not hit the repository`() = runTest {
        val vm = CreateAppointmentViewModel(context, activityRepo, projectRepo, customerRepo, contactRepo, authRepo, draftStore, java.time.Clock.system(java.time.ZoneId.of("Asia/Bangkok")), serverTimeAnchor, draftRepository)
        advanceUntilIdle()
        vm.onEvent(CreateAppointmentEvent.ToggleQuickAddProject(true))
        vm.onEvent(CreateAppointmentEvent.QuickAddProjectNameChanged("New Site"))
        vm.onEvent(CreateAppointmentEvent.SaveQuickAddProject)
        advanceUntilIdle()

        assertEquals("กรุณาระบุชื่อโครงการและสถานะให้ครบ", vm.uiState.value.quickAddProjectError)
        coVerify(exactly = 0) { projectRepo.createProject(any(), any()) }
    }

    // ฉบับร่างย้ายไปอยู่ Room แบบหลายรายการแล้ว (Phase 4C) — ไม่มีการเดาว่าร่างไหนคู่กับฟอร์มไหนอีก
    @Test
    fun `saveDraft stores the form through the draft repository`() = runTest {
        configureBaseData()
        coEvery { draftRepository.saveDraft(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            DraftSaveResult.Saved("D1")
        val vm = CreateAppointmentViewModel(context, activityRepo, projectRepo, customerRepo, contactRepo, authRepo, draftStore, java.time.Clock.system(java.time.ZoneId.of("Asia/Bangkok")), serverTimeAnchor, draftRepository)
        advanceUntilIdle()

        vm.onEvent(CreateAppointmentEvent.TitleChanged("เข้าพบเพื่อนำเสนอ"))
        vm.saveDraft()
        advanceUntilIdle()

        val payload = slot<String>()
        coVerify {
            draftRepository.saveDraft(
                draftId = null, schemaVersion = any(), payloadJson = capture(payload),
                title = "เข้าพบเพื่อนำเสนอ", plannedDate = any(), plannedTime = any(),
                projectId = any(), projectNameSnapshot = any(),
                customerId = any(), customerNameSnapshot = any()
            )
        }
        assertTrue(payload.captured.contains("เข้าพบเพื่อนำเสนอ"))
        assertEquals("D1", vm.uiState.value.draftId)
    }

    /** กดบันทึกร่างซ้ำต้องเขียนทับร่างเดิม ไม่ใช่งอกรายการใหม่ทุกครั้งจนเต็มโควตา */
    @Test
    fun `saving twice reuses the same draft id`() = runTest {
        configureBaseData()
        coEvery { draftRepository.saveDraft(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            DraftSaveResult.Saved("D1")
        val vm = CreateAppointmentViewModel(context, activityRepo, projectRepo, customerRepo, contactRepo, authRepo, draftStore, java.time.Clock.system(java.time.ZoneId.of("Asia/Bangkok")), serverTimeAnchor, draftRepository)
        advanceUntilIdle()

        vm.saveDraft()
        advanceUntilIdle()
        vm.saveDraft()
        advanceUntilIdle()

        coVerify(exactly = 1) { draftRepository.saveDraft(draftId = null, schemaVersion = any(), payloadJson = any(), title = any(), plannedDate = any(), plannedTime = any(), projectId = any(), projectNameSnapshot = any(), customerId = any(), customerNameSnapshot = any()) }
        coVerify(exactly = 1) { draftRepository.saveDraft(draftId = "D1", schemaVersion = any(), payloadJson = any(), title = any(), plannedDate = any(), plannedTime = any(), projectId = any(), projectNameSnapshot = any(), customerId = any(), customerNameSnapshot = any()) }
    }

    @Test
    fun `hitting the limit asks the user to delete instead of dropping a draft`() = runTest {
        configureBaseData()
        coEvery { draftRepository.saveDraft(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            DraftSaveResult.LimitReached(20)
        val vm = CreateAppointmentViewModel(context, activityRepo, projectRepo, customerRepo, contactRepo, authRepo, draftStore, java.time.Clock.system(java.time.ZoneId.of("Asia/Bangkok")), serverTimeAnchor, draftRepository)
        advanceUntilIdle()

        vm.saveDraft()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.draftLimitReached)
        assertEquals(null, vm.uiState.value.draftId)
        coVerify(exactly = 0) { draftRepository.deleteDraft(any()) }
    }

    @Test
    fun `discardDraft deletes only the draft being edited`() = runTest {
        configureBaseData()
        coEvery { draftRepository.getDraft("D1") } returns draftRow("D1", """{"titleTopic":"ค้างไว้"}""")
        val vm = CreateAppointmentViewModel(context, activityRepo, projectRepo, customerRepo, contactRepo, authRepo, draftStore, java.time.Clock.system(java.time.ZoneId.of("Asia/Bangkok")), serverTimeAnchor, draftRepository)
        advanceUntilIdle()
        vm.onEvent(CreateAppointmentEvent.LoadDraft("D1"))
        advanceUntilIdle()

        vm.discardDraft()
        advanceUntilIdle()

        coVerify(exactly = 1) { draftRepository.deleteDraft("D1") }
    }

    @Test
    fun `with no draft open discard touches nothing`() = runTest {
        configureBaseData()
        val vm = CreateAppointmentViewModel(context, activityRepo, projectRepo, customerRepo, contactRepo, authRepo, draftStore, java.time.Clock.system(java.time.ZoneId.of("Asia/Bangkok")), serverTimeAnchor, draftRepository)
        advanceUntilIdle()

        vm.discardDraft()
        advanceUntilIdle()

        coVerify(exactly = 0) { draftRepository.deleteDraft(any()) }
    }

    @Test
    fun `opening a draft fills the form back in`() = runTest {
        configureBaseData()
        coEvery { draftRepository.getDraft("D1") } returns
            draftRow("D1", """{"titleTopic":"หัวข้อที่ค้างไว้","activityType":"call"}""")
        val vm = CreateAppointmentViewModel(context, activityRepo, projectRepo, customerRepo, contactRepo, authRepo, draftStore, java.time.Clock.system(java.time.ZoneId.of("Asia/Bangkok")), serverTimeAnchor, draftRepository)
        advanceUntilIdle()

        vm.onEvent(CreateAppointmentEvent.LoadDraft("D1"))
        advanceUntilIdle()

        assertEquals("หัวข้อที่ค้างไว้", vm.uiState.value.titleTopic)
        assertEquals("call", vm.uiState.value.activityType)
        assertEquals("D1", vm.uiState.value.draftId)
    }

    /** โครงการที่ร่างอ้างถึงถูกลบไปแล้ว ต้องบอกชื่อเดิมและบังคับเลือกใหม่ ไม่ใช่เงียบแล้วบันทึกทับ */
    @Test
    fun `a draft whose project was deleted forces the user to pick again`() = runTest {
        configureBaseData()
        coEvery { draftRepository.getDraft("D2") } returns
            draftRow("D2", """{"selectedProjectId":"PRJ-GONE"}""", projectName = "โครงการเก่า")
        coEvery { draftRepository.resolveParentId("project", "PRJ-GONE") } returns "PRJ-GONE"
        coEvery { draftRepository.projectExists("PRJ-GONE") } returns false
        val vm = CreateAppointmentViewModel(context, activityRepo, projectRepo, customerRepo, contactRepo, authRepo, draftStore, java.time.Clock.system(java.time.ZoneId.of("Asia/Bangkok")), serverTimeAnchor, draftRepository)
        advanceUntilIdle()

        vm.onEvent(CreateAppointmentEvent.LoadDraft("D2"))
        advanceUntilIdle()

        assertEquals(null, vm.uiState.value.selectedProjectId)
        assertTrue(vm.uiState.value.saveError!!.contains("โครงการเก่า"))
    }

    /** TEMP- id ที่ sync ขึ้นไปแล้วระหว่างที่ร่างค้างอยู่ ต้องถูกแทนด้วยรหัสจริง */
    @Test
    fun `a draft pointing at a TEMP id is remapped to the real one`() = runTest {
        configureBaseData()
        coEvery { draftRepository.getDraft("D3") } returns
            draftRow("D3", """{"selectedProjectId":"TEMP-9"}""")
        coEvery { draftRepository.resolveParentId("project", "TEMP-9") } returns "PRJ-1"
        coEvery { draftRepository.projectExists("PRJ-1") } returns true
        val vm = CreateAppointmentViewModel(context, activityRepo, projectRepo, customerRepo, contactRepo, authRepo, draftStore, java.time.Clock.system(java.time.ZoneId.of("Asia/Bangkok")), serverTimeAnchor, draftRepository)
        advanceUntilIdle()

        vm.onEvent(CreateAppointmentEvent.LoadDraft("D3"))
        advanceUntilIdle()

        assertEquals("PRJ-1", vm.uiState.value.selectedProjectId)
    }

    private fun draftRow(
        id: String,
        payload: String,
        projectName: String? = null
    ) = com.example.pp68_salestrackingapp.data.model.AppointmentDraft(
        draftId = id,
        ownerKey = "owner",
        schemaVersion = 1,
        payloadJson = payload,
        projectNameSnapshot = projectName,
        createdAt = "2026-10-06T03:00:00Z",
        updatedAt = "2026-10-06T03:00:00Z",
        expiresAt = "2026-11-05T03:00:00Z"
    )


    // outbox ใช้คำนำหน้า TEMP- ตัดสินว่าจะ POST (สร้างใหม่) หรือ PATCH (แก้ของเดิม)
    // เดิมสร้างด่วนใช้ UUID เปล่า — กดสร้างตอนไม่มีเน็ตแล้ว outbox จะส่ง PATCH
    // ไปหาแถวที่ไม่มีจริง → 404 → ลูกค้าหายไปเงียบ ๆ โดยไม่มีใครรู้
    @Test
    fun `a quick-created customer gets a TEMP- id so the outbox posts it instead of patching`() = runTest {
        configureBaseData()
        coEvery { customerRepo.addCustomer(any()) } returns Result.success("C00123")

        val vm = CreateAppointmentViewModel(context, activityRepo, projectRepo, customerRepo, contactRepo, authRepo, draftStore, java.time.Clock.system(java.time.ZoneId.of("Asia/Bangkok")), serverTimeAnchor, draftRepository)
        advanceUntilIdle()
        vm.onEvent(CreateAppointmentEvent.QuickAddCustomerChanged("บริษัททดสอบ", "Owner"))
        vm.onEvent(CreateAppointmentEvent.SaveQuickAddCustomer)
        advanceUntilIdle()

        val sent = slot<com.example.pp68_salestrackingapp.data.model.Customer>()
        coVerify { customerRepo.addCustomer(capture(sent)) }
        assertTrue(sent.captured.custId.startsWith("TEMP-"))
    }

    @Test
    fun `a quick-created contact gets a TEMP- id and is selected as an attendee`() = runTest {
        configureBaseData()
        coEvery { contactRepo.addContact(any()) } returns Result.success("CT-REAL-1")

        val vm = CreateAppointmentViewModel(context, activityRepo, projectRepo, customerRepo, contactRepo, authRepo, draftStore, java.time.Clock.system(java.time.ZoneId.of("Asia/Bangkok")), serverTimeAnchor, draftRepository)
        advanceUntilIdle()
        vm.onEvent(CreateAppointmentEvent.CompanySelected("C1", "Company A"))
        advanceUntilIdle()
        vm.onEvent(CreateAppointmentEvent.QuickAddContactChanged("คุณทดสอบ", "0812345678"))
        vm.onEvent(CreateAppointmentEvent.SaveQuickAddContact)
        advanceUntilIdle()

        val sent = slot<com.example.pp68_salestrackingapp.data.model.ContactPerson>()
        coVerify { contactRepo.addContact(capture(sent)) }
        assertTrue(sent.captured.contactId.startsWith("TEMP-"))
        assertEquals("C1", sent.captured.custId)

        // ต้องใช้ id จริงที่ repository คืนมา ไม่ใช่ TEMP- ที่ส่งไป
        assertTrue(vm.uiState.value.selectedContactIds.contains("CT-REAL-1"))
        assertFalse(vm.uiState.value.isQuickAddContactOpen)
    }

    // ยังไม่เลือกบริษัท = ไม่รู้ว่าผู้ติดต่อคนนี้อยู่บริษัทไหน ห้ามสร้างลอย ๆ
    @Test
    fun `a quick contact without a company selected is refused`() = runTest {
        configureBaseData()

        val vm = CreateAppointmentViewModel(context, activityRepo, projectRepo, customerRepo, contactRepo, authRepo, draftStore, java.time.Clock.system(java.time.ZoneId.of("Asia/Bangkok")), serverTimeAnchor, draftRepository)
        advanceUntilIdle()
        vm.onEvent(CreateAppointmentEvent.QuickAddContactChanged("คุณทดสอบ", "0812345678"))
        vm.onEvent(CreateAppointmentEvent.SaveQuickAddContact)
        advanceUntilIdle()

        coVerify(exactly = 0) { contactRepo.addContact(any()) }
        assertNotNull(vm.uiState.value.quickAddContactError)
    }
}
