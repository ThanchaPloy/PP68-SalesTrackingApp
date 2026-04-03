package com.example.pp68_salestrackingapp.ui.viewmodels.activity

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pp68_salestrackingapp.data.repository.ActivityRepository
import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import com.example.pp68_salestrackingapp.data.repository.ProjectRepository
import com.example.pp68_salestrackingapp.data.model.Project
import com.example.pp68_salestrackingapp.data.model.ActivityResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
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
    companion object {
        val DEAL_POSITION_MAP = mapOf(
            "ลูกค้าใช้เราอยู่แล้ว การต่อสัญญามีโอกาสสูงมาก" to "incumbent",
            "ลูกค้าเลือกเราเป็นตัวหลัก คู่แข่งอื่นเป็นแค่ backup"  to "vendor_of_choice",
            "ถูกเชิญมาเพื่อ benchmark ราคา โอกาสต่ำ"              to "invited_to_compare"
        )
        val SOLUTION_MAP = mapOf(
            "ไม่มี Solution เดิม"              to "no_solution",
            "มีระบบเดิมที่ไม่ใช่คู่แข่ง"       to "non_competitor_system",
            "ใช้คู่แข่งอยู่และไม่มีปัญหา"      to "competitor_no_issue"
        )
        val COUNTERPARTY_MAP = mapOf(
            "ดีลกับ Main Contractor โดยตรง"                       to "direct_main_contractor",
            "ดีลผ่าน Installer — Main Contractor ได้งานแล้ว"      to "via_installer_main_awarded",
            "ดีลผ่าน Installer — Main Contractor ยังไม่ได้งาน"    to "via_installer_main_pending"
        )
        val RESPONSE_SPEED_MAP = mapOf(
            "เร็ว"           to "fast",
            "ปกติ"           to "normal",
            "ช้าหรือเงียบ"   to "slow_silent"
        )

        val DEAL_POSITION_REVERSE    = DEAL_POSITION_MAP.entries.associate { (k, v) -> v to k }
        val SOLUTION_REVERSE         = SOLUTION_MAP.entries.associate { (k, v) -> v to k }
        val COUNTERPARTY_REVERSE     = COUNTERPARTY_MAP.entries.associate { (k, v) -> v to k }
        val RESPONSE_SPEED_REVERSE   = RESPONSE_SPEED_MAP.entries.associate { (k, v) -> v to k }
    }

    private fun loadActivityData(aId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            activityRepo.getActivityById(aId).onSuccess { list ->
                list.firstOrNull()?.let { act ->
                    _uiState.update {
                        it.copy(
                            activityId = aId,
                            projectId  = act.projectId,
                            reportDate = act.activityDate ?: LocalDate.now().toString()
                        )
                    }
                    act.projectId?.let { loadProjectData(it) }
                }
            }

            // โหลดผลการขายที่เคยบันทึกไว้ (ถ้ามี)
            val result = activityRepo.getActivityResult(aId)
            if (result != null) {
                _uiState.update {
                    it.copy(
                        reportDate             = result.reportDate ?: it.reportDate,
                        newStatus              = result.newStatus ?: "",
                        isStatusUpdateEnabled  = !result.newStatus.isNullOrBlank(),
                        opportunityScore       = result.opportunityScore ?: it.opportunityScore,
                        dealPosition           = DEAL_POSITION_REVERSE[result.dealPosition] ?: result.dealPosition ?: "",
                        previousSolution       = SOLUTION_REVERSE[result.previousSolution] ?: result.previousSolution ?: "",
                        counterpartyMultiplier = COUNTERPARTY_REVERSE[result.counterpartyMultiplier] ?: result.counterpartyMultiplier ?: "",
                        responseSpeed          = RESPONSE_SPEED_REVERSE[result.responseSpeed] ?: result.responseSpeed ?: "",
                        isProposalSent         = result.isProposalSent,
                        proposalDate           = result.proposalDate,
                        competitorCount        = result.competitorCount,
                        visitSummary           = result.summary ?: ""
                    )
                }
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun onReportDateChanged(date: String)         { _uiState.update { it.copy(reportDate = date) } }
    fun onStatusToggle(enabled: Boolean)          { _uiState.update { it.copy(isStatusUpdateEnabled = enabled) } }
    fun onNewStatusSelected(status: String)       { _uiState.update { it.copy(newStatus = status) } }
    fun onOpportunitySelected(score: String)      { _uiState.update { it.copy(opportunityScore = score) } }
    fun onDealPositionChanged(value: String)      { _uiState.update { it.copy(dealPosition = value) } }
    fun onPreviousSolutionChanged(value: String)  { _uiState.update { it.copy(previousSolution = value) } }
    fun onCounterpartyMultiplierChanged(v: String){ _uiState.update { it.copy(counterpartyMultiplier = v) } }
    fun onResponseSpeedChanged(value: String)     { _uiState.update { it.copy(responseSpeed = value) } }
    fun onProposalToggle(sent: Boolean)           { _uiState.update { it.copy(isProposalSent = sent) } }
    fun onProposalDateChanged(date: String)       { _uiState.update { it.copy(proposalDate = date) } }
    fun onCompetitorCountChanged(delta: Int)      {
        _uiState.update { it.copy(competitorCount = (it.competitorCount + delta).coerceAtLeast(0)) }
    }
    fun onSummaryChanged(text: String)            { _uiState.update { it.copy(visitSummary = text) } }

    fun save() {
        val currentState = _uiState.value

        if (currentState.visitSummary.isBlank()) {
            _uiState.update { it.copy(error = "กรุณากรอกสรุปการเข้าพบ") }
            return
        }
        if (currentState.activityId.isNullOrBlank()) {
            _uiState.update { it.copy(error = "ไม่พบรหัสนัดหมาย") }
            return
        }

        val finalCustId = if (custId.isNotBlank()) custId else currentState.project?.custId ?: ""
        if (finalCustId.isBlank()) {
            _uiState.update { it.copy(error = "ไม่พบข้อมูลลูกค้า") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            val s = _uiState.value
            val user = authRepo.currentUser()
            val userName = user?.fullName ?: user?.userId ?: "Unknown User"

            // 1. บันทึก ActivityResult ลง Local DB + API
            val resultToSave = ActivityResult(
                activityId             = s.activityId!!,
                createdBy              = user?.userId,
                reportDate             = s.reportDate,
                newStatus              = if (s.isStatusUpdateEnabled) s.newStatus else null,
                opportunityScore       = s.opportunityScore,
                dealPosition           = DEAL_POSITION_MAP[s.dealPosition] ?: s.dealPosition.ifBlank { null },
                previousSolution       = SOLUTION_MAP[s.previousSolution] ?: s.previousSolution.ifBlank { null },
                counterpartyMultiplier = COUNTERPARTY_MAP[s.counterpartyMultiplier] ?: s.counterpartyMultiplier.ifBlank { null },
                responseSpeed          = RESPONSE_SPEED_MAP[s.responseSpeed] ?: s.responseSpeed.ifBlank { null },
                isProposalSent         = s.isProposalSent,
                proposalDate           = s.proposalDate,
                competitorCount        = s.competitorCount,
                summary                = s.visitSummary
            )

            val saveResult = activityRepo.saveActivityResult(resultToSave)
            if (saveResult.isFailure) {
                _uiState.update {
                    it.copy(isSaving = false, error = "บันทึกผลไม่สำเร็จ: ${saveResult.exceptionOrNull()?.message}")
                }
                return@launch
            }

            // 2. อัปเดต appointment → plan_status = completed + note
            val actResult = activityRepo.updateActivity(
                s.activityId!!,
                mapOf<String, Any>(
                    "plan_status" to "completed",
                    "note"        to s.visitSummary
                )
            )
            if (actResult.isFailure) {
                _uiState.update {
                    it.copy(isSaving = false, error = "อัปเดตนัดหมายไม่สำเร็จ: ${actResult.exceptionOrNull()?.message}")
                }
                return@launch
            }

            // 3. อัปเดต project status/score (ถ้าเปิด toggle หรือมี score)
            if (!s.projectId.isNullOrBlank()) {
                val pUpdates = mutableMapOf<String, String>()
                if (s.isStatusUpdateEnabled && s.newStatus.isNotBlank()) {
                    pUpdates["project_status"] = s.newStatus
                }
                s.opportunityScore?.let { pUpdates["opportunity_score"] = it }

                if (pUpdates.isNotEmpty()) {
                    projectRepo.updateProjectFields(
                        projectId = s.projectId,
                        fields    = pUpdates,
                        updatedBy = userName
                    )
                }
            }

            _uiState.update { it.copy(isSaving = false, isSaved = true) }
        }
    }
}