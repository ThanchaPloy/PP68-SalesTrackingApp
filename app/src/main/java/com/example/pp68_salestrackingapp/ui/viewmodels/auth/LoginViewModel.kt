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

// 1. สร้าง Sealed Interface สำหรับจัดการ State ของหน้าจอ (LoginScreen ถามหาตัวนี้อยู่)
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

    // 2. รวม State ตามที่ LoginScreen.kt คาดหวัง
    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _email = MutableStateFlow("")
    val email: StateFlow<String> = _email.asStateFlow()

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    init {
        // เช็คว่าเคยล็อกอินไว้แล้วหรือยัง
        // หมายเหตุ: ตรงนี้ต้องมั่นใจว่า AuthRepository สามารถดึงข้อมูล User ปัจจุบันมาให้เราได้
        if (authRepository.isUserLoggedIn()) {
            // สมมติว่ามีฟังก์ชัน getCurrentUser()
            // val currentUser = authRepository.getCurrentUser()
            // if (currentUser != null) {
            //    _uiState.value = LoginUiState.Success(currentUser)
            // }
        }
    }

    // 3. ฟังก์ชันอัปเดตช่องกรอกข้อมูล
    fun onEmailChange(newValue: String) {
        _email.value = newValue
        clearError()
    }

    fun onPasswordChange(newValue: String) {
        _password.value = newValue
        clearError()
    }

    // 4. ฟังก์ชัน login ดึงค่าจาก StateFlow โดยตรง (หน้าจอจะได้ไม่ต้องส่ง Parameter มา)
    fun login() {
        val currentEmail = _email.value
        val currentPassword = _password.value

        if (currentEmail.isBlank() || currentPassword.isBlank()) {
            _uiState.value = LoginUiState.Error("กรุณากรอกข้อมูลให้ครบถ้วน")
            return
        }

        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading

            // ส่งข้อมูลไปที่ Repository
            val result = authRepository.login(currentEmail.trim(), currentPassword.trim())

            result.onSuccess { response ->
                // สร้าง AuthUser จากข้อมูลที่ได้จาก API และ Email ที่ผู้ใช้กรอก
                val authUser = AuthUser(
                    userId = response.userId,
                    email = currentEmail,
                    role = response.role,
                    teamId = "" // ถ้า API ยังไม่ส่ง teamId มาให้ใส่ค่าว่างไปก่อนครับ
                )

                // ส่ง AuthUser เข้าไปให้ UI
                _uiState.value = LoginUiState.Success(authUser)
            }
            result.onFailure { exception ->
                _uiState.value = LoginUiState.Error(exception.message ?: "เกิดข้อผิดพลาดในการเข้าสู่ระบบ")
            }
        }
    }

    // 5. ฟังก์ชันล้างค่ากลับเป็นค่าเริ่มต้น
    fun resetState() {
        _uiState.value = LoginUiState.Idle
        _email.value = ""
        _password.value = ""
    }

    private fun clearError() {
        if (_uiState.value is LoginUiState.Error) {
            _uiState.value = LoginUiState.Idle
        }
    }
}
