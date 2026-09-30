package com.example.pp68_salestrackingapp.ui.viewmodels.project

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pp68_salestrackingapp.data.repository.ProjectRepository
import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import com.example.pp68_salestrackingapp.data.model.Project
import com.example.pp68_salestrackingapp.data.model.AuthUser
import com.example.pp68_salestrackingapp.utils.ProjectStages
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
                // ✅ จัดแท็บด้วย "สถานะ" อย่างเดียว ไม่เอาวันปิดมาร่วมตัดสิน
                //
                // เดิมแท็บ Closed คือ PO && closingDate <= today ซึ่งพลาดสองทาง: โครงการที่ปิด
                // การขายได้แล้วแต่ยังไม่ถึงวันส่งมอบ (หรือไม่เคยกรอกวันปิด) ค้างอยู่แท็บ Active
                // และการเทียบสตริงยาวไม่เท่ากัน ("2026-04-06T00:00:00" > "2026-04-06") ทำให้
                // โครงการที่ปิดวันนี้ถูกตีว่ายังไม่ถึงกำหนด — PO แปลว่าขายได้แล้ว จบดีลแล้ว
                // จึงอยู่แท็บ "ปิดแล้ว" ตั้งแต่ตอนที่สถานะเป็น PO ไม่ต้องรอวันไหน
                //
                // WON กับ LOST แยกกันคนละแท็บโดยตั้งใจ: ปิดได้ = ผลงาน, แพ้/ล้มเลิก = inactive
                var filtered = all.filter { p ->
                    when (criteria.tabIndex) {
                        1 -> p.projectStatus in ProjectStages.WON   // ปิดแล้ว (ขายได้)
                        2 -> p.projectStatus in ProjectStages.LOST  // inactive (แพ้/ล้มเลิก)
                        else -> p.projectStatus !in ProjectStages.CLOSED
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
