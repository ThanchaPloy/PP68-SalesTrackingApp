package com.example.pp68_salestrackingapp.ui.viewmodels.activity

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.example.pp68_salestrackingapp.data.model.AuthUser
import com.example.pp68_salestrackingapp.data.model.SalesActivity
import com.example.pp68_salestrackingapp.data.repository.ActivityRepository
import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ActivityViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private val activityRepo = mockk<ActivityRepository>(relaxed = true)
    private val authRepo     = mockk<AuthRepository>(relaxed = true)
    private lateinit var viewModel: ActivityViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { authRepo.currentUser() } returns
                AuthUser(userId = "USR-001", email = "test@test.com", role = "sale")
        every { activityRepo.getAllActivitiesFlow() } returns flowOf(emptyList())
        coEvery { activityRepo.refreshActivities(any()) } returns Result.success(Unit)
    }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `init should call refreshDataFromApi`() = runTest {
        viewModel = ActivityViewModel(activityRepo, authRepo)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { activityRepo.refreshActivities("USR-001") }
    }

    @Test
    fun `refreshDataFromApi success should set isLoading false`() = runTest {
        viewModel = ActivityViewModel(activityRepo, authRepo)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.isLoading.value)
    }

    @Test
    fun `refreshDataFromApi failure should set error`() = runTest {
        coEvery { activityRepo.refreshActivities(any()) } returns
                Result.failure(Exception("Network error"))

        viewModel = ActivityViewModel(activityRepo, authRepo)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Network error", viewModel.error.value)
    }

    @Test
    fun `clearError should set error to null`() = runTest {
        coEvery { activityRepo.refreshActivities(any()) } returns
                Result.failure(Exception("error"))
        viewModel = ActivityViewModel(activityRepo, authRepo)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.clearError()

        assertNull(viewModel.error.value)
    }

    @Test
    fun `no userId should not call refreshActivities`() = runTest {
        every { authRepo.currentUser() } returns null

        viewModel = ActivityViewModel(activityRepo, authRepo)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { activityRepo.refreshActivities(any()) }
    }

    @Test
    fun `activities flow should have correct size after init`() = runTest {
        val sampleActivities = listOf(
            SalesActivity(activityId = "APT-001", userId = "USR-001",
                customerId = "CST-001", activityType = "onsite",
                activityDate = "2026-03-28", status = "planned")
        )
        // กำหนดให้ repository คืนค่า list ที่มีข้อมูล
        every { activityRepo.getAllActivitiesFlow() } returns flowOf(sampleActivities)

        val vm = ActivityViewModel(activityRepo, authRepo)

        // ✅ ใช้ Turbine ในการทดสอบ StateFlow ที่เป็น WhileSubscribed
        vm.activities.test {
            // ตัวแรกจะเป็น initialValue (emptyList)
            assertEquals(emptyList<SalesActivity>(), awaitItem())
            
            // ตัวถัดมาจะเป็นข้อมูลจาก repository flow
            val result = awaitItem()
            assertTrue(result.isNotEmpty())
            assertEquals("APT-001", result[0].activityId)
        }
    }
}
