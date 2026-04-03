package com.example.pp68_salestrackingapp.ui.viewmodels.activity

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.example.pp68_salestrackingapp.data.model.SalesActivity
import com.example.pp68_salestrackingapp.data.repository.ActivityRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ActivityDetailViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var viewModel: ActivityDetailViewModel
    private val repository = mockk<ActivityRepository>(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = ActivityDetailViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `TC-UNIT-VM-ACT-01 finishActivity should set isFinished true on success`() = runTest {
        // Arrange
        val activityId = "ACT-01"
        val act = SalesActivity(
            activityId = activityId,
            userId = "USR-01",
            customerId = "CUST-01",
            activityType = "Visit",
            activityDate = "2023-10-27",
            status = "Planned"
        )
        coEvery { repository.getActivityById(activityId) } returns Result.success(listOf(act))
        coEvery { repository.getPlanItems(activityId) } returns Result.success(emptyList())
        coEvery { repository.finishActivity(any(), any(), any()) } returns Result.success(Unit)

        viewModel.uiState.test {
            // 1. Initial State
            assertEquals(false, awaitItem().isLoading)

            // 2. Load Activity
            viewModel.loadActivity(activityId)
            
            // Skip loading state
            val loadingState = awaitItem()
            assertTrue(loadingState.isLoading)
            
            // Loaded state
            val loadedState = awaitItem()
            assertFalse(loadedState.isLoading)
            assertEquals(activityId, loadedState.activity?.activityId)

            // 3. Act: Finish Activity
            viewModel.finishActivity()

            // State changes to isFinishing = true
            val finishingState = awaitItem()
            assertTrue(finishingState.isFinishing)

            // State changes to isFinished = true
            val finishedState = awaitItem()
            assertFalse(finishedState.isFinishing)
            assertTrue(finishedState.isFinished)
        }
    }

    @Test
    fun `TC-UNIT-VM-ACT-02 toggleItem should update selectedItemIds`() = runTest {
        viewModel.uiState.test {
            // Initial empty state
            awaitItem() 

            // Act: toggle item ID = 100
            viewModel.toggleItem(100)

            // State changes to contain 100
            val stateWithItem = awaitItem()
            assertTrue(stateWithItem.selectedItemIds.contains(100))

            // Act: toggle item ID = 100 again
            viewModel.toggleItem(100)

            // State changes to remove 100
            val stateWithoutItem = awaitItem()
            assertFalse(stateWithoutItem.selectedItemIds.contains(100))
        }
    }
}
