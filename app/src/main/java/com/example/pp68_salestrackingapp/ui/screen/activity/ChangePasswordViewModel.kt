package com.example.pp68_salestrackingapp.ui.viewmodels.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChangePasswordUiState(
    val oldPassword:     String  = "",
    val newPassword:     String  = "",
    val confirmPassword: String  = "",
    val isLoading:       Boolean = false,
    val isSuccess:       Boolean = false,
    val error:           String? = null
)

@HiltViewModel
class ChangePasswordViewModel @Inject constructor(
    private val authRepo: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChangePasswordUiState())
    val uiState: StateFlow<ChangePasswordUiState> = _uiState

    fun onOldPasswordChange(v: String) = _uiState.update { it.copy(oldPassword = v, error = null) }
    fun onNewPasswordChange(v: String) = _uiState.update { it.copy(newPassword = v, error = null) }
    fun onConfirmPasswordChange(v: String) = _uiState.update { it.copy(confirmPassword = v, error = null) }

    fun save() {
        val s = _uiState.value

        if (s.oldPassword.isBlank() || s.newPassword.isBlank() || s.confirmPassword.isBlank()) {
            _uiState.update { it.copy(error = "กรุณากรอกข้อมูลให้ครบ") }
            return
        }
        if (s.newPassword.length < 6) {
            _uiState.update { it.copy(error = "รหัสผ่านใหม่ต้องมีอย่างน้อย 6 ตัวอักษร") }
            return
        }
        if (s.newPassword != s.confirmPassword) {
            _uiState.update { it.copy(error = "รหัสผ่านใหม่ไม่ตรงกัน") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            authRepo.changePassword(s.oldPassword, s.newPassword)
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false, isSuccess = true) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }
}