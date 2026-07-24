package com.example.pp68_salestrackingapp.ui.viewmodels.activity

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.pp68_salestrackingapp.di.TokenManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationSettingsViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private val tokenManager = mockk<TokenManager>(relaxed = true)
    private lateinit var viewModel: NotificationSettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // TC-UNIT-VM-NOTISET-01
    @Test
    fun `init should load push enabled and visit reminder from TokenManager`() {
        every { tokenManager.isPushEnabled() } returns true
        every { tokenManager.isVisitReminderEnabled() } returns false
        viewModel = NotificationSettingsViewModel(tokenManager)

        assertTrue(viewModel.uiState.value.pushEnabled)
        assertFalse(viewModel.uiState.value.visitReminder)
    }

    // TC-UNIT-VM-NOTISET-02
    @Test
    fun `init with both preferences disabled should reflect in state`() {
        every { tokenManager.isPushEnabled() } returns false
        every { tokenManager.isVisitReminderEnabled() } returns false
        viewModel = NotificationSettingsViewModel(tokenManager)

        assertFalse(viewModel.uiState.value.pushEnabled)
        assertFalse(viewModel.uiState.value.visitReminder)
    }

    // TC-UNIT-VM-NOTISET-03
    @Test
    fun `init with both preferences enabled should reflect in state`() {
        every { tokenManager.isPushEnabled() } returns true
        every { tokenManager.isVisitReminderEnabled() } returns true
        viewModel = NotificationSettingsViewModel(tokenManager)

        assertTrue(viewModel.uiState.value.pushEnabled)
        assertTrue(viewModel.uiState.value.visitReminder)
    }

    // TC-UNIT-VM-NOTISET-04
    @Test
    fun `onPushEnabledChange to false should save and update state`() {
        every { tokenManager.isPushEnabled() } returns true
        every { tokenManager.isVisitReminderEnabled() } returns true
        viewModel = NotificationSettingsViewModel(tokenManager)

        viewModel.onPushEnabledChange(false)

        assertFalse(viewModel.uiState.value.pushEnabled)
        verify { tokenManager.savePushEnabled(false) }
    }

    // TC-UNIT-VM-NOTISET-05
    @Test
    fun `onPushEnabledChange to true should save and update state`() {
        every { tokenManager.isPushEnabled() } returns false
        every { tokenManager.isVisitReminderEnabled() } returns true
        viewModel = NotificationSettingsViewModel(tokenManager)

        viewModel.onPushEnabledChange(true)

        assertTrue(viewModel.uiState.value.pushEnabled)
        verify { tokenManager.savePushEnabled(true) }
    }

    // TC-UNIT-VM-NOTISET-06
    @Test
    fun `onVisitReminderChange to false should save and update state`() {
        every { tokenManager.isPushEnabled() } returns true
        every { tokenManager.isVisitReminderEnabled() } returns true
        viewModel = NotificationSettingsViewModel(tokenManager)

        viewModel.onVisitReminderChange(false)

        assertFalse(viewModel.uiState.value.visitReminder)
        verify { tokenManager.saveVisitReminderEnabled(false) }
    }

    // TC-UNIT-VM-NOTISET-07
    @Test
    fun `onVisitReminderChange to true should save and update state`() {
        every { tokenManager.isPushEnabled() } returns true
        every { tokenManager.isVisitReminderEnabled() } returns false
        viewModel = NotificationSettingsViewModel(tokenManager)

        viewModel.onVisitReminderChange(true)

        assertTrue(viewModel.uiState.value.visitReminder)
        verify { tokenManager.saveVisitReminderEnabled(true) }
    }

    // TC-UNIT-VM-NOTISET-08
    @Test
    fun `multiple preference changes should each be saved independently`() {
        every { tokenManager.isPushEnabled() } returns true
        every { tokenManager.isVisitReminderEnabled() } returns true
        viewModel = NotificationSettingsViewModel(tokenManager)

        viewModel.onPushEnabledChange(false)
        viewModel.onVisitReminderChange(false)

        assertFalse(viewModel.uiState.value.pushEnabled)
        assertFalse(viewModel.uiState.value.visitReminder)
        verify { tokenManager.savePushEnabled(false) }
        verify { tokenManager.saveVisitReminderEnabled(false) }
    }
}
