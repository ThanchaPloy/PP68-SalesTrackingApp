package com.example.pp68_salestrackingapp.ui.screen.customer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import com.example.pp68_salestrackingapp.data.repository.CustomerRepository
import com.example.pp68_salestrackingapp.data.model.AuthUser
import com.example.pp68_salestrackingapp.data.model.Customer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class CustomerListViewModel @Inject constructor(
    private val repo: CustomerRepository,
    private val authRepo: AuthRepository
) : ViewModel() {

    private val _isLoading      = MutableStateFlow(false)
    private val _error          = MutableStateFlow<String?>(null)
    private val _searchQuery    = MutableStateFlow("")
    private val _authUser       = MutableStateFlow<AuthUser?>(authRepo.currentUser())
    private val _selectedBizGroup  = MutableStateFlow<String?>(null)
    private val _selectedCustType  = MutableStateFlow<String?>(null)

    val searchQuery:      StateFlow<String>   = _searchQuery.asStateFlow()
    val isLoading:        StateFlow<Boolean>  = _isLoading.asStateFlow()
    val error:            StateFlow<String?>  = _error.asStateFlow()
    val authUser:         StateFlow<AuthUser?> = _authUser.asStateFlow()
    val selectedBizGroup: StateFlow<String?>  = _selectedBizGroup.asStateFlow()
    val selectedCustType: StateFlow<String?>  = _selectedCustType.asStateFlow()

    init {
        refreshDataFromApi()
    }

    val customers: StateFlow<List<Customer>> = combine(
        _searchQuery.debounce(300).flatMapLatest { query ->
            if (query.isBlank()) repo.getAllCustomersFlow() else repo.searchCustomersFlow(query)
        },
        _selectedBizGroup,
        _selectedCustType
    ) { list, bizGroup, custType ->
        list
            .let { if (bizGroup != null) it.filter { c -> c.branchId == bizGroup } else it }
            .let { if (custType != null) it.filter { c -> c.custType == custType } else it }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun onSearchChange(query: String) { _searchQuery.value = query }

    fun onBizGroupFilter(code: String?) {
        _selectedBizGroup.value = if (_selectedBizGroup.value == code) null else code
    }

    fun onCustTypeFilter(type: String?) {
        _selectedCustType.value = if (_selectedCustType.value == type) null else type
    }

    fun resetFilters() {
        _selectedBizGroup.value = null
        _selectedCustType.value = null
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
