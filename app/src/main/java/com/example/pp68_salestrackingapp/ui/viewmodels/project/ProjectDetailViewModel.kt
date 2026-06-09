package com.example.pp68_salestrackingapp.ui.viewmodels.project

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
            observeActivities(id)
            syncProjectFromServer(id)
        }
    }

    private fun syncProjectFromServer(id: String) {
        viewModelScope.launch {
            projectRepo.syncProject(id)
        }
    }

    /**
     * ✅ บังคับโหลดข้อมูลสมาชิกและโปรเจคจาก Server ใหม่
     */
    fun refresh() {
        val id = projectId ?: return
        viewModelScope.launch {
            projectRepo.syncProject(id)
            val members = projectRepo.getProjectMembersDetailed(id)
                .map { TeamMember(it.first, it.second) }
            _uiState.update { it.copy(teamMembers = members) }
        }
    }

    private fun observeProject(id: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            projectRepo.getProjectByIdFlow(id).collectLatest { project ->
                if (project != null) {
                    _uiState.update { it.copy(isLoading = false, project = project, error = null) }
                    launch {
                        val company = customerRepo.getCustomerById(project.custId)
                            .getOrNull()?.companyName ?: ""
                        _uiState.update { it.copy(companyName = company) }
                    }
                    launch {
                        val members = projectRepo.getProjectMembersDetailed(id)
                            .map { TeamMember(it.first, it.second) }
                        _uiState.update { it.copy(teamMembers = members) }
                    }
                }
            }
        }
    }

    private fun observeActivities(id: String) {
        viewModelScope.launch {
            combine(
                activityRepo.getActivitiesByProjectFlow(id),
                activityRepo.getResultsByProjectFlow(id)
            ) { activities, results ->
                // 1. งานที่กำลังจะมาถึง
                val tasks = activities
                    .filter { it.status.lowercase() != "completed" }
                    .map { TaskItem(it.activityId, it.activityType, it.detail, it.activityDate) }

                val actMap = activities.associateBy { it.activityId }

                // 2. ประวัติกิจกรรม (History)
                // ✅ กรองรายการที่ซ้ำซ้อนอย่างรัดกุมที่สุด
                val history = results
                    .sortedWith(
                        compareByDescending<com.example.pp68_salestrackingapp.data.model.ActivityResult> { it.reportDate ?: "" }
                            .thenByDescending { it.resultId }
                    )
                    .map { res ->
                        val act = res.activityId?.let { actMap[it] }
                        val summaryText = res.summary?.trim() ?: ""
                        val titleSnippet = summaryText.take(40).let { if (it.length == 40) "$it..." else it }
                        val title = if (!res.activityId.isNullOrBlank()) "นัดหมาย" else (if (titleSnippet.isBlank()) "บันทึกสรุปการเข้าพบ" else titleSnippet)

                        HistoryItem(
                            activityId   = res.activityId ?: res.resultId,
                            title        = title,
                            description  = summaryText,
                            plannedDate  = (res.reportDate?.take(10) ?: act?.activityDate?.take(10)) ?: "",
                            planStatus   = "completed",
                            activityType = act?.activityType ?: "visit",
                            contactName  = act?.contactName ?: "ไม่ระบุชื่อผู้ติดต่อ"
                        )
                    }
                    .distinctBy { item ->
                        // กุญแจในการยุบรวม:
                        // - ถ้ามีนัดหมาย (ActivityId) จะแสดงแค่อันเดียวต่อนัดหมายนั้น
                        // - ถ้าเป็นบันทึก Standalone จะยุบรวมด้วย วันที่ + หัวข้อ + เนื้อหาที่ลบช่องว่างออกทั้งหมด
                        val isActivity = actMap.containsKey(item.activityId)
                        if (isActivity) {
                            item.activityId
                        } else {
                            val normalizedContent = (item.description ?: "").replace("\\s".toRegex(), "")
                            "${item.plannedDate}_${item.title}_$normalizedContent"
                        }
                    }
                    .sortedByDescending { it.plannedDate }

                tasks to history
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
