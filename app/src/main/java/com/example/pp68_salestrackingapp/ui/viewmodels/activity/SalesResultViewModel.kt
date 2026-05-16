package com.example.pp68_salestrackingapp.ui.viewmodels.activity

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pp68_salestrackingapp.data.model.ActivityResult
import com.example.pp68_salestrackingapp.data.model.Project
import com.example.pp68_salestrackingapp.data.repository.ActivityRepository
import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import com.example.pp68_salestrackingapp.data.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.*

data class SalesResultUiState(
    val mode: ResultMode = ResultMode.FROM_APPOINTMENT,
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
    val dmInvolved: Boolean = false,
    val visitSummary: String = "",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null,
    val photoUri: Uri? = null,
    val photoUrl: String? = null,
    val isUploadingPhoto: Boolean = false,
    val isPhotoLocationValid: Boolean? = null,
    val photoTakenAt: String? = null,
    val photoLat: Double? = null,
    val photoLng: Double? = null,
    val photoDeviceModel: String? = null,
    val lossReason: String = "",
    val otherLossReason: String = "",
    val lossReasonError: String? = null
)

enum class ResultMode {
    FROM_APPOINTMENT,
    STANDALONE
}

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

    val lossReasonOptions = listOf(
        "ผลิตไม่ได้/ผลิตไม่ทัน",
        "เทคโนโลยีไม่ผ่าน",
        "สู้ราคาไม่ไหว",
        "อื่น ๆ"
    )

    init {
        val pId = savedStateHandle.get<String>("projectId")
        val idParam = savedStateHandle.get<String>("activityId")

        if (idParam.isNullOrBlank()) {
            // กรณีเปิดจาก FAB (สร้างใหม่)
            _uiState.update { it.copy(projectId = pId, mode = ResultMode.STANDALONE) }
            pId?.let { loadProjectData(it) }
        } else {
            // กรณีดูรายงาน หรือบันทึกต่อนัดหมาย
            loadInitialData(idParam)
        }
    }

    private fun loadInitialData(id: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            // 1. ลองหาว่าเป็นนัดหมายหรือไม่
            val actResult = activityRepo.getActivityById(id)
            val activity = actResult.getOrNull()?.firstOrNull()
            
            if (activity != null) {
                // ✅ เป็นการบันทึกต่อนัดหมาย
                _uiState.update { it.copy(
                    activityId = id, 
                    projectId = activity.projectId, 
                    mode = ResultMode.FROM_APPOINTMENT,
                    reportDate = activity.activityDate
                ) }
                activity.projectId?.let { loadProjectData(it) }
                
                // โหลดผลที่เคยบันทึกไว้ (ถ้ามี)
                activityRepo.getActivityResult(id)?.let { applyResultToState(it) }
            } else {
                // 2. ถ้าไม่ใช่นัดหมาย ลองหาว่าเป็น ResultId (Standalone) หรือไม่
                val result = activityRepo.getResultById(id)
                if (result != null) {
                    // ✅ เป็นการดูรายงาน Standalone
                    _uiState.update { it.copy(
                        projectId = result.projectId, 
                        mode = ResultMode.STANDALONE,
                        reportDate = result.reportDate ?: LocalDate.now().toString()
                    ) }
                    applyResultToState(result)
                    result.projectId?.let { loadProjectData(it) }
                }
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private fun applyResultToState(result: ActivityResult) {
        val existingReason = result.lossReason ?: ""
        val (reason, other) = if (existingReason in lossReasonOptions) {
            existingReason to ""
        } else if (existingReason.isNotBlank()) {
            "อื่น ๆ" to existingReason
        } else {
            "" to ""
        }

        _uiState.update {
            it.copy(
                reportDate             = result.reportDate ?: it.reportDate,
                newStatus              = STATUS_REVERSE[result.newStatus] ?: result.newStatus ?: "",
                isStatusUpdateEnabled  = !result.newStatus.isNullOrBlank(),
                opportunityScore       = OPPORTUNITY_REVERSE[result.opportunityScore] ?: result.opportunityScore ?: it.opportunityScore,
                dealPosition           = DEAL_POSITION_REVERSE[result.dealPosition] ?: result.dealPosition ?: "",
                previousSolution       = SOLUTION_REVERSE[result.previousSolution] ?: result.previousSolution ?: "",
                counterpartyMultiplier = COUNTERPARTY_REVERSE[result.counterpartyMultiplier] ?: result.counterpartyMultiplier ?: "",
                responseSpeed          = RESPONSE_SPEED_REVERSE[result.responseSpeed] ?: result.responseSpeed ?: "",
                isProposalSent         = result.isProposalSent,
                proposalDate           = result.proposalDate,
                competitorCount        = result.competitorCount,
                dmInvolved             = result.dmInvolved,
                visitSummary           = result.summary ?: "",
                photoUrl               = result.photoUrl,
                photoTakenAt           = result.photoTakenAt,
                photoLat               = result.photoLat,
                photoLng               = result.photoLng,
                photoDeviceModel       = result.photoDeviceModel,
                lossReason             = reason,
                otherLossReason        = other
            )
        }
    }

    private fun loadProjectData(pId: String) {
        viewModelScope.launch {
            projectRepo.getProjectById(pId).onSuccess { p ->
                custId = p.custId
                _uiState.update {
                    it.copy(
                        project = p,
                        currentStatus = p.projectStatus ?: "",
                        opportunityScore = if (it.opportunityScore.isNullOrBlank()) p.opportunityScore else it.opportunityScore
                    )
                }
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
        val STATUS_MAP = mapOf(
            "Lead" to "Lead", "New Project" to "New Project", "Quotation" to "Quotation",
            "Bidding" to "Bidding", "Decision Making" to "Make a Decision", "Assured" to "Assured",
            "PO" to "PO", "Lost" to "Lost", "Failed" to "Failed"
        )
        val OPPORTUNITY_MAP = mapOf("สูง (HOT)" to "HOT", "กลาง (WARM)" to "WARM", "ต่ำ (COLD)" to "COLD")

        val DEAL_POSITION_REVERSE    = DEAL_POSITION_MAP.entries.associate { (k, v) -> v to k }
        val SOLUTION_REVERSE         = SOLUTION_MAP.entries.associate { (k, v) -> v to k }
        val COUNTERPARTY_REVERSE     = COUNTERPARTY_MAP.entries.associate { (k, v) -> v to k }
        val RESPONSE_SPEED_REVERSE   = RESPONSE_SPEED_MAP.entries.associate { (k, v) -> v to k }
        val STATUS_REVERSE           = STATUS_MAP.entries.associate { (k, v) -> v to k }
        val OPPORTUNITY_REVERSE      = OPPORTUNITY_MAP.entries.associate { (k, v) -> v to k }
    }

    fun onReportDateChanged(date: String)         { _uiState.update { it.copy(reportDate = date) } }
    fun onStatusToggle(enabled: Boolean)          { _uiState.update { it.copy(isStatusUpdateEnabled = enabled) } }
    fun onNewStatusSelected(status: String)       { _uiState.update { it.copy(newStatus = status, lossReasonError = null) } }
    fun onOpportunitySelected(score: String)      { _uiState.update { it.copy(opportunityScore = score) } }
    fun onDealPositionChanged(value: String)      { _uiState.update { it.copy(dealPosition = value) } }
    fun onPreviousSolutionChanged(value: String)  { _uiState.update { it.copy(previousSolution = value) } }
    fun onCounterpartyMultiplierChanged(v: String){ _uiState.update { it.copy(counterpartyMultiplier = v) } }
    fun onResponseSpeedChanged(value: String)     { _uiState.update { it.copy(responseSpeed = value) } }
    fun onProposalToggle(sent: Boolean)           { _uiState.update { it.copy(isProposalSent = sent) } }
    fun onDmToggle(involved: Boolean)             { _uiState.update { it.copy(dmInvolved = involved) } }
    fun onLossReasonChanged(value: String)        { _uiState.update { it.copy(lossReason = value, lossReasonError = null) } }
    fun onOtherLossReasonChanged(value: String)   { _uiState.update { it.copy(otherLossReason = value, lossReasonError = null) } }

    fun onPhotoPicked(context: Context, uri: Uri) {
        _uiState.update { it.copy(photoUri = uri, photoUrl = null) }
        extractExifData(context, uri)
    }

    private fun extractExifData(context: Context, uri: Uri) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val exif = ExifInterface(inputStream)
                val dateTaken = exif.getAttribute(ExifInterface.TAG_DATETIME)
                val deviceModel = exif.getAttribute(ExifInterface.TAG_MODEL)
                val latLong = FloatArray(2)
                val hasGps = exif.getLatLong(latLong)
                val lat = if (hasGps) latLong[0].toDouble() else null
                val lng = if (hasGps) latLong[1].toDouble() else null

                val isLocationValid: Boolean? = if (hasGps) {
                    val plannedLat = _uiState.value.project?.projectLat?.toDouble()
                    val plannedLng = _uiState.value.project?.projectLong?.toDouble()
                    if (plannedLat != null && plannedLng != null) {
                        calculateHaversine(plannedLat, plannedLng, latLong[0].toDouble(), latLong[1].toDouble()) <= 500.0
                    } else null
                } else null

                _uiState.update { it.copy(photoTakenAt = dateTaken, photoLat = lat, photoLng = lng, photoDeviceModel = deviceModel, isPhotoLocationValid = isLocationValid) }
            }
        } catch (e: Exception) {
            Log.e("SalesResult", "Error extracting EXIF: ${e.message}")
        }
    }

    private fun calculateHaversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371e3
        val phi1 = lat1 * PI / 180; val phi2 = lat2 * PI / 180
        val deltaPhi = (lat2 - lat1) * PI / 180; val deltaLambda = (lon2 - lon1) * PI / 180
        val a = sin(deltaPhi / 2).pow(2) + cos(phi1) * cos(phi2) * sin(deltaLambda / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    fun onProposalDateChanged(date: String)       { _uiState.update { it.copy(proposalDate = date) } }
    fun onCompetitorCountChanged(delta: Int)      { _uiState.update { it.copy(competitorCount = (it.competitorCount + delta).coerceAtLeast(0)) } }
    fun onSummaryChanged(text: String)            { _uiState.update { it.copy(visitSummary = text) } }

    fun uploadPhoto(context: Context) {
        val s = _uiState.value; val aId = s.activityId; val uri = s.photoUri
        if (uri == null || aId == null) return
        viewModelScope.launch {
            _uiState.update { it.copy(isUploadingPhoto = true, error = null) }
            val bytes = try { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } } catch (e: Exception) { null }
            if (bytes == null) { _uiState.update { it.copy(error = "ไม่สามารถอ่านไฟล์รูปภาพได้", isUploadingPhoto = false) }; return@launch }
            activityRepo.uploadVisitPhoto(aId, bytes).onSuccess { url -> _uiState.update { it.copy(photoUrl = url, isUploadingPhoto = false) } }
                .onFailure { e -> _uiState.update { it.copy(error = "อัปโหลดรูปไม่สำเร็จ: ${e.message}", isUploadingPhoto = false) } }
        }
    }

    fun save() {
        val s = _uiState.value
        if (s.visitSummary.isBlank()) { _uiState.update { it.copy(error = "กรุณากรอกสรุปการเข้าพบ") }; return }
        if (s.isStatusUpdateEnabled && (s.newStatus == "Lost" || s.newStatus == "Failed")) {
            if (s.lossReason.isBlank()) { _uiState.update { it.copy(lossReasonError = "กรุณาระบุเหตุผลที่ไม่ได้งาน", error = "กรุณาระบุเหตุผลที่ไม่ได้งาน") }; return }
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            val user = authRepo.currentUser()
            try {
                val finalLossReason = if (s.isStatusUpdateEnabled && (s.newStatus == "Lost" || s.newStatus == "Failed")) {
                    if (s.lossReason == "อื่น ๆ") s.otherLossReason else s.lossReason
                } else null

                val resultToSave = ActivityResult(
                    resultId               = java.util.UUID.randomUUID().toString(),
                    activityId             = if (s.mode == ResultMode.FROM_APPOINTMENT) s.activityId else null,
                    projectId              = s.projectId,
                    createdBy              = user?.userId,
                    reportDate             = s.reportDate,
                    newStatus              = if (s.isStatusUpdateEnabled) STATUS_MAP[s.newStatus] else null,
                    opportunityScore       = OPPORTUNITY_MAP[s.opportunityScore] ?: s.opportunityScore,
                    dealPosition           = DEAL_POSITION_MAP[s.dealPosition] ?: s.dealPosition.ifBlank { null },
                    previousSolution       = SOLUTION_MAP[s.previousSolution] ?: s.previousSolution.ifBlank { null },
                    counterpartyMultiplier = COUNTERPARTY_MAP[s.counterpartyMultiplier] ?: s.counterpartyMultiplier.ifBlank { null },
                    responseSpeed          = RESPONSE_SPEED_MAP[s.responseSpeed] ?: s.responseSpeed.ifBlank { null },
                    isProposalSent         = s.isProposalSent,
                    proposalDate           = s.proposalDate,
                    competitorCount        = s.competitorCount,
                    dmInvolved             = s.dmInvolved,
                    summary                = s.visitSummary,
                    photoUrl               = s.photoUrl,
                    photoTakenAt           = s.photoTakenAt,
                    photoLat               = s.photoLat,
                    photoLng               = s.photoLng,
                    photoDeviceModel       = s.photoDeviceModel,
                    lossReason             = finalLossReason
                )
                
                val saveResult = when (s.mode) {
                    ResultMode.FROM_APPOINTMENT -> activityRepo.saveActivityResult(resultToSave)
                    ResultMode.STANDALONE -> activityRepo.saveStandaloneResult(s.projectId!!, resultToSave)
                }

                if (saveResult.isSuccess) { _uiState.update { it.copy(isSaving = false, isSaved = true) } }
                else { _uiState.update { it.copy(isSaving = false, error = saveResult.exceptionOrNull()?.message) } }
            } catch (e: Exception) { _uiState.update { it.copy(isSaving = false, error = e.message) } }
        }
    }
}
