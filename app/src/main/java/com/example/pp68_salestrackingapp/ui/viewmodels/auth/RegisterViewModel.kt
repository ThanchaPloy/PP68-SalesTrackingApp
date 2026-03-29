package com.example.pp68_salestrackingapp.ui.viewmodels.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pp68_salestrackingapp.data.model.Branch
import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import com.example.pp68_salestrackingapp.data.repository.BranchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RegisterUiState(
    val fullName:           String       = "",
    val email:              String       = "",
    val password:           String       = "",
    val selectedBranchId:   String       = "",
    val selectedBranchName: String       = "",
    val branches:           List<Branch> = emptyList(),
    val isLoading:          Boolean      = false,
    val isSuccess:          Boolean      = false,
    val error:              String?      = null
)

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val authRepo:   AuthRepository,
    private val branchRepo: BranchRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState

    init { loadBranches() }

    private fun loadBranches() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                branchRepo.syncFromRemote()
                val branches = branchRepo.observeBranches()

                // ✅ เพิ่ม log ดูว่าได้ข้อมูลกี่สาขา
                android.util.Log.d("BranchDebug", "Total branches: ${branches.size}")
                branches.forEach {
                    android.util.Log.d("BranchDebug", "Branch: ${it.branchId} - ${it.branchName}")
                }

                _uiState.update { it.copy(branches = branches, isLoading = false) }
            } catch (e: Exception) {
                android.util.Log.e("BranchDebug", "Error: ${e.message}")
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun onFullNameChange(value: String)  { _uiState.update { it.copy(fullName = value) } }
    fun onEmailChange(value: String)     { _uiState.update { it.copy(email = value) } }
    fun onPasswordChange(value: String)  { _uiState.update { it.copy(password = value) } }

    fun onBranchSelected(index: Int) {
        val branch = _uiState.value.branches.getOrNull(index) ?: return
        _uiState.update {
            it.copy(
                selectedBranchId   = branch.branchId,
                selectedBranchName = branch.branchName
            )
        }
    }

    fun register() {
        val s = _uiState.value
        if (s.fullName.isBlank() || s.email.isBlank() ||
            s.password.isBlank() || s.selectedBranchId.isBlank()) {
            _uiState.update { it.copy(error = "กรุณากรอกข้อมูลให้ครบถ้วน") }
            return
        }

        if (s.password.length < 6) {
            _uiState.update { it.copy(error = "รหัสผ่านต้องมีอย่างน้อย 6 ตัวอักษร") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            // ✅ เรียก register จริง — return LoginResponse พร้อม token
            authRepo.register(
                email    = s.email,
                password = s.password,
                fullName = s.fullName,
                branchId = s.selectedBranchId
            ).onSuccess {
                // register สำเร็จ + token ถูก save ใน AuthRepository แล้ว
                _uiState.update { it.copy(isLoading = false, isSuccess = true) }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}