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
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class SalesResultViewModelTest {

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
            authRepo
        )
        advanceUntilIdle()

        assertEquals("PRJ-1", vm.uiState.value.projectId)
        assertEquals("Project A", vm.uiState.value.project?.projectName)
        assertEquals("Lead", vm.uiState.value.currentStatus)
        assertFalse(vm.uiState.value.isLoading)
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
            activityId = "A1",
            newStatus = "Quotation",
            opportunityScore = "WARM",
            summary = "done summary"
        )

        val vm = SalesResultViewModel(
            SavedStateHandle(mapOf("activityId" to "A1")),
            projectRepo,
            activityRepo,
            authRepo
        )
        advanceUntilIdle()

        assertEquals("A1", vm.uiState.value.activityId)
        assertEquals("PRJ-1", vm.uiState.value.projectId)
        assertTrue(vm.uiState.value.isStatusUpdateEnabled)
        assertEquals("done summary", vm.uiState.value.visitSummary)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `save should validate summary before repository call`() = runTest {
        val vm = SalesResultViewModel(
            SavedStateHandle(mapOf("activityId" to "A1", "projectId" to "PRJ-1")),
            projectRepo,
            activityRepo,
            authRepo
        )
        advanceUntilIdle()

        vm.save()

        assertEquals("กรุณากรอกสรุปการเข้าพบ", vm.uiState.value.error)
    }

    @Test
    fun `save success should persist result and mark saved`() = runTest {
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
        every { authRepo.currentUser() } returns AuthUser("U1", "u@test.com", "sale", fullName = "User One")
        coEvery { activityRepo.saveActivityResult(any()) } returns Result.success(Unit)
        coEvery { activityRepo.updateActivity(any(), any()) } returns Result.success(Unit)
        coEvery { projectRepo.updateProjectFields(any(), any(), any()) } returns Result.success(Unit)

        val vm = SalesResultViewModel(
            SavedStateHandle(mapOf("activityId" to "A1")),
            projectRepo,
            activityRepo,
            authRepo
        )
        advanceUntilIdle()
        vm.onSummaryChanged("visit summary")
        vm.onStatusToggle(true)
        vm.onNewStatusSelected("Quotation")
        vm.onOpportunitySelected("สูง (HOT)")

        vm.save()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isSaved)
        assertFalse(vm.uiState.value.isSaving)
        coVerify(exactly = 1) { activityRepo.saveActivityResult(any()) }
    }

    @Test
    fun `save should validate missing activity id`() = runTest {
        coEvery { projectRepo.getProjectById("PRJ-1") } returns Result.success(
            Project(projectId = "PRJ-1", custId = "C1", projectName = "Project A")
        )

        val vm = SalesResultViewModel(
            SavedStateHandle(mapOf("projectId" to "PRJ-1")),
            projectRepo,
            activityRepo,
            authRepo
        )
        advanceUntilIdle()
        vm.onSummaryChanged("summary")

        vm.save()

        assertEquals("ไม่พบรหัสนัดหมาย", vm.uiState.value.error)
    }

    @Test
    fun `save should validate missing customer`() = runTest {
        val vm = SalesResultViewModel(
            SavedStateHandle(mapOf("activityId" to "A1")),
            projectRepo,
            activityRepo,
            authRepo
        )
        advanceUntilIdle()
        vm.onSummaryChanged("summary")

        vm.save()

        assertEquals("ไม่พบข้อมูลลูกค้า", vm.uiState.value.error)
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
            authRepo
        )
        advanceUntilIdle()
        vm.onSummaryChanged("summary")

        vm.save()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isSaving)
        assertTrue(vm.uiState.value.error?.contains("db down") == true)
    }
}
