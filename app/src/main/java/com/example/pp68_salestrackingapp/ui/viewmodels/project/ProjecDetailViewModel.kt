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
import kotlinx.coroutines.flow.first
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
    val activityId: String,
    val title: String,
    val description: String? = null,
    val plannedDate: String? = null,
    val planStatus: String = "completed",
    val activityType: String = "online",
    val contactName: String? = null
)

// ── 2. UI State สำหรับหน้า Detail ─────────────────────────────────────
data class ProjectDetailUiState(
    val isLoading: Boolean = false,
    val project: Project? = null,
    val companyName: String = "",
    val upcomingTasks: List<TaskItem> = emptyList(),
    val teamMembers: List<TeamMember> = emptyList(),
    val history: List<HistoryItem> = emptyList(),
    val error: String? = null
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
            loadProjectDetail(id)
            observeActivities(id)
        }
    }

    private fun observeActivities(id: String) {
        viewModelScope.launch {
            activityRepo.getActivitiesByProjectFlow(id).collect { activities ->
                val tasks = activities
                    .filter { it.status.lowercase() != "completed" }
                    .map { TaskItem(it.activityId, it.activityType, it.detail, it.activityDate) }
                
                val history = activities
                    .filter { it.status.lowercase() == "completed" }
                    .map { HistoryItem(it.activityId, it.activityType, it.detail, it.activityDate, it.status, it.activityType, it.contactName) }
                
                _uiState.update { it.copy(upcomingTasks = tasks, history = history) }
            }
        }
    }

    fun loadProjectDetail(projectId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            projectRepo.getProjectById(projectId).fold(
                onSuccess = { project ->
                    val company = customerRepo.getCustomerById(project.custId).getOrNull()?.companyName ?: ""
                    
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            project = project,
                            companyName = company,
                            teamMembers = listOf(
                                TeamMember("U01", "สมชาย ใจดี"),
                                TeamMember("U02", "สมหญิง รักงาน")
                            )
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message) }
                }
            )
        }
    }

    fun logout() {
        authRepo.logout()
    }
}