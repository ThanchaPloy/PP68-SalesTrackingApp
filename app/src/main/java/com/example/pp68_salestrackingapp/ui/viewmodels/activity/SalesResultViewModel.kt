package com.example.pp68_salestrackingapp.ui.viewmodels.activity

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pp68_salestrackingapp.data.repository.ActivityRepository
import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import com.example.pp68_salestrackingapp.data.repository.ProjectRepository
import com.example.pp68_salestrackingapp.data.model.Project
import com.example.pp68_salestrackingapp.data.model.SalesActivity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

data class SalesResultUiState(
    val projectId: String? = null,
    val activityId: String? = null,
    val project: Project? = null,
    val reportDate: String = LocalDate.now().toString(),
    val currentStatus: String = "",
    val isStatusUpdateEnabled: Boolean = false,
    val newStatus: String = "",
    val opportunityScore: String? = null,
    val dealPosition: String = "",
    val previousSolution: String = "",
    val counterpartyMultiplier: String = "",
    val responseSpeed: String = "",
    val isProposalSent: Boolean = false,
    val proposalDate: String? = null,
    val competitorCount: Int = 0,
    val visitSummary: String = "",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class SalesResultViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val projectRepo: ProjectRepository,
    private val activityRepo: ActivityRepository,
    private val authRepo: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SalesResultUiState())
    val uiState: StateFlow<SalesResultUiState> = _uiState

    private var custId: String = ""

    init {
        val pId = savedStateHandle.get<String>("projectId")
        val aId = savedStateHandle.get<String>("activityId")

        _uiState.update { it.copy(projectId = pId, activityId = aId) }

        if (!aId.isNullOrBlank()) {
            loadActivityData(aId)
        } else if (!pId.isNullOrBlank()) {
            loadProjectData(pId)
        }
    }

    private fun loadProjectData(pId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            projectRepo.getProjectById(pId).onSuccess { p ->
                custId = p.custId
                _uiState.update {
                    it.copy(
                        project = p,
                        currentStatus = p.projectStatus ?: "",
                        opportunityScore = p.opportunityScore,
                        isLoading = false
                    )
                }
            }.onFailure { e ->
                _uiState.update { it.copy(error = "โหลดข้อมูลโครงการไม่สำเร็จ: ${e.message}", isLoading = false) }
            }
        }
    }

    private fun loadActivityData(aId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            activityRepo.getActivityById(aId).onSuccess { actList ->
                val act = actList.firstOrNull()
                if (act != null) {
                    custId = act.customerId
                    _uiState.update {
                        it.copy(
                            activityId   = aId,
                            projectId    = act.projectId,
                            visitSummary = act.weeklyNote ?: act.detail ?: "",
                            reportDate   = act.activityDate,
                            isLoading    = false
                        )
                    }
                    // โหลด project ต่อ
                    act.projectId?.let { loadProjectData(it) }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "ไม่พบข้อมูลกิจกรรม") }
                }
            }.onFailure { e ->
                _uiState.update { it.copy(error = "โหลดข้อมูลกิจกรรมไม่สำเร็จ: ${e.message}", isLoading = false) }
            }
        }
    }

    fun onReportDateChanged(date: String)        { _uiState.update { it.copy(reportDate = date) } }
    fun onStatusToggle(enabled: Boolean)         { _uiState.update { it.copy(isStatusUpdateEnabled = enabled) } }
    fun onNewStatusSelected(status: String)      { _uiState.update { it.copy(newStatus = status) } }
    fun onOpportunitySelected(score: String)     { _uiState.update { it.copy(opportunityScore = score) } }
    fun onDealPositionChanged(value: String)     { _uiState.update { it.copy(dealPosition = value) } }
    fun onPreviousSolutionChanged(value: String) { _uiState.update { it.copy(previousSolution = value) } }
    fun onCounterpartyMultiplierChanged(v: String){ _uiState.update { it.copy(counterpartyMultiplier = v) } }
    fun onResponseSpeedChanged(value: String)    { _uiState.update { it.copy(responseSpeed = value) } }
    fun onProposalToggle(sent: Boolean)          { _uiState.update { it.copy(isProposalSent = sent) } }
    fun onProposalDateChanged(date: String)      { _uiState.update { it.copy(proposalDate = date) } }
    fun onCompetitorCountChanged(delta: Int)     {
        _uiState.update { it.copy(competitorCount = (it.competitorCount + delta).coerceAtLeast(0)) }
    }
    fun onSummaryChanged(text: String)           { _uiState.update { it.copy(visitSummary = text) } }

    fun save() {
        val s = _uiState.value
        if (s.visitSummary.isBlank()) {
            _uiState.update { it.copy(error = "กรุณากรอกสรุปการเข้าพบ") }
            return
        }

        val finalCustId = if (custId.isNotBlank()) custId else s.project?.custId ?: ""
        if (finalCustId.isBlank()) {
            _uiState.update { it.copy(error = "ไม่พบข้อมูลลูกค้า กรุณาลองใหม่อีกครั้ง") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }

            val userId = authRepo.currentUser()?.userId ?: "USR-0001"
            val summaryContent = buildString {
                append(s.visitSummary)
                append("\n\n--- ผลการขาย ---")
                append("\nDeal Position: ${s.dealPosition}")
                append("\nPrevious Solution: ${s.previousSolution}")
                append("\nCounterparty Multiplier: ${s.counterpartyMultiplier}")
                append("\nResponse Speed: ${s.responseSpeed}")
                append("\nProposal: ${if (s.isProposalSent) s.proposalDate else "No"}")
                append("\nCompetitors: ${s.competitorCount}")
            }

            // 1. บันทึก/อัปเดต Appointment
            val actResult = if (!s.activityId.isNullOrBlank()) {
                // ✅ ใช้ updateActivity แทน updateActivityMap
                val updates = mapOf<String, Any>(
                    "plan_status" to "completed",
                    "note"        to summaryContent
                )
                activityRepo.updateActivity(s.activityId, updates)
            } else {
                val newId = "APT-" + UUID.randomUUID().toString().take(8).uppercase()
                val newActivity = SalesActivity(
                    activityId   = newId,
                    userId       = userId,
                    customerId   = finalCustId,
                    projectId    = s.projectId,
                    activityType = "onsite",
                    activityDate = s.reportDate,
                    detail       = summaryContent,
                    status       = "completed"
                )
                activityRepo.addActivity(newActivity)
            }

            if (actResult.isFailure) {
                _uiState.update { it.copy(isSaving = false, error = "บันทึกไม่สำเร็จ: ${actResult.exceptionOrNull()?.message}") }
                return@launch
            }

            // 2. อัปเดต Project status/score (ถ้ามี)
            if (!s.projectId.isNullOrBlank()) {
                val pUpdates = mutableMapOf<String, String>()
                val statusToUpdate = if (s.isStatusUpdateEnabled && s.newStatus.isNotBlank()) s.newStatus else s.currentStatus
                if (statusToUpdate.isNotBlank()) pUpdates["project_status"] = statusToUpdate
                val score = s.opportunityScore ?: s.project?.opportunityScore
                if (score != null) pUpdates["opportunity_score"] = score
                if (pUpdates.isNotEmpty()) projectRepo.updateProjectFields(s.projectId, pUpdates)
            }

            _uiState.update { it.copy(isSaving = false, isSaved = true) }
        }
    }
}