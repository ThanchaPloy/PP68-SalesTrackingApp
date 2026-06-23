package com.example.pp68_salestrackingapp.ui.viewmodels.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pp68_salestrackingapp.data.model.AuthUser
import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LoginUiState {
    object Idle : LoginUiState
    object Loading : LoginUiState
    data class Error(val message: String) : LoginUiState
    data class Success(val user: AuthUser) : LoginUiState
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _username = MutableStateFlow("")
    val username: StateFlow<String> = _username.asStateFlow()

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    fun onUsernameChange(newValue: String) {
        _username.value = newValue
        clearError()
    }

    fun onPasswordChange(newValue: String) {
        _password.value = newValue
        clearError()
    }

    fun login() {
        val currentUsername = _username.value.trim()
        val currentPassword = _password.value

        if (currentUsername.isBlank() || currentPassword.isBlank()) {
            _uiState.value = LoginUiState.Error("กรุณากรอกข้อมูลให้ครบถ้วน")
            return
        }

        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            val result = authRepository.login(currentUsername, currentPassword)
            result.onSuccess { response ->
                _uiState.value = LoginUiState.Success(
                    AuthUser(userId = response.userId, email = currentUsername, role = response.role)
                )
            }
            result.onFailure { exception ->
                _uiState.value = LoginUiState.Error(exception.message ?: "เกิดข้อผิดพลาดในการเข้าสู่ระบบ")
            }
        }
    }

    fun resetState() {
        _uiState.value = LoginUiState.Idle
        _username.value = ""
        _password.value = ""
    }

    private fun clearError() {
        if (_uiState.value is LoginUiState.Error) _uiState.value = LoginUiState.Idle
    }
}
