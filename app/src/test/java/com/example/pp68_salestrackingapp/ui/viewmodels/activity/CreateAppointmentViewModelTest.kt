package com.example.pp68_salestrackingapp.ui.viewmodels.activity

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.pp68_salestrackingapp.data.model.ActivityMaster
import com.example.pp68_salestrackingapp.data.model.AuthUser
import com.example.pp68_salestrackingapp.data.model.ContactPerson
import com.example.pp68_salestrackingapp.data.model.SalesActivity
import com.example.pp68_salestrackingapp.data.model.Project
import com.example.pp68_salestrackingapp.data.repository.ActivityRepository
import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import com.example.pp68_salestrackingapp.data.repository.CustomerRepository
import com.example.pp68_salestrackingapp.data.repository.ProjectRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
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
    private val activityRepo = mockk<ActivityRepository>(relaxed = true)
    private val projectRepo = mockk<ProjectRepository>(relaxed = true)
    private val customerRepo = mockk<CustomerRepository>(relaxed = true)
    private val authRepo = mockk<AuthRepository>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
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
        coEvery { customerRepo.getContactPersons("C1") } returns Result.success(
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

        val vm = CreateAppointmentViewModel(activityRepo, projectRepo, customerRepo, authRepo)
        advanceUntilIdle()

        assertEquals(1, vm.uiState.value.projectOptions.size)
        assertEquals("Project A", vm.uiState.value.projectOptions.first().name)
        assertEquals(1, vm.uiState.value.allMasterOptions.size)
        assertFalse(vm.uiState.value.isLoadingProjects)
        assertFalse(vm.uiState.value.isLoadingMasters)
    }

    @Test
    fun `master load failure should fallback to default masters`() = runTest {
        configureBaseData()
        coEvery { activityRepo.getMasterActivities() } throws RuntimeException("network")

        val vm = CreateAppointmentViewModel(activityRepo, projectRepo, customerRepo, authRepo)
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
        val vm = CreateAppointmentViewModel(activityRepo, projectRepo, customerRepo, authRepo)
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
    fun `save should fail when no project selected`() = runTest {
        configureBaseData()
        coEvery { activityRepo.getMasterActivities() } returns emptyList()
        val vm = CreateAppointmentViewModel(activityRepo, projectRepo, customerRepo, authRepo)
        advanceUntilIdle()

        vm.onEvent(CreateAppointmentEvent.Save)

        assertEquals("กรุณาเลือกโครงการ", vm.uiState.value.projectError)
    }

    @Test
    fun `save should fail when user is missing`() = runTest {
        configureBaseData()
        coEvery { activityRepo.getMasterActivities() } returns listOf(ActivityMaster(1, "Lead", "L"))
        every { authRepo.currentUser() } returns null
        val vm = CreateAppointmentViewModel(activityRepo, projectRepo, customerRepo, authRepo)
        advanceUntilIdle()
        vm.onEvent(CreateAppointmentEvent.LoadInitialProject("PRJ-1"))
        vm.onEvent(CreateAppointmentEvent.TitleChanged("Visit"))
        advanceUntilIdle()

        vm.onEvent(CreateAppointmentEvent.Save)
        advanceUntilIdle()

        assertEquals("ไม่พบข้อมูล User กรุณา Login ใหม่", vm.uiState.value.saveError)
    }

    @Test
    fun `save create success should persist activity contacts and plan items`() = runTest {
        configureBaseData()
        coEvery { activityRepo.getMasterActivities() } returns listOf(ActivityMaster(1, "Lead", "Lead objective"))
        every { authRepo.currentUser() } returns AuthUser("U1", "u@test.com", "sale")
        coEvery { activityRepo.addActivity(any()) } returns Result.success(Unit)
        coEvery { activityRepo.saveAppointmentContacts(any(), any()) } returns Unit
        coEvery { activityRepo.savePlanItems(any(), any()) } returns Unit

        val vm = CreateAppointmentViewModel(activityRepo, projectRepo, customerRepo, authRepo)
        advanceUntilIdle()
        vm.onEvent(CreateAppointmentEvent.LoadInitialProject("PRJ-1"))
        vm.onEvent(CreateAppointmentEvent.TypeChanged("onsite"))
        vm.onEvent(CreateAppointmentEvent.TitleChanged("Visit topic"))
        vm.onEvent(CreateAppointmentEvent.DateChanged("Apr 06, 2026"))
        vm.onEvent(CreateAppointmentEvent.StartTimeSelected("09:00 AM"))
        vm.onEvent(CreateAppointmentEvent.EndTimeSelected("10:00 AM"))
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
        val vm = CreateAppointmentViewModel(activityRepo, projectRepo, customerRepo, authRepo)
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
        coEvery { activityRepo.getMasterActivities() } returns listOf(
            ActivityMaster(1, "Lead", "Lead item"),
            ActivityMaster(2, "Quotation", "Quotation item")
        )
        val vm = CreateAppointmentViewModel(activityRepo, projectRepo, customerRepo, authRepo)
        advanceUntilIdle()

        vm.onEvent(CreateAppointmentEvent.ContactToggled("CT-1"))
        vm.onEvent(CreateAppointmentEvent.ProjectSelected("PRJ-1", "Project A", "unknown_status"))
        advanceUntilIdle()

        assertTrue(vm.uiState.value.selectedContactIds.isEmpty())
        assertEquals(2, vm.uiState.value.masterOptions.size)
    }

    @Test
    fun `load initial project failure should keep project selection unchanged`() = runTest {
        configureBaseData()
        coEvery { activityRepo.getMasterActivities() } returns emptyList()
        coEvery { projectRepo.getProjectById("PRJ-X") } returns Result.failure(Exception("not found"))
        val vm = CreateAppointmentViewModel(activityRepo, projectRepo, customerRepo, authRepo)
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
        val vm = CreateAppointmentViewModel(activityRepo, projectRepo, customerRepo, authRepo)
        advanceUntilIdle()

        vm.onEvent(CreateAppointmentEvent.ProjectSelected("PRJ-2", "Project B", "Lead"))
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isLoadingContacts)
        assertTrue(vm.uiState.value.contactOptions.isEmpty())
    }

    @Test
    fun `save should fail when project customer cannot be resolved`() = runTest {
        every { projectRepo.getAllProjectsFlow() } returns flowOf(
            listOf(Project(projectId = "PRJ-3", custId = "", projectName = "Project C", projectStatus = "Lead"))
        )
        coEvery { projectRepo.getProjectById("PRJ-3") } returns Result.success(
            Project(projectId = "PRJ-3", custId = "", projectName = "Project C", projectStatus = "Lead")
        )
        coEvery { customerRepo.getContactPersons(any()) } returns Result.success(emptyList())
        coEvery { activityRepo.getMasterActivities() } returns emptyList()
        every { authRepo.currentUser() } returns AuthUser("U1", "u@test.com", "sale")
        val vm = CreateAppointmentViewModel(activityRepo, projectRepo, customerRepo, authRepo)
        advanceUntilIdle()
        vm.onEvent(CreateAppointmentEvent.LoadInitialProject("PRJ-3"))
        vm.onEvent(CreateAppointmentEvent.TitleChanged("topic"))
        advanceUntilIdle()

        vm.onEvent(CreateAppointmentEvent.Save)
        advanceUntilIdle()

        assertEquals("ไม่พบข้อมูลลูกค้าของโครงการนี้", vm.uiState.value.saveError)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `save should surface addActivity failure message`() = runTest {
        configureBaseData()
        coEvery { activityRepo.getMasterActivities() } returns emptyList()
        every { authRepo.currentUser() } returns AuthUser("U1", "u@test.com", "sale")
        coEvery { activityRepo.addActivity(any()) } returns Result.failure(Exception("insert fail"))
        val vm = CreateAppointmentViewModel(activityRepo, projectRepo, customerRepo, authRepo)
        advanceUntilIdle()
        vm.onEvent(CreateAppointmentEvent.LoadInitialProject("PRJ-1"))
        vm.onEvent(CreateAppointmentEvent.TitleChanged("topic"))
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
        coEvery { activityRepo.updateActivity(any(), any()) } returns Result.success(Unit)
        coEvery { activityRepo.addActivity(any()) } returns Result.success(Unit)
        coEvery { activityRepo.saveAppointmentContacts(any(), any()) } returns Unit

        val vm = CreateAppointmentViewModel(activityRepo, projectRepo, customerRepo, authRepo)
        advanceUntilIdle()
        vm.onEvent(CreateAppointmentEvent.LoadInitialProject("PRJ-1"))
        vm.onEvent(CreateAppointmentEvent.TitleChanged("edited"))
        advanceUntilIdle()

        // force edit mode directly in state via repository-mocked data path
        val activitySlot = slot<SalesActivity>()
        coEvery { activityRepo.addActivity(capture(activitySlot)) } returns Result.success(Unit)
        coEvery { activityRepo.getActivityById("A-EDIT") } returns Result.success(
            listOf(
                SalesActivity(
                    activityId = "A-EDIT",
                    userId = "U1",
                    customerId = "C1",
                    projectId = "PRJ-1",
                    activityType = "onsite",
                    detail = "old",
                    activityDate = "2026-04-06"
                )
            )
        )
        coEvery { activityRepo.getPlanItems("A-EDIT") } returns Result.success(emptyList())
        coEvery { activityRepo.getAppointmentContacts("A-EDIT") } returns emptyList()

        vm.onEvent(CreateAppointmentEvent.LoadActivity("A-EDIT"))
        advanceUntilIdle()
        vm.onEvent(CreateAppointmentEvent.Save)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isSaved)
        coVerify(exactly = 1) { activityRepo.updateActivity("A-EDIT", any()) }
        coVerify(atLeast = 1) { activityRepo.addActivity(any()) }
        coVerify(exactly = 0) { activityRepo.savePlanItems(any(), any()) }
    }
}
