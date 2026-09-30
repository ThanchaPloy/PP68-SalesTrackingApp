// สร้างไฟล์ใหม่ EditProfileViewModel.kt
package com.example.pp68_salestrackingapp.ui.viewmodels.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pp68_salestrackingapp.data.remote.ApiService
import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditProfileUiState(
    val fullName:    String  = "",
    val empCode:     String  = "",
    val email:       String  = "",
    val phoneNumber: String  = "",
    val branchName:  String  = "",
    val isLoading:   Boolean = false,
    val isSaved:     Boolean = false,
    val error:       String? = null
)

@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val authRepo:   AuthRepository,
    private val apiService: ApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditProfileUiState())
    val uiState: StateFlow<EditProfileUiState> = _uiState

    init { loadProfile() }

    private fun loadProfile() {
        viewModelScope.launch {
            val user = authRepo.currentUser() ?: return@launch
            _uiState.update {
                it.copy(
                    fullName   = user.fullName   ?: "",
                    empCode    = user.userId,
                    branchName = user.branchName ?: ""
                )
            }
            // ดึง phone_number/email จริงจาก API (AuthUser.email เป็นแค่รหัสพนักงานที่ใช้ล็อกอิน ไม่ใช่อีเมลจริง)
            try {
                val resp = apiService.getUserById("eq.${user.userId}")
                // ✅ ต้องเช็ค isSuccessful ก่อน — .body() คืน null เวลา server ตอบ 4xx/5xx ด้วย
                // เดิมจึงล้างช่องเบอร์โทร/อีเมลให้ว่างทุกครั้งที่เซิร์ฟเวอร์สะดุด ผู้ใช้เปิดหน้ามา
                // เห็นช่องว่างแล้วเข้าใจว่าตัวเองไม่เคยกรอกไว้ (ตอนกดบันทึกไม่ถึงกับลบของจริง
                // เพราะ save() ข้ามฟิลด์ที่ว่าง แต่ก็ทำให้เข้าใจผิดและกรอกซ้ำเปล่า ๆ)
                if (resp.isSuccessful) {
                    val userDto = resp.body()?.firstOrNull()
                    _uiState.update {
                        it.copy(
                            phoneNumber = userDto?.phoneNumber ?: "",
                            email       = userDto?.email ?: ""
                        )
                    }
                }
            } catch (e: Exception) { }
        }
    }

    fun onFullNameChange(v: String)    = _uiState.update { it.copy(fullName = v) }
    fun onPhoneChange(v: String)       = _uiState.update { it.copy(phoneNumber = v) }
    fun onEmailChange(v: String)       = _uiState.update { it.copy(email = v) }

    fun resetSavedState() {
        _uiState.update { it.copy(isSaved = false) }
    }

    fun save() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val user = authRepo.currentUser()
                if (user == null) {
                    // เดิมจบเงียบ — สปินเนอร์หยุดแล้วไม่มีอะไรเกิดขึ้น ผู้ใช้ไม่รู้ว่าต้องทำอะไรต่อ
                    _uiState.update {
                        it.copy(isLoading = false, error = "ไม่พบข้อมูลผู้ใช้ กรุณาเข้าสู่ระบบใหม่")
                    }
                    return@launch
                }
                val userId = user.userId

                // ⚠️ ต้องใช้ชื่อ column จริงของตาราง employee ("emp_name") ไม่ใช่ "full_name"
                val updates = mutableMapOf<String, String>(
                    "emp_name" to _uiState.value.fullName.trim()
                )
                if (_uiState.value.phoneNumber.isNotBlank()) {
                    updates["phone_number"] = _uiState.value.phoneNumber.trim()
                }
                if (_uiState.value.email.isNotBlank()) {
                    updates["email"] = _uiState.value.email.trim()
                }

                val response = apiService.updateUserProfile("eq.$userId", updates)

                if (response.isSuccessful) {
                    // ✅ อัปเดต TokenManager ด้วยเพื่อให้ Profile overlay แสดงข้อมูลใหม่
                    val updatedUser = user.copy(
                        fullName = _uiState.value.fullName.trim()
                    )
                    authRepo.updateLocalUser(updatedUser)
                    _uiState.update { it.copy(isLoading = false, isSaved = true) }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error     = "บันทึกไม่สำเร็จ: HTTP ${response.code()}"
                        )
                    }
                }
            } catch (e: java.io.IOException) {
                // หน้านี้ไม่ได้เก็บลง Room และไม่มี outbox — ออฟไลน์คือบันทึกไม่ได้จริง
                // ต้องบอกด้วยภาษาคน ไม่ใช่โยนข้อความ exception ดิบ ๆ ให้ผู้ใช้อ่านเอง
                _uiState.update {
                    it.copy(isLoading = false, error = "บันทึกไม่สำเร็จ: เชื่อมต่อเซิร์ฟเวอร์ไม่ได้ กรุณาลองใหม่เมื่อมีสัญญาณ")
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "บันทึกไม่สำเร็จ") }
            }
        }
    }
}
