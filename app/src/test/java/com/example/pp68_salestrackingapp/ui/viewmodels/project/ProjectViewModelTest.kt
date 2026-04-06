package com.example.pp68_salestrackingapp.ui.viewmodels.project

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.pp68_salestrackingapp.data.model.AuthUser
import com.example.pp68_salestrackingapp.data.model.Project
import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import com.example.pp68_salestrackingapp.data.repository.ProjectRepository
import com.example.pp68_salestrackingapp.ui.screen.project.ProjectListViewModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val dispatcher = StandardTestDispatcher()
    private val projectRepo = mockk<ProjectRepository>(relaxed = true)
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
    fun `refreshDataFromApi should set error when no user`() = runTest {
        every { authRepo.currentUser() } returns null
        every { projectRepo.getAllProjectsFlow() } returns flowOf(emptyList())

        val vm = ProjectListViewModel(projectRepo, authRepo)
        advanceUntilIdle()

        assertEquals("กรุณาเข้าสู่ระบบใหม่", vm.error.value)
    }

    @Test
    fun `filters should apply by status and score`() = runTest {
        val today = LocalDate.now().toString()
        every { authRepo.currentUser() } returns AuthUser("U1", "u@test.com", "sale")
        coEvery { projectRepo.refreshProjects("U1") } returns Result.success(Unit)
        every { projectRepo.getAllProjectsFlow() } returns flowOf(
            listOf(
                Project("P1", "C1", projectName = "A", projectStatus = "Quotation", opportunityScore = "HOT"),
                Project("P2", "C1", projectName = "B", projectStatus = "Lost", opportunityScore = "COLD"),
                Project("P3", "C1", projectName = "C", projectStatus = "PO", closingDate = today, opportunityScore = "WARM")
            )
        )
        every { projectRepo.searchProjectsFlow(any()) } returns flowOf(emptyList())

        val vm = ProjectListViewModel(projectRepo, authRepo)
        advanceUntilIdle()
        vm.onSelectTab(0)
        vm.toggleStatusFilter("Quotation")
        vm.toggleScoreFilter("HOT")
        advanceTimeBy(350)
        advanceUntilIdle()

        assertTrue(vm.projects.value.all { it.projectStatus == "Quotation" })
        assertTrue(vm.projects.value.all { it.opportunityScore == "HOT" })
    }
}
