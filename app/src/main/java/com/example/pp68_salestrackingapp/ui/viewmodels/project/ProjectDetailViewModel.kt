package com.example.pp68_salestrackingapp.ui.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pp68_salestrackingapp.data.model.Project
import com.example.pp68_salestrackingapp.data.repository.ProjectRepository
import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import com.example.pp68_salestrackingapp.data.repository.ActivityRepository
import com.example.pp68_salestrackingapp.data.repository.CustomerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── 1. Data Classes สำหรับ UI ──
data class TaskItem(
    val activityId: String,
    val title: String,
    val description: String? = null,
    val plannedDate: String? = null
)

data class TeamMember(
    val userId: String,
    val fullName: String
)

data class HistoryItem(
    val activityId: String? = null,
    val resultId: String? = null,
    val title: String,
    val description: String? = null,
    val plannedDate: String? = null,
    val planStatus: String = "completed",
    val activityType: String = "online",
    val contactName: String? = null,
    val isStandaloneResult: Boolean = false
)

// ── 2. UI State ──────────────────────────────────────────────────────
data class ProjectDetailUiState(
    val isLoading: Boolean = false,
    val isDeleting: Boolean = false,
    val project: Project? = null,
    val companyName: String = "",
    val upcomingTasks: List<TaskItem> = emptyList(),
    val teamMembers: List<TeamMember> = emptyList(),
    val history: List<HistoryItem> = emptyList(),
    val error: String? = null,
    val deleteSuccess: Boolean = false
)

// ── 3. ViewModel ──────────────────────────────────────────────────────
@HiltViewModel
class ProjectDetailViewModel @Inject constructor(
    private val projectRepo: ProjectRepository,
    private val authRepo: AuthRepository,
    private val activityRepo: ActivityRepository,
    private val customerRepo: CustomerRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProjectDetailUiState())
    val uiState: StateFlow<ProjectDetailUiState> = _uiState.asStateFlow()

    private val projectId: String? = savedStateHandle["projectId"]

    init {
        projectId?.let { id ->
            observeProject(id)
            observeActivitiesAndResults(id)
        }
    }

    private fun observeProject(id: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            projectRepo.getProjectByIdFlow(id).collectLatest { project ->
                if (project != null) {
                    val company = customerRepo.getCustomerById(project.custId)
                        .getOrNull()?.companyName ?: ""

                    // ✅ เรียกใช้ผ่าน Repository ที่เราเตรียมไว้ แก้ปัญหา Unresolved 'apiService'
                    val members = projectRepo.getProjectMembersDetailed(id).map {
                        TeamMember(it.first, it.second)
                    }

                    _uiState.update { state ->
                        state.copy(
                            isLoading   = false,
                            project     = project,
                            companyName = company,
                            teamMembers = members
                        )
                    }
                }
            }
        }
    }

    private fun observeActivitiesAndResults(id: String) {
        viewModelScope.launch {
            combine(
                activityRepo.getActivitiesByProjectFlow(id),
                activityRepo.getResultsByProjectFlow(id)
            ) { activities, results ->

                val tasks = activities
                    .filter { it.status.lowercase() != "completed" }
                    .map { TaskItem(it.activityId, it.activityType, it.detail, it.activityDate) }

                val activityHistory = activities
                    .filter { it.status.lowercase() == "completed" }
                    .map { act ->
                        HistoryItem(
                            activityId    = act.activityId,
                            title         = if (!act.detail.isNullOrBlank()) act.detail else act.activityType,
                            description   = act.activityType,
                            plannedDate   = act.activityDate,
                            planStatus    = act.status,
                            activityType  = act.activityType,
                            contactName   = act.contactName,
                            isStandaloneResult = false
                        )
                    }

                val standaloneHistory = results
                    .filter { it.activityId == null }
                    .map { res ->
                        HistoryItem(
                            activityId    = null,
                            resultId      = res.resultId,
                            title         = if (!res.summary.isNullOrBlank()) {
                                if (res.summary.length > 50) res.summary.take(47) + "..." else res.summary
                            } else "บันทึกผลการขายอิสระ",
                            description   = "Standalone Report",
                            plannedDate   = res.reportDate,
                            planStatus    = "completed",
                            activityType  = "report",
                            contactName   = "Standalone Record",
                            isStandaloneResult = true
                        )
                    }

                val fullHistory = (activityHistory + standaloneHistory)
                    .sortedByDescending { it.plannedDate }

                tasks to fullHistory
            }.collect { (tasks, history) ->
                _uiState.update { it.copy(upcomingTasks = tasks, history = history) }
            }
        }
    }

    fun deleteProject() {
        val id = projectId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true) }
            projectRepo.deleteProject(id).fold(
                onSuccess = {
                    _uiState.update { it.copy(isDeleting = false, deleteSuccess = true) }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isDeleting = false, error = e.message) }
                }
            )
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepo.logout()
        }
    }
}