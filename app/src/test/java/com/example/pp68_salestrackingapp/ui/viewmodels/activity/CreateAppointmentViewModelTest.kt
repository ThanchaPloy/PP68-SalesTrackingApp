package com.example.pp68_salestrackingapp.ui.viewmodels.activity

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.pp68_salestrackingapp.data.model.ActivityMaster
import com.example.pp68_salestrackingapp.data.model.AuthUser
import com.example.pp68_salestrackingapp.data.model.ContactPerson
import com.example.pp68_salestrackingapp.data.model.Project
import com.example.pp68_salestrackingapp.data.repository.ActivityRepository
import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import com.example.pp68_salestrackingapp.data.repository.CustomerRepository
import com.example.pp68_salestrackingapp.data.repository.ProjectRepository
import io.mockk.coEvery
import io.mockk.coVerify
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
}
