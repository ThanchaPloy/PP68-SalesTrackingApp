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
                    email      = user.email,
                    branchName = user.branchName ?: ""
                )
            }
            // ดึง phone_number จาก API
            try {
                val resp = apiService.getUserById("eq.${user.userId}")
                val userDto = resp.body()?.firstOrNull()
                _uiState.update {
                    it.copy(phoneNumber = userDto?.phoneNumber ?: "")
                }
            } catch (e: Exception) { }
        }
    }

    fun onFullNameChange(v: String)    = _uiState.update { it.copy(fullName = v) }
    fun onPhoneChange(v: String)       = _uiState.update { it.copy(phoneNumber = v) }

    fun save() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val user   = authRepo.currentUser() ?: return@launch
                val userId = user.userId

                val updates = mutableMapOf<String, String>(
                    "full_name" to _uiState.value.fullName.trim()
                )
                if (_uiState.value.phoneNumber.isNotBlank()) {
                    updates["phone_number"] = _uiState.value.phoneNumber.trim()
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
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}