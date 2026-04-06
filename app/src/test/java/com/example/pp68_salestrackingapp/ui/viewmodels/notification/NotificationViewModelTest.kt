package com.example.pp68_salestrackingapp.ui.viewmodels.notification

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.pp68_salestrackingapp.data.repository.ActivityRepository
import com.example.pp68_salestrackingapp.ui.viewmodels.activity.ActivityCard
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val dispatcher = StandardTestDispatcher()
    private val activityRepo = mockk<ActivityRepository>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadNotifications should create reminders and weekly notification`() = runTest {
        val today = LocalDate.now().toString()
        val tomorrow = LocalDate.now().plusDays(1).toString()
        coEvery { activityRepo.getMyActivitiesWithDetails() } returns Result.success(
            listOf(
                ActivityCard(
                    activityId = "A1",
                    activityType = "Visit",
                    projectName = "Project A",
                    companyName = "Company A",
                    contactName = "Contact A",
                    objective = "Obj A",
                    planStatus = "planned",
                    plannedDate = today,
                    plannedTime = "09:00",
                    plannedEndTime = "10:00"
                ),
                ActivityCard(
                    activityId = "A2",
                    activityType = "Call",
                    projectName = "Project B",
                    companyName = "Company B",
                    contactName = "Contact B",
                    objective = "Obj B",
                    planStatus = "checked_in",
                    plannedDate = tomorrow,
                    plannedTime = "11:00",
                    plannedEndTime = "12:00"
                )
            )
        )

        val vm = NotificationViewModel(activityRepo)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isLoading)
        assertTrue(vm.uiState.value.notifications.isNotEmpty())
        assertTrue(vm.uiState.value.notifications.any { it.id == "A1" || it.id == "A2" })
        assertTrue(vm.uiState.value.notifications.any { it.id == "weekly_report" })
    }

    @Test
    fun `loadNotifications failure should set error`() = runTest {
        coEvery { activityRepo.getMyActivitiesWithDetails() } returns Result.failure(Exception("fetch fail"))

        val vm = NotificationViewModel(activityRepo)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isLoading)
        assertTrue(vm.uiState.value.error == "fetch fail")
    }

    @Test
    fun `loadNotifications should ignore invalid date and map checked in to report action`() = runTest {
        val today = LocalDate.now().toString()
        coEvery { activityRepo.getMyActivitiesWithDetails() } returns Result.success(
            listOf(
                ActivityCard(
                    activityId = "A1",
                    activityType = "Visit",
                    projectName = "Project A",
                    companyName = "Company A",
                    contactName = null,
                    objective = "Obj A",
                    planStatus = "planned",
                    plannedDate = "invalid-date",
                    plannedTime = "xx:yy",
                    plannedEndTime = null
                ),
                ActivityCard(
                    activityId = "A2",
                    activityType = "Call",
                    projectName = "Project B",
                    companyName = "Company B",
                    contactName = null,
                    objective = "Obj B",
                    planStatus = "checked_in",
                    plannedDate = today,
                    plannedTime = "09:61",
                    plannedEndTime = null
                ),
                ActivityCard(
                    activityId = "A3",
                    activityType = "Visit",
                    projectName = "Project C",
                    companyName = "Company C",
                    contactName = null,
                    objective = "Obj C",
                    planStatus = "completed",
                    plannedDate = today,
                    plannedTime = "09:00",
                    plannedEndTime = null
                )
            )
        )

        val vm = NotificationViewModel(activityRepo)
        advanceUntilIdle()

        val notis = vm.uiState.value.notifications
        assertFalse(vm.uiState.value.isLoading)
        assertTrue(notis.none { it.id == "A1" })
        assertTrue(notis.any { it.id == "A2" && it.action.name == "REPORT" })
        assertTrue(notis.none { it.id == "A3" })
    }
}
