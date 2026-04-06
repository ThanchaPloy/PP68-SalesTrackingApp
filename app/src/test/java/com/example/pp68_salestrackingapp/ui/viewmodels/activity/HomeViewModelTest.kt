package com.example.pp68_salestrackingapp.ui.viewmodels.activity

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.pp68_salestrackingapp.data.model.AuthUser
import com.example.pp68_salestrackingapp.data.model.Customer
import com.example.pp68_salestrackingapp.data.model.Project
import com.example.pp68_salestrackingapp.data.model.SalesActivity
import com.example.pp68_salestrackingapp.data.repository.ActivityRepository
import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import com.example.pp68_salestrackingapp.data.repository.CallLogRepository
import com.example.pp68_salestrackingapp.data.repository.CustomerRepository
import com.example.pp68_salestrackingapp.data.repository.ProjectRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val dispatcher = StandardTestDispatcher()
    private val activityRepo = mockk<ActivityRepository>(relaxed = true)
    private val authRepo = mockk<AuthRepository>(relaxed = true)
    private val customerRepo = mockk<CustomerRepository>(relaxed = true)
    private val projectRepo = mockk<ProjectRepository>(relaxed = true)
    private val callLogRepo = mockk<CallLogRepository>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init with current user should refresh and load activities`() = runTest {
        val nowMonth = YearMonth.now()
        val validDate = "${nowMonth}-10"

        every { authRepo.currentUser() } returns AuthUser("U1", "u@test.com", "sale")
        coEvery { customerRepo.getAllContactPhoneMap() } returns mapOf("0812345678" to "C1")
        coEvery { callLogRepo.syncCallLogs(any()) } returns Result.success(1)
        coEvery { activityRepo.refreshActivities("U1") } returns Result.success(Unit)
        coEvery { customerRepo.refreshCustomers("U1") } returns Result.success(Unit)
        coEvery { projectRepo.refreshProjects("U1") } returns Result.success(Unit)
        every { activityRepo.getAllActivitiesFlow() } returns flowOf(emptyList())
        every { customerRepo.getAllCustomersFlow() } returns flowOf(emptyList())
        every { projectRepo.getAllProjectsFlow() } returns flowOf(emptyList())
        every { activityRepo.getAllResultIdsFlow() } returns flowOf(emptyList())
        coEvery { activityRepo.getMyActivitiesWithDetails() } returns Result.success(
            listOf(
                ActivityCard(
                    activityId = "A1",
                    activityType = "Visit",
                    projectName = "Project",
                    companyName = "Company",
                    contactName = "Contact",
                    objective = "Objective",
                    planStatus = "planned",
                    plannedDate = validDate,
                    plannedTime = "09:00",
                    plannedEndTime = "10:00"
                )
            )
        )

        val vm = HomeViewModel(activityRepo, authRepo, customerRepo, projectRepo, callLogRepo)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isLoading)
        assertTrue(vm.uiState.value.groupedCards.isNotEmpty())
        coVerify(exactly = 1) { activityRepo.refreshActivities("U1") }
        coVerify(exactly = 1) { customerRepo.refreshCustomers("U1") }
        coVerify(exactly = 1) { projectRepo.refreshProjects("U1") }
        coVerify(exactly = 1) { callLogRepo.syncCallLogs(any()) }
    }

    @Test
    fun `loadActivities failure should set error`() = runTest {
        every { authRepo.currentUser() } returns null
        every { activityRepo.getAllActivitiesFlow() } returns flowOf(emptyList())
        every { customerRepo.getAllCustomersFlow() } returns flowOf(emptyList())
        every { projectRepo.getAllProjectsFlow() } returns flowOf(emptyList())
        every { activityRepo.getAllResultIdsFlow() } returns flowOf(emptyList())
        coEvery { activityRepo.getMyActivitiesWithDetails() } returns Result.failure(Exception("cannot load"))

        val vm = HomeViewModel(activityRepo, authRepo, customerRepo, projectRepo, callLogRepo)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.error == "cannot load")
    }

    @Test
    fun `selectMonth should update selected month and reload`() = runTest {
        val month = YearMonth.now().minusMonths(1)
        val dateInMonth = "${month}-05"
        every { authRepo.currentUser() } returns null
        every { activityRepo.getAllActivitiesFlow() } returns flowOf(emptyList())
        every { customerRepo.getAllCustomersFlow() } returns flowOf(emptyList())
        every { projectRepo.getAllProjectsFlow() } returns flowOf(emptyList())
        every { activityRepo.getAllResultIdsFlow() } returns flowOf(emptyList())
        coEvery { activityRepo.getMyActivitiesWithDetails() } returns Result.success(
            listOf(
                ActivityCard(
                    activityId = "A1",
                    activityType = "Visit",
                    projectName = "Project",
                    companyName = "Company",
                    contactName = null,
                    objective = null,
                    planStatus = "planned",
                    plannedDate = dateInMonth,
                    plannedTime = "09:00",
                    plannedEndTime = "10:00"
                )
            )
        )

        val vm = HomeViewModel(activityRepo, authRepo, customerRepo, projectRepo, callLogRepo)
        advanceUntilIdle()

        vm.selectMonth(month)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.selectedMonth == month)
        assertTrue(vm.uiState.value.groupedCards.isNotEmpty())
    }

    @Test
    fun `deleteActivity should call repository and reload`() = runTest {
        val resultIds = MutableStateFlow(listOf<String>())
        val activitiesFlow = MutableStateFlow(listOf<SalesActivity>())
        val customersFlow = MutableStateFlow(
            listOf(Customer("C1", "Company", null, null, null, null, null, null, null))
        )
        val projectsFlow = MutableStateFlow(
            listOf(Project("P1", "C1", projectName = "Project", projectStatus = "Lead"))
        )
        every { authRepo.currentUser() } returns null
        every { activityRepo.getAllResultIdsFlow() } returns resultIds
        every { activityRepo.getAllActivitiesFlow() } returns activitiesFlow
        every { customerRepo.getAllCustomersFlow() } returns customersFlow
        every { projectRepo.getAllProjectsFlow() } returns projectsFlow
        coEvery { activityRepo.getMyActivitiesWithDetails() } returns Result.success(emptyList())
        coEvery { activityRepo.deleteActivity("A1") } returns Result.success(Unit)

        val vm = HomeViewModel(activityRepo, authRepo, customerRepo, projectRepo, callLogRepo)
        advanceUntilIdle()

        vm.deleteActivity("A1")
        advanceUntilIdle()

        coVerify(exactly = 1) { activityRepo.deleteActivity("A1") }
    }

    @Test
    fun `logout should call auth repository`() = runTest {
        every { authRepo.currentUser() } returns null
        every { activityRepo.getAllActivitiesFlow() } returns flowOf(emptyList())
        every { customerRepo.getAllCustomersFlow() } returns flowOf(emptyList())
        every { projectRepo.getAllProjectsFlow() } returns flowOf(emptyList())
        every { activityRepo.getAllResultIdsFlow() } returns flowOf(emptyList())
        coEvery { activityRepo.getMyActivitiesWithDetails() } returns Result.success(emptyList())
        coEvery { authRepo.logout() } returns Unit

        val vm = HomeViewModel(activityRepo, authRepo, customerRepo, projectRepo, callLogRepo)
        advanceUntilIdle()
        vm.logout()
        advanceUntilIdle()

        coVerify(exactly = 1) { authRepo.logout() }
    }

    @Test
    fun `loadActivities should filter by month and map hasResult correctly`() = runTest {
        val nowMonth = YearMonth.now()
        val currentDate = "${nowMonth}-10"
        val outMonthDate = "${nowMonth.minusMonths(1)}-20"

        every { authRepo.currentUser() } returns null
        every { activityRepo.getAllActivitiesFlow() } returns flowOf(emptyList())
        every { customerRepo.getAllCustomersFlow() } returns flowOf(emptyList())
        every { projectRepo.getAllProjectsFlow() } returns flowOf(emptyList())
        every { activityRepo.getAllResultIdsFlow() } returns flowOf(listOf("A1"))
        coEvery { activityRepo.getMyActivitiesWithDetails() } returns Result.success(
            listOf(
                ActivityCard("A1", "Visit", "P", "C", "N", null, "planned", currentDate, "10:00", "11:00"),
                ActivityCard("A2", "Visit", "P", "C", "N", null, "planned", outMonthDate, "09:00", "10:00"),
                ActivityCard("A3", "Visit", "P", "C", "N", null, "planned", null, "09:00", "10:00"),
                ActivityCard("A4", "Visit", "P", "C", "N", null, "planned", "bad-date", "09:00", "10:00")
            )
        )

        val vm = HomeViewModel(activityRepo, authRepo, customerRepo, projectRepo, callLogRepo)
        advanceUntilIdle()

        val cards = vm.uiState.value.groupedCards.values.flatten()
        assertEquals(1, cards.size)
        assertEquals("A1", cards.first().activityId)
        assertTrue(cards.first().hasResult)
    }

    @Test
    fun `loadActivities should sort cards by date then time`() = runTest {
        val nowMonth = YearMonth.now()
        val d1 = "${nowMonth}-05"
        val d2 = "${nowMonth}-06"

        every { authRepo.currentUser() } returns null
        every { activityRepo.getAllActivitiesFlow() } returns flowOf(emptyList())
        every { customerRepo.getAllCustomersFlow() } returns flowOf(emptyList())
        every { projectRepo.getAllProjectsFlow() } returns flowOf(emptyList())
        every { activityRepo.getAllResultIdsFlow() } returns flowOf(emptyList())
        coEvery { activityRepo.getMyActivitiesWithDetails() } returns Result.success(
            listOf(
                ActivityCard("A3", "Visit", "P", "C", "N", null, "planned", d2, "08:00", "09:00"),
                ActivityCard("A2", "Visit", "P", "C", "N", null, "planned", d1, "10:00", "11:00"),
                ActivityCard("A1", "Visit", "P", "C", "N", null, "planned", d1, "09:00", "10:00")
            )
        )

        val vm = HomeViewModel(activityRepo, authRepo, customerRepo, projectRepo, callLogRepo)
        advanceUntilIdle()

        val idsInOrder = vm.uiState.value.groupedCards.values.flatten().map { it.activityId }
        assertEquals(listOf("A1", "A2", "A3"), idsInOrder)
    }

    @Test
    fun `refreshData with no current user should stop loading and skip remote refresh`() = runTest {
        every { authRepo.currentUser() } returns null
        every { activityRepo.getAllActivitiesFlow() } returns flowOf(emptyList())
        every { customerRepo.getAllCustomersFlow() } returns flowOf(emptyList())
        every { projectRepo.getAllProjectsFlow() } returns flowOf(emptyList())
        every { activityRepo.getAllResultIdsFlow() } returns flowOf(emptyList())
        coEvery { activityRepo.getMyActivitiesWithDetails() } returns Result.success(emptyList())

        val vm = HomeViewModel(activityRepo, authRepo, customerRepo, projectRepo, callLogRepo)
        advanceUntilIdle()

        vm.refreshData()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isLoading)
        coVerify(exactly = 0) { activityRepo.refreshActivities(any()) }
        coVerify(exactly = 0) { customerRepo.refreshCustomers(any()) }
        coVerify(exactly = 0) { projectRepo.refreshProjects(any()) }
    }

    @Test
    fun `init should continue when contact phone map sync throws`() = runTest {
        every { authRepo.currentUser() } returns AuthUser("U1", "u@test.com", "sale")
        coEvery { customerRepo.getAllContactPhoneMap() } throws RuntimeException("no permission")
        coEvery { activityRepo.refreshActivities("U1") } returns Result.success(Unit)
        coEvery { customerRepo.refreshCustomers("U1") } returns Result.success(Unit)
        coEvery { projectRepo.refreshProjects("U1") } returns Result.success(Unit)
        every { activityRepo.getAllActivitiesFlow() } returns flowOf(emptyList())
        every { customerRepo.getAllCustomersFlow() } returns flowOf(emptyList())
        every { projectRepo.getAllProjectsFlow() } returns flowOf(emptyList())
        every { activityRepo.getAllResultIdsFlow() } returns flowOf(emptyList())
        coEvery { activityRepo.getMyActivitiesWithDetails() } returns Result.success(emptyList())

        val vm = HomeViewModel(activityRepo, authRepo, customerRepo, projectRepo, callLogRepo)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isLoading)
        coVerify(exactly = 0) { callLogRepo.syncCallLogs(any()) }
        coVerify(exactly = 1) { activityRepo.refreshActivities("U1") }
    }
}
