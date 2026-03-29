package com.example.pp68_salestrackingapp.ui.viewmodels.project

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
import javax.inject.Inject

private val CLOSED_STATUSES = setOf("Completed", "Lost", "Failed")

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProjectListViewModel @Inject constructor(
    private val repo: ProjectRepository,
    private val authRepo: AuthRepository
) : ViewModel() {

    private val _isLoading   = MutableStateFlow(false)
    private val _error       = MutableStateFlow<String?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _showClosed  = MutableStateFlow(false)
    private val _authUser    = MutableStateFlow<AuthUser?>(authRepo.currentUser())

    private val _selectedStatuses = MutableStateFlow<Set<String>>(emptySet())
    private val _selectedScores   = MutableStateFlow<Set<String>>(emptySet())

    val isLoading:   StateFlow<Boolean> = _isLoading.asStateFlow()
    val error:       StateFlow<String?> = _error.asStateFlow()
    val searchQuery: StateFlow<String>  = _searchQuery.asStateFlow()
    val showClosed:  StateFlow<Boolean> = _showClosed.asStateFlow()
    val authUser:    StateFlow<AuthUser?> = _authUser.asStateFlow()

    val selectedStatuses: StateFlow<Set<String>> = _selectedStatuses.asStateFlow()
    val selectedScores:   StateFlow<Set<String>> = _selectedScores.asStateFlow()

    val projects: StateFlow<List<Project>> = combine(
        _searchQuery.debounce(300),
        _showClosed,
        _selectedStatuses,
        _selectedScores
    ) { query, closed, statuses, scores ->
        FilterCriteria(query, closed, statuses, scores)
    }
        .flatMapLatest { criteria ->
            _isLoading.value = true
            val sourceFlow = if (criteria.query.isBlank()) {
                repo.getAllProjectsFlow()
            } else {
                repo.searchProjectsFlow(criteria.query)
            }

            sourceFlow.map { all ->
                var filtered = if (criteria.closed) {
                    all.filter { it.projectStatus in CLOSED_STATUSES }
                } else {
                    all.filter { it.projectStatus !in CLOSED_STATUSES }
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

    fun onSearchChange(query: String) { _searchQuery.value = query }
    fun onToggleTab(showClosed: Boolean) { _showClosed.value = showClosed }

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

    fun logout() {
        viewModelScope.launch {
            authRepo.logout()
        }
    }

    fun clearError() { _error.value = null }

    fun refreshDataFromApi() {
        viewModelScope.launch {
            _isLoading.value = true
            val userId = authRepo.currentUser()?.userId ?: return@launch
            repo.refreshProjects(userId)
            _isLoading.value = false
        }
    }

    init {
        refreshDataFromApi()
    }

    private data class FilterCriteria(
        val query: String,
        val closed: Boolean,
        val statuses: Set<String>,
        val scores: Set<String>
    )
}
