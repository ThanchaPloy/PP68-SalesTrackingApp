package com.example.pp68_salestrackingapp.ui.screen.project

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pp68_salestrackingapp.data.repository.ProjectRepository
import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import com.example.pp68_salestrackingapp.data.model.Project
import com.example.pp68_salestrackingapp.data.model.AuthUser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
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
    
    // 0: Active, 1: Closed (PO & closing date reached), 2: Inactive (Lost/Failed)
    private val _selectedTabIndex = MutableStateFlow(0)

    private val _selectedStatuses = MutableStateFlow<Set<String>>(emptySet())
    private val _selectedScores   = MutableStateFlow<Set<String>>(emptySet())
    private val _authUser         = MutableStateFlow<AuthUser?>(authRepo.currentUser())

    val isLoading:   StateFlow<Boolean> = _isLoading
    val error:       StateFlow<String?> = _error
    val searchQuery: StateFlow<String>  = _searchQuery
    val selectedTabIndex: StateFlow<Int> = _selectedTabIndex
    val authUser:    StateFlow<AuthUser?> = _authUser

    val selectedStatuses: StateFlow<Set<String>> = _selectedStatuses
    val selectedScores:   StateFlow<Set<String>> = _selectedScores

    init {
        refreshDataFromApi()
    }

    val projects: StateFlow<List<Project>> = combine(
        _searchQuery.debounce(300),
        _selectedTabIndex,
        _selectedStatuses,
        _selectedScores
    ) { query, tabIndex, statuses, scores ->
        FilterCriteria(query, tabIndex, statuses, scores)
    }
        .flatMapLatest { criteria ->
            _isLoading.value = true

            val sourceFlow = if (criteria.query.isBlank()) {
                repo.getAllProjectsFlow()
            } else {
                repo.searchProjectsFlow(criteria.query)
            }

            sourceFlow.map { all ->
                val today = LocalDate.now().toString()
                
                var filtered = all.filter { p ->
                    when (criteria.tabIndex) {
                        1 -> { // Closed: PO & closing date reached, OR Completed
                            p.projectStatus == "Completed" || 
                            (p.projectStatus == "PO" && p.closingDate != null && p.closingDate <= today)
                        }
                        2 -> { // Inactive: Lost or Failed
                            p.projectStatus == "Lost" || p.projectStatus == "Failed"
                        }
                        else -> { // Active: Everything else
                            val isClosed = p.projectStatus == "Completed" || 
                                           (p.projectStatus == "PO" && p.closingDate != null && p.closingDate <= today)
                            val isInactive = p.projectStatus == "Lost" || p.projectStatus == "Failed"
                            !isClosed && !isInactive
                        }
                    }
                }

                if (criteria.statuses.isNotEmpty()) {
                    filtered = filtered.filter { it.projectStatus in criteria.statuses }
                }
                if (criteria.scores.isNotEmpty()) {
                    filtered = filtered.filter { it.opportunityScore?.uppercase() in criteria.scores }
                }

                filtered
            }
        }
        .onEach { _isLoading.value = false }
        .catch { e ->
            _error.value = e.message
            _isLoading.value = false
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
    
    fun onSelectTab(index: Int) { _selectedTabIndex.value = index }

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

    fun logout() {
        viewModelScope.launch {
            authRepo.logout()
        }
    }

    private data class FilterCriteria(
        val query: String,
        val tabIndex: Int,
        val statuses: Set<String>,
        val scores: Set<String>
    )
}
