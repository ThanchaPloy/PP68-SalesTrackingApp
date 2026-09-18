package com.example.pp68_salestrackingapp.ui.viewmodels.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pp68_salestrackingapp.data.model.AuthUser
import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val user: AuthUser? = null,
    val isLoggingOut: Boolean = false,
    val isLoggedOut: Boolean = false,
    val logoutError: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepo: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadUser()
    }

    private fun loadUser() {
        val currentUser = authRepo.currentUser()
        _uiState.update { it.copy(user = currentUser) }
    }

    fun refreshUser() {
        loadUser()
    }

    fun logout() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoggingOut = true, logoutError = null) }
            authRepo.logout().fold(
                onSuccess = { _uiState.update { it.copy(isLoggingOut = false, isLoggedOut = true) } },
                onFailure = { e -> _uiState.update { it.copy(isLoggingOut = false, logoutError = e.message) } }
            )
        }
    }

    fun dismissLogoutError() {
        _uiState.update { it.copy(logoutError = null) }
    }
}
