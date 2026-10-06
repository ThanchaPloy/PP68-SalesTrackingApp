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

                // ลบ log ที่ไล่พิมพ์รหัส+ชื่อสาขาทุกแถวออก — เป็น log ดีบักที่ค้างมาจากตอนพัฒนา
                // และ minifyEnabled = false ทำให้มันพิมพ์จริงบน release ด้วย ไม่ใช่แค่ debug
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

    private fun isValidEmail(email: String): Boolean = email.matches(EMAIL_PATTERN)

    private companion object {
        // ✅ เดิมแยกสองทางด้วย System.getProperty("is_test"): ตอนรันเทสต์ใช้ regex นี้ ตอนใช้งานจริง
        // ใช้ android.util.Patterns.EMAIL_ADDRESS (ซึ่งเรียกในเทสต์แบบ JVM ไม่ได้ คืน null)
        // ผลคือโค้ดที่ผู้ใช้รันมีทางแยกที่มีอยู่เพื่อเอาใจเทสต์ และ "is_test" เป็นชื่อ property
        // ที่ไม่มีใครจอง ถ้าไลบรารีไหนไปตั้งขึ้นมา แอปจริงจะเปลี่ยนพฤติกรรมเองเงียบ ๆ
        // ใช้ regex ตัวเดียวทั้งสองที่จึงตรงไปตรงมากว่า และเป็นเส้นทางเดียวกับที่เทสต์คุมอยู่แล้ว
        // (ฝั่ง backend ตรวจอีกชั้นอยู่แล้ว ตรงนี้แค่กันพิมพ์ผิดตั้งแต่หน้าจอ)
        private val EMAIL_PATTERN = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$")
    }

    fun register() {
        val s = _uiState.value
        if (s.fullName.isBlank() || s.email.isBlank() ||
            s.password.isBlank() || s.selectedBranchId.isBlank()) {
            _uiState.update { it.copy(error = "กรุณากรอกข้อมูลให้ครบถ้วน") }
            return
        }

        if (!isValidEmail(s.email)) {
            _uiState.update { it.copy(error = "รูปแบบอีเมลไม่ถูกต้อง") }
            return
        }

        if (s.password.codePointCount(0, s.password.length) < 8) {
            _uiState.update { it.copy(error = "รหัสผ่านต้องมีอย่างน้อย 8 ตัวอักษร") }
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
