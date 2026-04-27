package com.example.pp68_salestrackingapp.ui.screen.customer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import com.example.pp68_salestrackingapp.data.repository.CustomerRepository
import com.example.pp68_salestrackingapp.data.model.AuthUser
import com.example.pp68_salestrackingapp.data.model.Customer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class CustomerListViewModel @Inject constructor(
    private val repo: CustomerRepository,
    private val authRepo: AuthRepository
) : ViewModel() {

    private val _isLoading   = MutableStateFlow(false)
    private val _error       = MutableStateFlow<String?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _authUser    = MutableStateFlow<AuthUser?>(authRepo.currentUser())

    val searchQuery: StateFlow<String>  = _searchQuery.asStateFlow()
    val isLoading:   StateFlow<Boolean> = _isLoading.asStateFlow()
    val error:       StateFlow<String?> = _error.asStateFlow()
    val authUser:    StateFlow<AuthUser?> = _authUser.asStateFlow()

    init {
        // ให้ยิง API ไปอัปเดต Local DB ทันทีที่เปิดหน้าจอ
        refreshDataFromApi()
    }

    // 🌟 พระเอกอยู่ตรงนี้: ใช้ debounce ของคุณ + Flow ของผม
    val customers: StateFlow<List<Customer>> = _searchQuery
        .debounce(300) // รอให้พิมพ์เสร็จ 0.3 วินาที
        .flatMapLatest { query ->
            // ถ้าคำค้นหาว่าง ดึงทั้งหมดจาก Local DB, ถ้าไม่ว่าง ให้ค้นหาจาก Local DB
            if (query.isBlank()) {
                repo.getAllCustomersFlow()
            } else {
                repo.searchCustomersFlow(query)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun onSearchChange(query: String) {
        _searchQuery.value = query
    }

    fun clearError() {
        _error.value = null
    }

    fun refreshDataFromApi() {
        viewModelScope.launch {
            _isLoading.value = true
            val user = authRepo.currentUser()
            val branchId = user?.teamId // ✅ ใช้ teamId จาก AuthUser
            if (branchId == null) {
                _isLoading.value = false
                _error.value = "ไม่พบรหัสสาขาของผู้ใช้"
                return@launch
            }
            // ✅ แก้ไขให้ส่ง branchId แทน userId เพื่อให้ดึงข้อมูลลูกค้าในระดับสาขา
            val result = repo.refreshCustomers(branchId)
            result.onFailure { _error.value = it.message }
            _isLoading.value = false
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepo.logout()
        }
    }
}
