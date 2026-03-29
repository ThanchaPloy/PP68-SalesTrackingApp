package com.example.pp68_salestrackingapp.ui.viewmodels.activity

import androidx.lifecycle.ViewModel
import com.example.pp68_salestrackingapp.di.TokenManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class NotificationSettingsUiState(
    val pushEnabled:    Boolean = true,
    val visitReminder:  Boolean = true
)

@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationSettingsUiState())
    val uiState: StateFlow<NotificationSettingsUiState> = _uiState

    init { loadPreferences() }

    private fun loadPreferences() {
        _uiState.update {
            it.copy(
                pushEnabled   = tokenManager.isPushEnabled(),
                visitReminder = tokenManager.isVisitReminderEnabled()
            )
        }
    }

    fun onPushEnabledChange(enabled: Boolean) {
        tokenManager.savePushEnabled(enabled)
        _uiState.update { it.copy(pushEnabled = enabled) }
    }

    fun onVisitReminderChange(enabled: Boolean) {
        tokenManager.saveVisitReminderEnabled(enabled)
        _uiState.update { it.copy(visitReminder = enabled) }
    }
}