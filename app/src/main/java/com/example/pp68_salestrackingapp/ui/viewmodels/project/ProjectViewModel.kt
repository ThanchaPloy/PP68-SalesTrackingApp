package com.example.pp68_salestrackingapp.ui.screen.project

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pp68_salestrackingapp.data.repository.ProjectRepository
import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import com.example.pp68_salestrackingapp.data.model.Project
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

    private val _selectedStatuses = MutableStateFlow<Set<String>>(emptySet())
    private val _selectedScores   = MutableStateFlow<Set<String>>(emptySet())

    val isLoading:   StateFlow<Boolean> = _isLoading
    val error:       StateFlow<String?> = _error
    val searchQuery: StateFlow<String>  = _searchQuery
    val showClosed:  StateFlow<Boolean> = _showClosed

    val selectedStatuses: StateFlow<Set<String>> = _selectedStatuses
    val selectedScores:   StateFlow<Set<String>> = _selectedScores

    init {
        refreshDataFromApi()
    }

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

    fun clearError() { _error.value = null }

    private data class FilterCriteria(
        val query: String,
        val closed: Boolean,
        val statuses: Set<String>,
        val scores: Set<String>
    )
}
