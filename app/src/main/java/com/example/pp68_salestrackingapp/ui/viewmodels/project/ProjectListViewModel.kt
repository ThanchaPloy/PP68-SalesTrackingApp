package com.example.pp68_salestrackingapp.ui.viewmodels.project

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.pp68_salestrackingapp.data.repository.ProjectRepository
import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import com.example.pp68_salestrackingapp.data.model.Project
import com.example.pp68_salestrackingapp.data.model.AuthUser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProjectListViewModel @Inject constructor(
    private val repo: ProjectRepository,
    private val authRepo: AuthRepository
) : ViewModel() {

    private val _isLoading   = MutableStateFlow(false)
    private val _error       = MutableStateFlow<String?>(null)
    private val _searchQuery = MutableStateFlow("")
    
    // 0: Active, 1: Closed, 2: Inactive
    private val _selectedTabIndex = MutableStateFlow(0)

    private val _selectedStatuses = MutableStateFlow<Set<String>>(emptySet())
    private val _selectedScores   = MutableStateFlow<Set<String>>(emptySet())
    private val _authUser         = MutableStateFlow<AuthUser?>(authRepo.currentUser())

    val isLoading:   StateFlow<Boolean> = _isLoading.asStateFlow()
    val error:       StateFlow<String?> = _error.asStateFlow()
    val searchQuery: StateFlow<String>  = _searchQuery.asStateFlow()
    val selectedTabIndex: StateFlow<Int> = _selectedTabIndex.asStateFlow()
    val authUser:    StateFlow<AuthUser?> = _authUser.asStateFlow()

    val selectedStatuses: StateFlow<Set<String>> = _selectedStatuses.asStateFlow()
    val selectedScores:   StateFlow<Set<String>> = _selectedScores.asStateFlow()

    val projects: Flow<PagingData<Project>> = combine(
        _searchQuery.debounce(300),
        _selectedTabIndex,
        _selectedStatuses,
        _selectedScores
    ) { query, tabIndex, statuses, scores ->
        FilterCriteria(query, tabIndex, statuses, scores)
    }
        .distinctUntilChanged()
        .flatMapLatest { criteria ->
            // Tab/search/status/score filtering is performed by Room, not on an in-memory full list.
            repo.getProjectsPagingFlow(
                searchQuery = criteria.query,
                tabIndex = criteria.tabIndex,
                selectedStatuses = criteria.statuses,
                selectedScores = criteria.scores
            )
        }
        .catch { e ->
            _error.value = e.message
            emit(PagingData.empty())
        }
        .cachedIn(viewModelScope)

    fun refreshDataFromApi() {
        val userId = authRepo.currentUser()?.userId
        if (userId == null) {
            _error.value = "กรุณาเข้าสู่ระบบใหม่"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            repo.refreshProjects(userId).onFailure { _error.value = it.message }
            _isLoading.value = false
        }
    }

    fun onSearchChange(query: String) { _searchQuery.value = query }
    
    fun onSelectTab(index: Int) { _selectedTabIndex.value = index.coerceIn(0, 2) }

    fun toggleStatusFilter(status: String) {
        _selectedStatuses.update { current ->
            if (status in current) current - status else current + status
        }
    }

    fun toggleScoreFilter(score: String) {
        val upperScore = score.uppercase()
        _selectedScores.update { current ->
            if (upperScore in current) current - upperScore else current + upperScore
        }
    }

    fun resetFilters() {
        _selectedStatuses.value = emptySet()
        _selectedScores.value = emptySet()
    }

    fun clearError() { _error.value = null }


    init {
        refreshDataFromApi()
    }

    private data class FilterCriteria(
        val query: String,
        val tabIndex: Int,
        val statuses: Set<String>,
        val scores: Set<String>
    )
}
