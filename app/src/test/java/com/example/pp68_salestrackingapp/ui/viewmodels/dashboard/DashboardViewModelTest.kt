package com.example.pp68_salestrackingapp.ui.viewmodels.dashboard

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.pp68_salestrackingapp.data.model.AuthUser
import com.example.pp68_salestrackingapp.data.repository.ActivityRepository
import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import com.example.pp68_salestrackingapp.data.repository.CustomerRepository
import com.example.pp68_salestrackingapp.data.repository.DashboardRepository
import com.example.pp68_salestrackingapp.data.repository.DashboardSummary
import com.example.pp68_salestrackingapp.data.repository.ProjectRepository
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private val dashboardRepository = mockk<DashboardRepository>(relaxed = true)
    private val customerRepo = mockk<CustomerRepository>(relaxed = true)
    private val projectRepo = mockk<ProjectRepository>(relaxed = true)
    private val activityRepo = mockk<ActivityRepository>(relaxed = true)
    private val authRepo = mockk<AuthRepository>(relaxed = true)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { dashboardRepository.getDashboardSummary() } returns flowOf(DashboardSummary(3, 2, 1))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init should refresh all repos when user exists`() = runTest {
        every { authRepo.currentUser() } returns AuthUser(userId = "USR-001", email = "x@test.com", role = "sale")

        DashboardViewModel(dashboardRepository, customerRepo, projectRepo, activityRepo, authRepo)
        advanceUntilIdle()

        coVerify(exactly = 1) { customerRepo.refreshCustomers("USR-001") }
        coVerify(exactly = 1) { projectRepo.refreshProjects("USR-001") }
        coVerify(exactly = 1) { activityRepo.refreshActivities("USR-001") }
    }

    @Test
    fun `init should not refresh when no current user`() = runTest {
        every { authRepo.currentUser() } returns null

        DashboardViewModel(dashboardRepository, customerRepo, projectRepo, activityRepo, authRepo)
        advanceUntilIdle()

        coVerify(exactly = 0) { customerRepo.refreshCustomers(any()) }
        coVerify(exactly = 0) { projectRepo.refreshProjects(any()) }
        coVerify(exactly = 0) { activityRepo.refreshActivities(any()) }
    }

    @Test
    fun `summary state should expose repository values`() = runTest {
        every { authRepo.currentUser() } returns null
        val vm = DashboardViewModel(dashboardRepository, customerRepo, projectRepo, activityRepo, authRepo)
        advanceUntilIdle()

        assertEquals(3, vm.summary.value.totalCustomers)
        assertEquals(2, vm.summary.value.activeProjects)
        assertEquals(1, vm.summary.value.completedActivities)
    }
}
