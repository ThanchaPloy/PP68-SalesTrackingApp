package com.example.pp68_salestrackingapp.ui.viewmodels.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InitialAccountSetupUiState(
    val currentPassword: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val phoneNumber: String = "",
    val phoneRequired: Boolean = false,
    val isLoading: Boolean = false,
    val isCompleted: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class InitialAccountSetupViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {
    private val setupInfo = authRepository.initialSetupInfo()
    private val _uiState = MutableStateFlow(
        InitialAccountSetupUiState(phoneRequired = setupInfo.phoneRequired)
    )
    val uiState: StateFlow<InitialAccountSetupUiState> = _uiState.asStateFlow()

    fun onCurrentPasswordChange(value: String) = updateSecret { copy(currentPassword = value) }
    fun onNewPasswordChange(value: String) = updateSecret { copy(newPassword = value) }
    fun onConfirmPasswordChange(value: String) = updateSecret { copy(confirmPassword = value) }
    fun onPhoneNumberChange(value: String) {
        val filtered = value.filter { it.isDigit() || it == '+' || it == '-' || it == ' ' || it == '(' || it == ')' }
        _uiState.update { it.copy(phoneNumber = filtered.take(20), error = null) }
    }

    private fun updateSecret(block: InitialAccountSetupUiState.() -> InitialAccountSetupUiState) {
        _uiState.update { it.block().copy(error = null) }
    }

    fun submit() {
        val state = _uiState.value
        if (!setupInfo.required) {
            _uiState.update { it.copy(error = "กรุณาเข้าสู่ระบบใหม่") }
            return
        }
        if (state.currentPassword.isBlank() || state.newPassword.isEmpty() || state.confirmPassword.isEmpty()) {
            _uiState.update { it.copy(error = "กรุณากรอกรหัสผ่านให้ครบ") }
            return
        }
        if (state.newPassword.codePointCount(0, state.newPassword.length) < 8) {
            _uiState.update { it.copy(error = "รหัสผ่านใหม่ต้องมีอย่างน้อย 8 ตัวอักษร") }
            return
        }
        if (state.newPassword.isBlank()) {
            _uiState.update { it.copy(error = "รหัสผ่านใหม่ต้องไม่เป็นช่องว่างทั้งหมด") }
            return
        }
        if (state.newPassword.toByteArray(Charsets.UTF_8).size > 72) {
            _uiState.update { it.copy(error = "รหัสผ่านใหม่ยาวเกินขอบเขตที่ระบบรองรับ") }
            return
        }
        if (state.newPassword == state.currentPassword) {
            _uiState.update { it.copy(error = "รหัสผ่านใหม่ต้องไม่ซ้ำกับรหัสผ่านปัจจุบัน") }
            return
        }
        if (state.newPassword != state.confirmPassword) {
            _uiState.update { it.copy(error = "รหัสผ่านใหม่ไม่ตรงกัน") }
            return
        }
        if (state.phoneRequired && !isValidThaiMobile(state.phoneNumber)) {
            _uiState.update { it.copy(error = "กรุณากรอกเบอร์มือถือไทยให้ถูกต้อง") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            authRepository.completeInitialSetup(
                currentPassword = state.currentPassword,
                newPassword = state.newPassword,
                phoneNumber = state.phoneNumber
            ).fold(
                onSuccess = {
                    _uiState.value = InitialAccountSetupUiState(
                        phoneRequired = state.phoneRequired,
                        isCompleted = true
                    )
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            currentPassword = "",
                            newPassword = "",
                            confirmPassword = "",
                            isLoading = false,
                            error = error.message ?: "ตั้งค่าบัญชีไม่สำเร็จ"
                        )
                    }
                }
            )
        }
    }

    fun logout() = authRepository.abandonInitialSetup()

    private fun isValidThaiMobile(value: String): Boolean {
        val compact = value.filterNot { it.isWhitespace() || it == '-' || it == '(' || it == ')' }
        val normalized = when {
            compact.startsWith("+66") -> "0" + compact.removePrefix("+66")
            compact.startsWith("0066") -> "0" + compact.removePrefix("0066")
            else -> compact
        }
        return Regex("^0[689][0-9]{8}$").matches(normalized)
    }
}
