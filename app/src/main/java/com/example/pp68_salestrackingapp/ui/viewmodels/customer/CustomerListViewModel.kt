package com.example.pp68_salestrackingapp.ui.viewmodels.customer

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.pp68_salestrackingapp.data.model.AuthUser
import com.example.pp68_salestrackingapp.data.model.Customer
import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import com.example.pp68_salestrackingapp.data.repository.CustomerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class CustomerListViewModel @Inject constructor(
    private val repo: CustomerRepository,
    private val authRepo: AuthRepository
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _authUser = MutableStateFlow<AuthUser?>(authRepo.currentUser())
    private val _selectedBizGroup = MutableStateFlow<String?>(null)
    private val _selectedCustType = MutableStateFlow<String?>(null)
    private val _selectedInitial = MutableStateFlow<String?>(null)
    private val _selectedTab = MutableStateFlow(0)

    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    val error: StateFlow<String?> = _error.asStateFlow()
    val authUser: StateFlow<AuthUser?> = _authUser.asStateFlow()
    val selectedBizGroup: StateFlow<String?> = _selectedBizGroup.asStateFlow()
    val selectedCustType: StateFlow<String?> = _selectedCustType.asStateFlow()
    val selectedInitial: StateFlow<String?> = _selectedInitial.asStateFlow()
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    val customers: Flow<PagingData<Customer>> = combine(
        _searchQuery.debounce(300),
        _selectedBizGroup,
        _selectedCustType,
        _selectedTab,
        _selectedInitial
    ) { query, bizGroup, custType, tab, initial ->
        FilterCriteria(query, bizGroup, custType, tab, initial)
    }
        .distinctUntilChanged()
        .flatMapLatest { criteria ->
            repo.getCustomersPagingFlow(
                searchQuery = criteria.query,
                bizGroup = criteria.bizGroup,
                custType = criteria.custType,
                tabIndex = criteria.tab,
                initial = criteria.initial
            )
        }
        .catch { e ->
            _error.value = e.message
            emit(PagingData.empty())
        }
        .cachedIn(viewModelScope)

    init {
        refreshDataFromApi()
    }

    fun onSearchChange(query: String) {
        _searchQuery.value = query
        if (query.isNotBlank()) _selectedInitial.value = null
    }

    fun onInitialSelected(initial: String?) {
        _selectedInitial.value = initial
    }

    fun onTabSelected(index: Int) {
        _selectedTab.value = index.coerceIn(0, 2)
    }

    fun onBizGroupFilter(code: String?) {
        _selectedBizGroup.update { if (it == code) null else code }
    }

    fun onCustTypeFilter(type: String?) {
        _selectedCustType.update { if (it == type) null else type }
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
            val branchId = authRepo.currentUser()?.teamId
            if (branchId == null) {
                _isLoading.value = false
                _error.value = "ไม่พบรหัสสาขาของผู้ใช้"
                return@launch
            }
            repo.refreshCustomers(branchId).onFailure {
                Log.e("CustomerListVM", "refreshCustomers failed: ${it.message}", it)
                _error.value = it.message
            }
            _isLoading.value = false
        }
    }

    private data class FilterCriteria(
        val query: String,
        val bizGroup: String?,
        val custType: String?,
        val tab: Int,
        val initial: String?
    )
}
