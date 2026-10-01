package com.example.pp68_salestrackingapp.ui.viewmodels.project

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pp68_salestrackingapp.data.model.ProjectFactorSnapshot
import com.example.pp68_salestrackingapp.data.repository.ProjectRepository
import com.example.pp68_salestrackingapp.utils.DealFactors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ฟิลด์หนึ่งที่เปลี่ยนในการแก้ครั้งหนึ่ง — ได้จากการ diff snapshot สองแถวที่ติดกัน
// fieldKey ใช้ชื่อคอลัมน์ฝั่ง DB เพื่อให้ชั้น UI แปลงเป็นหัวข้อ/ค่าที่อ่านรู้เรื่องได้ที่เดียว
data class FactorChange(
    val fieldKey: String,
    val oldValue: String?,
    val newValue: String?
)

// การแก้ 1 ครั้ง (1 snapshot) พร้อมรายการฟิลด์ที่เปลี่ยนในครั้งนั้น — แก้ 3 ข้อพร้อมกันจะเป็น
// รายการเดียวที่มี 3 บรรทัด ไม่ใช่ 3 รายการแยกกันเหมือนตอนเก็บ log ทีละฟิลด์
data class FactorHistoryEntry(
    val changedAt: String,
    val changedBy: String?,
    val changes: List<FactorChange>
)

data class EditProjectFactorsUiState(
    val projectId: String = "",
    val projectName: String = "",
    // ข้อ 4-7 เก็บเป็น "ป้าย" (label) เหมือนหน้าบันทึกผล แปลงเป็นรหัสตอนบันทึก
    val dealPosition: String = "",
    val previousSolution: String = "",
    val counterpartyType: String = "",
    val responseSpeed: String = "",
    // ข้อ 8-9
    val isProposalSent: Boolean = false,
    val proposalDate: String? = null,
    val competitorCount: String = "0",
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null,
    val history: List<FactorHistoryEntry> = emptyList(),
    val historyError: String? = null
)

@HiltViewModel
class EditProjectFactorsViewModel @Inject constructor(
    private val projectRepo: ProjectRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditProjectFactorsUiState())
    val uiState: StateFlow<EditProjectFactorsUiState> = _uiState

    val dealPositionOptions     get() = DealFactors.labelToCode(DealFactors.DEAL_POSITION).keys.toList()
    val previousSolutionOptions get() = DealFactors.labelToCode(DealFactors.PREVIOUS_SOLUTION).keys.toList()
    val counterpartyTypeOptions get() = DealFactors.labelToCode(DealFactors.COUNTERPARTY_TYPE).keys.toList()
    val responseSpeedOptions    get() = DealFactors.labelToCode(DealFactors.RESPONSE_SPEED).keys.toList()

    fun load(projectId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            projectRepo.getProjectById(projectId).fold(
                onSuccess = { p ->
                    _uiState.update {
                        it.copy(
                            projectId = p.projectId,
                            projectName = p.projectName,
                            // รหัส -> ป้าย เพื่อโชว์ใน dropdown (ค่าที่ไม่รู้จัก/ว่าง กลายเป็นยังไม่เลือก)
                            dealPosition = DealFactors.codeToLabel(DealFactors.DEAL_POSITION)[p.dealPosition] ?: "",
                            previousSolution = DealFactors.codeToLabel(DealFactors.PREVIOUS_SOLUTION)[p.previousSolution] ?: "",
                            counterpartyType = DealFactors.codeToLabel(DealFactors.COUNTERPARTY_TYPE)[p.counterpartyType] ?: "",
                            responseSpeed = DealFactors.codeToLabel(DealFactors.RESPONSE_SPEED)[p.responseSpeed] ?: "",
                            isProposalSent = p.isProposalSent ?: false,
                            proposalDate = p.proposalDate,
                            competitorCount = (p.competitorCount ?: 0).toString(),
                            isLoading = false
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
            )
            loadHistory(projectId)
        }
    }

    private suspend fun loadHistory(projectId: String) {
        projectRepo.getFactorHistory(projectId).fold(
            onSuccess = { logs ->
                _uiState.update { it.copy(history = buildHistoryEntries(logs), historyError = null) }
            },
            // ประวัติอ่านจาก server เท่านั้น ออฟไลน์อยู่ก็ยังแก้ค่าปัจจุบันได้ปกติ แค่ไม่เห็นประวัติ
            onFailure = { _uiState.update { it.copy(history = emptyList(), historyError = "ดูประวัติได้เมื่อออนไลน์เท่านั้น") } }
        )
    }

    // แปลง snapshot เป็นรายการ "อะไรเปลี่ยน" — backend ส่งมาเรียงใหม่สุดก่อน (changed_at DESC)
    // แถวที่ i จึงเทียบกับแถวที่ i+1 ซึ่งเก่ากว่าหนึ่งขั้น ส่วนแถวเก่าสุดไม่มีอะไรให้เทียบ
    // ถือเป็นค่าตั้งต้น (old = null ทุกฟิลด์ที่มีค่า)
    //
    // ถ้า diff แล้วไม่มีฟิลด์ไหนต่างเลยจะไม่แสดงรายการนั้น — ปกติ trigger กันไว้แล้วไม่ให้เกิด
    // snapshot ซ้ำ แต่กันไว้อีกชั้นกันแถวแปลกๆ จากข้อมูลเก่าโชว์เป็นรายการเปล่า
    internal fun buildHistoryEntries(logs: List<com.example.pp68_salestrackingapp.data.model.ProjectFactorLog>): List<FactorHistoryEntry> =
        logs.mapIndexedNotNull { index, log ->
            val previous = logs.getOrNull(index + 1)?.factors
            val changes = diffSnapshots(previous, log.factors)
            if (changes.isEmpty()) null
            else FactorHistoryEntry(changedAt = log.changedAt, changedBy = log.changedBy, changes = changes)
        }

    private fun diffSnapshots(old: ProjectFactorSnapshot?, new: ProjectFactorSnapshot): List<FactorChange> {
        val changes = mutableListOf<FactorChange>()
        fun add(key: String, oldValue: Any?, newValue: Any?) {
            if (oldValue?.toString() != newValue?.toString()) {
                changes += FactorChange(key, oldValue?.toString(), newValue?.toString())
            }
        }
        add("deal_position", old?.dealPosition, new.dealPosition)
        add("current_solution", old?.previousSolution, new.previousSolution)
        add("counterparty_type", old?.counterpartyType, new.counterpartyType)
        add("response_speed", old?.responseSpeed, new.responseSpeed)
        add("is_proposal_sent", old?.isProposalSent, new.isProposalSent)
        add("proposal_date", old?.proposalDate, new.proposalDate)
        add("competitor_count", old?.competitorCount, new.competitorCount)
        return changes
    }

    fun onDealPositionChange(v: String)     = _uiState.update { it.copy(dealPosition = v, error = null) }
    fun onPreviousSolutionChange(v: String) = _uiState.update { it.copy(previousSolution = v, error = null) }
    fun onCounterpartyTypeChange(v: String) = _uiState.update { it.copy(counterpartyType = v, error = null) }
    fun onResponseSpeedChange(v: String)    = _uiState.update { it.copy(responseSpeed = v, error = null) }
    fun onProposalDateChange(v: String)     = _uiState.update { it.copy(proposalDate = v, error = null) }

    // ปิดสวิตช์ "ส่งใบเสนอราคาแล้ว" ต้องล้างวันที่ทิ้งด้วย ไม่งั้นเหลือวันที่ค้างขัดกับสถานะ
    fun onProposalSentToggle(sent: Boolean) = _uiState.update {
        it.copy(isProposalSent = sent, proposalDate = if (sent) it.proposalDate else null, error = null)
    }

    // กันพิมพ์อย่างอื่นนอกจากตัวเลข และกันค่ายาวเกินจริง (99 คู่แข่งก็เกินพอสำหรับดีลจริงแล้ว)
    fun onCompetitorCountChange(v: String) = _uiState.update {
        val digits = v.filter { c -> c.isDigit() }.take(2)
        it.copy(competitorCount = digits, error = null)
    }

    fun save() {
        val s = _uiState.value
        if (s.projectId.isBlank()) return

        if (s.dealPosition.isBlank() || s.previousSolution.isBlank() ||
            s.counterpartyType.isBlank() || s.responseSpeed.isBlank()
        ) {
            _uiState.update { it.copy(error = "กรุณาเลือกปัจจัยของดีลให้ครบ") }
            return
        }
        if (s.isProposalSent && s.proposalDate.isNullOrBlank()) {
            _uiState.update { it.copy(error = "กรุณาระบุวันที่ส่งใบเสนอราคา") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }

            // ป้าย -> รหัส ก่อนส่งขึ้น server (ค่าที่หาไม่เจอในตารางแปลว่าเป็น master data
            // ที่ถูกลบไปแล้ว — กันส่งค่าเพี้ยนด้วยการหยุดไว้ก่อน ไม่ส่ง null ทับของเดิม)
            val dealPositionCode = DealFactors.labelToCode(DealFactors.DEAL_POSITION)[s.dealPosition]
            val solutionCode = DealFactors.labelToCode(DealFactors.PREVIOUS_SOLUTION)[s.previousSolution]
            val counterpartyCode = DealFactors.labelToCode(DealFactors.COUNTERPARTY_TYPE)[s.counterpartyType]
            val responseSpeedCode = DealFactors.labelToCode(DealFactors.RESPONSE_SPEED)[s.responseSpeed]
            if (dealPositionCode == null || solutionCode == null ||
                counterpartyCode == null || responseSpeedCode == null
            ) {
                _uiState.update { it.copy(isSaving = false, error = "ตัวเลือกปัจจัยไม่ถูกต้อง กรุณาเลือกใหม่") }
                return@launch
            }

            // ⚠️ proposal_date ส่ง "" (ไม่ใช่ null) เพื่อล้างค่า — Gson ตัดคีย์ที่เป็น null ออกจาก
            // body ทั้งหมด (ไม่ได้เปิด serializeNulls) ส่ง null ไปจะเท่ากับ "ไม่แตะฟิลด์นี้"
            // ฝั่ง backend อ่าน "" เป็นการล้างค่าให้ (ดู ProjectRepositoryImpl.update)
            val fields = mapOf(
                "deal_position" to dealPositionCode,
                "current_solution" to solutionCode,
                "counterparty_type" to counterpartyCode,
                "response_speed" to responseSpeedCode,
                "is_proposal_sent" to s.isProposalSent,
                "proposal_date" to (if (s.isProposalSent) (s.proposalDate ?: "") else ""),
                "competitor_count" to (s.competitorCount.toIntOrNull() ?: 0),
                "updated_at" to java.time.Instant.now().toString()
            )

            projectRepo.updateProjectFields(s.projectId, fields).fold(
                onSuccess = { _uiState.update { it.copy(isSaving = false, isSaved = true) } },
                onFailure = { e -> _uiState.update { it.copy(isSaving = false, error = e.message ?: "บันทึกไม่สำเร็จ") } }
            )
        }
    }
}
