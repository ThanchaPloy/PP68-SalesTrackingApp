package com.example.pp68_salestrackingapp.ui.viewmodels.project

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import com.example.pp68_salestrackingapp.data.model.AuthUser
import com.example.pp68_salestrackingapp.data.model.Customer
import com.example.pp68_salestrackingapp.data.model.Project
import com.example.pp68_salestrackingapp.data.model.SalesActivity
import com.example.pp68_salestrackingapp.data.repository.ActivityRepository
import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import com.example.pp68_salestrackingapp.data.repository.CustomerRepository
import com.example.pp68_salestrackingapp.data.repository.ProjectRepository
import com.example.pp68_salestrackingapp.ui.viewmodels.ProjectDetailViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
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
class ProjectDetailViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val dispatcher = StandardTestDispatcher()
    private val projectRepo = mockk<ProjectRepository>(relaxed = true)
    private val authRepo = mockk<AuthRepository>(relaxed = true)
    private val activityRepo = mockk<ActivityRepository>(relaxed = true)
    private val customerRepo = mockk<CustomerRepository>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        clearAllMocks()
        Dispatchers.resetMain()
    }

    @Test
    fun `init should load detail and split tasks history`() = runTest {
        coEvery { projectRepo.getProjectById("PRJ-1") } returns Result.success(
            Project(projectId = "PRJ-1", custId = "C1", projectName = "Project A")
        )
        coEvery { customerRepo.getCustomerById("C1") } returns Result.success(
            Customer("C1", "Company A", null, null, null, null, null, null, null)
        )
        every { activityRepo.getActivitiesByProjectFlow("PRJ-1") } returns MutableStateFlow(
            listOf(
                SalesActivity(
                    activityId = "A1",
                    userId = "U1",
                    customerId = "C1",
                    projectId = "PRJ-1",
                    activityType = "Visit",
                    activityDate = "2026-04-01",
                    status = "planned",
                    detail = "Upcoming"
                ),
                SalesActivity(
                    activityId = "A2",
                    userId = "U1",
                    customerId = "C1",
                    projectId = "PRJ-1",
                    activityType = "Call",
                    activityDate = "2026-04-02",
                    status = "completed",
                    detail = "Done"
                )
            )
        )

        val vm = ProjectDetailViewModel(
            projectRepo, authRepo, activityRepo, customerRepo,
            SavedStateHandle(mapOf("projectId" to "PRJ-1"))
        )
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isLoading)
        assertEquals("Project A", vm.uiState.value.project?.projectName)
        assertEquals("Company A", vm.uiState.value.companyName)
        assertEquals(1, vm.uiState.value.upcomingTasks.size)
        assertEquals(1, vm.uiState.value.history.size)
        assertTrue(vm.uiState.value.teamMembers.isNotEmpty())
    }

    @Test
    fun `loadProjectDetail failure should set error`() = runTest {
        coEvery { projectRepo.getProjectById("PRJ-1") } returns Result.failure(Exception("load error"))
        every { activityRepo.getActivitiesByProjectFlow("PRJ-1") } returns MutableStateFlow(emptyList())

        val vm = ProjectDetailViewModel(
            projectRepo, authRepo, activityRepo, customerRepo,
            SavedStateHandle(mapOf("projectId" to "PRJ-1"))
        )
        advanceUntilIdle()

        assertEquals("load error", vm.uiState.value.error)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `deleteProject success should set deleteSuccess true`() = runTest {
        coEvery { projectRepo.getProjectById("PRJ-1") } returns Result.success(
            Project(projectId = "PRJ-1", custId = "C1", projectName = "Project A")
        )
        coEvery { customerRepo.getCustomerById(any()) } returns Result.success(
            Customer("C1", "Company A", null, null, null, null, null, null, null)
        )
        every { activityRepo.getActivitiesByProjectFlow(any()) } returns MutableStateFlow(emptyList())
        coEvery { projectRepo.deleteProject("PRJ-1") } returns Result.success(Unit)

        val vm = ProjectDetailViewModel(
            projectRepo, authRepo, activityRepo, customerRepo,
            SavedStateHandle(mapOf("projectId" to "PRJ-1"))
        )
        advanceUntilIdle()
        vm.deleteProject()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.deleteSuccess)
        assertFalse(vm.uiState.value.isDeleting)
    }

    @Test
    fun `logout should call auth repository`() = runTest {
        coEvery { projectRepo.getProjectById("PRJ-1") } returns Result.success(
            Project(projectId = "PRJ-1", custId = "C1", projectName = "Project A")
        )
        coEvery { customerRepo.getCustomerById(any()) } returns Result.success(
            Customer("C1", "Company A", null, null, null, null, null, null, null)
        )
        every { activityRepo.getActivitiesByProjectFlow(any()) } returns MutableStateFlow(emptyList())
        coEvery { authRepo.logout() } returns Unit

        val vm = ProjectDetailViewModel(
            projectRepo, authRepo, activityRepo, customerRepo,
            SavedStateHandle(mapOf("projectId" to "PRJ-1"))
        )
        advanceUntilIdle()
        vm.logout()
        advanceUntilIdle()

        coVerify(exactly = 1) { authRepo.logout() }
    }

    @Test
    fun `deleteProject failure should set error and stop deleting`() = runTest {
        coEvery { projectRepo.getProjectById("PRJ-1") } returns Result.success(
            Project(projectId = "PRJ-1", custId = "C1", projectName = "Project A")
        )
        coEvery { customerRepo.getCustomerById("C1") } returns Result.success(
            Customer("C1", "Company A", null, null, null, null, null, null, null)
        )
        every { activityRepo.getActivitiesByProjectFlow("PRJ-1") } returns MutableStateFlow(emptyList())
        coEvery { projectRepo.deleteProject("PRJ-1") } returns Result.failure(Exception("delete failed"))

        val vm = ProjectDetailViewModel(
            projectRepo, authRepo, activityRepo, customerRepo,
            SavedStateHandle(mapOf("projectId" to "PRJ-1"))
        )
        advanceUntilIdle()
        vm.deleteProject()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isDeleting)
        assertEquals("delete failed", vm.uiState.value.error)
    }

    @Test
    fun `init without projectId should not load detail or activities`() = runTest {
        val vm = ProjectDetailViewModel(
            projectRepo, authRepo, activityRepo, customerRepo,
            SavedStateHandle()
        )
        advanceUntilIdle()

        assertNull(vm.uiState.value.project)
        assertFalse(vm.uiState.value.isLoading)
        assertTrue(vm.uiState.value.upcomingTasks.isEmpty())
        assertTrue(vm.uiState.value.history.isEmpty())
        coVerify(exactly = 0) { projectRepo.getProjectById(any()) }
    }

    @Test
    fun `loadProjectDetail success when customer lookup fails should fallback companyName empty`() = runTest {
        coEvery { projectRepo.getProjectById("PRJ-2") } returns Result.success(
            Project(projectId = "PRJ-2", custId = "C2", projectName = "Project B")
        )
        coEvery { customerRepo.getCustomerById("C2") } returns Result.failure(Exception("missing customer"))
        every { activityRepo.getActivitiesByProjectFlow("PRJ-2") } returns MutableStateFlow(emptyList())

        val vm = ProjectDetailViewModel(
            projectRepo, authRepo, activityRepo, customerRepo,
            SavedStateHandle(mapOf("projectId" to "PRJ-2"))
        )
        advanceUntilIdle()

        assertEquals("Project B", vm.uiState.value.project?.projectName)
        assertEquals("", vm.uiState.value.companyName)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `observeActivities should treat completed status case-insensitively`() = runTest {
        coEvery { projectRepo.getProjectById("PRJ-3") } returns Result.success(
            Project(projectId = "PRJ-3", custId = "C3", projectName = "Project C")
        )
        coEvery { customerRepo.getCustomerById("C3") } returns Result.success(
            Customer("C3", "Company C", null, null, null, null, null, null, null)
        )
        every { activityRepo.getActivitiesByProjectFlow("PRJ-3") } returns flowOf(
            listOf(
                SalesActivity(
                    activityId = "A1",
                    userId = "U1",
                    customerId = "C3",
                    projectId = "PRJ-3",
                    activityType = "Visit",
                    activityDate = "2026-04-03",
                    status = "Completed",
                    detail = "Done"
                ),
                SalesActivity(
                    activityId = "A2",
                    userId = "U1",
                    customerId = "C3",
                    projectId = "PRJ-3",
                    activityType = "Call",
                    activityDate = "2026-04-04",
                    status = "PLANNED",
                    detail = "Todo"
                )
            )
        )

        val vm = ProjectDetailViewModel(
            projectRepo, authRepo, activityRepo, customerRepo,
            SavedStateHandle(mapOf("projectId" to "PRJ-3"))
        )
        advanceUntilIdle()

        assertEquals(1, vm.uiState.value.history.size)
        assertEquals("A1", vm.uiState.value.history.first().activityId)
        assertEquals(1, vm.uiState.value.upcomingTasks.size)
        assertEquals("A2", vm.uiState.value.upcomingTasks.first().activityId)
    }
}
