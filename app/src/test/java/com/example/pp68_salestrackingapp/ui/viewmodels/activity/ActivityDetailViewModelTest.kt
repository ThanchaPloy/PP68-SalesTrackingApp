package com.example.pp68_salestrackingapp.ui.viewmodels.activity

import com.example.pp68_salestrackingapp.data.model.PlanItemDto
import com.example.pp68_salestrackingapp.data.model.SalesActivity
import com.example.pp68_salestrackingapp.data.repository.ActivityRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ActivityDetailViewModelTest {

    private lateinit var viewModel: ActivityDetailViewModel
    private val repository = mockk<ActivityRepository>(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        coEvery { repository.enrichActivity(any()) } answers { firstArg() }
        coEvery { repository.getPlanItems(any()) } returns Result.success(emptyList())
        coEvery { repository.getActivityById(any()) } returns Result.success(emptyList())
        coEvery { repository.finishActivity(any(), any(), any()) } returns Result.success(Unit)
        coEvery { repository.checkIn(any(), any(), any(), any()) } returns Result.success(Unit)
        viewModel = ActivityDetailViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `finishActivity should set isFinished true on success`() = runTest {
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

        viewModel.loadActivity(activityId)
        advanceUntilIdle()
        viewModel.finishActivity()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isFinished)
        assertFalse(viewModel.uiState.value.isFinishing)
    }

    @Test
    fun `loadActivity should format date and time and derive selected items`() = runTest {
        val activityId = "ACT-02"
        val act = SalesActivity(
            activityId = activityId,
            userId = "USR-01",
            customerId = "CUST-01",
            activityType = "Visit",
            activityDate = "2026-04-10T09:00:00.000Z",
            plannedTime = "14:30:00",
            plannedEndTime = "15:45",
            status = "Planned"
        )
        coEvery { repository.getActivityById(activityId) } returns Result.success(listOf(act))
        coEvery { repository.enrichActivity(act) } returns act
        coEvery { repository.getPlanItems(activityId) } returns Result.success(
            listOf(PlanItemDto(masterId = 10, isDone = true), PlanItemDto(masterId = 11, isDone = false))
        )

        viewModel.loadActivity(activityId)
        advanceUntilIdle()

        val loaded = viewModel.uiState.value
        assertEquals("Apr 10, 2026", loaded.activity?.activityDate)
        assertEquals("02:30 PM", loaded.activity?.plannedTime)
        assertEquals("03:45 PM", loaded.activity?.plannedEndTime)
        assertEquals(setOf(10), loaded.selectedItemIds)
    }

    @Test
    fun `loadActivity failure should expose error message`() = runTest {
        coEvery { repository.getActivityById("BAD") } returns Result.failure(Exception("no data"))
        coEvery { repository.getPlanItems("BAD") } returns Result.success(emptyList())

        viewModel.loadActivity("BAD")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.error?.contains("no data") == true)
    }

    @Test
    fun `updateCurrentLocation should set mismatch when distance over threshold`() = runTest {
        val activityId = "ACT-03"
        val act = SalesActivity(
            activityId = activityId,
            userId = "USR-01",
            customerId = "CUST-01",
            activityType = "Visit",
            activityDate = "2026-04-10",
            plannedLat = 13.7563,
            plannedLong = 100.5018,
            status = "Planned"
        )
        coEvery { repository.getActivityById(activityId) } returns Result.success(listOf(act))

        viewModel.loadActivity(activityId)
        advanceUntilIdle()
        viewModel.updateCurrentLocation(13.7563, 100.5018)
        assertFalse(viewModel.uiState.value.isLocationMismatch)

        viewModel.updateCurrentLocation(13.7663, 100.5118)
        assertTrue(viewModel.uiState.value.isLocationMismatch)
        assertTrue(viewModel.uiState.value.currentDistance > 200.0)
    }

    @Test
    fun `confirmCheckin success should refresh activity and close dialog`() = runTest {
        val activityId = "ACT-04"
        val act = SalesActivity(
            activityId = activityId,
            userId = "USR-01",
            customerId = "CUST-01",
            activityType = "Visit",
            activityDate = "2026-04-10",
            status = "Planned"
        )
        coEvery { repository.getActivityById(activityId) } returnsMany listOf(
            Result.success(listOf(act)),
            Result.success(listOf(act.copy(status = "checked_in")))
        )
        viewModel.loadActivity(activityId)
        advanceUntilIdle()
        viewModel.setShowCheckinDialog(true)

        viewModel.confirmCheckin(13.7, 100.5)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showCheckinDialog)
        assertFalse(viewModel.uiState.value.isCheckingIn)
        coVerify(atLeast = 1) { repository.checkIn(activityId, 13.7, 100.5, any()) }
    }

    @Test
    fun `confirmCheckin failure should set error`() = runTest {
        val activityId = "ACT-05"
        val act = SalesActivity(
            activityId = activityId,
            userId = "USR-01",
            customerId = "CUST-01",
            activityType = "Visit",
            activityDate = "2026-04-10",
            status = "Planned"
        )
        coEvery { repository.getActivityById(activityId) } returns Result.success(listOf(act))
        coEvery { repository.checkIn(any(), any(), any(), any()) } returns Result.failure(Exception("gps fail"))
        viewModel.loadActivity(activityId)
        advanceUntilIdle()

        viewModel.confirmCheckin(13.7, 100.5)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.error?.contains("gps fail") == true)
        assertFalse(viewModel.uiState.value.isCheckingIn)
    }

    @Test
    fun `toggleItem should update selected ids and persist checklist state`() = runTest {
        val activityId = "ACT-06"
        val act = SalesActivity(
            activityId = activityId,
            userId = "USR-01",
            customerId = "CUST-01",
            activityType = "Visit",
            activityDate = "2026-04-10",
            status = "Planned"
        )
        coEvery { repository.getActivityById(activityId) } returns Result.success(listOf(act))
        viewModel.loadActivity(activityId)
        advanceUntilIdle()

        viewModel.toggleItem(100)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.selectedItemIds.contains(100))
        coVerify { repository.updatePlanItemStatus(activityId, 100, true) }
        coVerify { repository.updateChecklistItem(activityId, 100, true) }

        viewModel.toggleItem(100)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.selectedItemIds.contains(100))
        coVerify { repository.updatePlanItemStatus(activityId, 100, false) }
        coVerify { repository.updateChecklistItem(activityId, 100, false) }
    }

    @Test
    fun `finishActivity failure should set error`() = runTest {
        val activityId = "ACT-07"
        val act = SalesActivity(
            activityId = activityId,
            userId = "USR-01",
            customerId = "CUST-01",
            activityType = "Visit",
            activityDate = "2026-04-10",
            status = "Planned"
        )
        coEvery { repository.getActivityById(activityId) } returns Result.success(listOf(act))
        coEvery { repository.finishActivity(any(), any(), any()) } returns Result.failure(Exception("api failed"))
        viewModel.loadActivity(activityId)
        advanceUntilIdle()

        viewModel.finishActivity()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isFinished)
        assertTrue(viewModel.uiState.value.error?.contains("api failed") == true)
    }

    @Test
    fun `clearError should remove error state`() = runTest {
        val activityId = "ACT-08"
        val act = SalesActivity(
            activityId = activityId,
            userId = "USR-01",
            customerId = "CUST-01",
            activityType = "Visit",
            activityDate = "2026-04-10",
            status = "Planned"
        )
        coEvery { repository.getActivityById(activityId) } returns Result.success(listOf(act))
        coEvery { repository.finishActivity(any(), any(), any()) } returns Result.failure(Exception("x"))
        viewModel.loadActivity(activityId)
        advanceUntilIdle()
        viewModel.finishActivity()
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.error)

        viewModel.clearError()
        assertNull(viewModel.uiState.value.error)
    }
}
