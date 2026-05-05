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
    val resultId: String? = null, // ✅ เพิ่ม resultId ใน state
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
    // EXIF data
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
        val aId = savedStateHandle.get<String>("activityId")
        val rId = savedStateHandle.get<String>("resultId") // ✅ รับ resultId จาก Navigation

        Log.d("SalesResult", "init: projectId=$pId, activityId=$aId, resultId=$rId")

        val mode = if (aId.isNullOrBlank()) ResultMode.STANDALONE else ResultMode.FROM_APPOINTMENT
        _uiState.update { it.copy(projectId = pId, activityId = aId, resultId = rId, mode = mode) }

        when (mode) {
            ResultMode.STANDALONE -> {
                pId?.let { loadProjectData(it) }
                rId?.let { loadResultById(it) } // ✅ ถ้ามี rId ให้โหลดข้อมูลรายงานนั้นๆ ขึ้นมาแสดง
            }
            ResultMode.FROM_APPOINTMENT -> aId?.let { loadActivityData(it) }
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

    // ✅ ฟังก์ชันสำหรับโหลดข้อมูลรายงานโดยใช้ ID
    private fun loadResultById(rId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = activityRepo.getResultById(rId)
            if (result != null) {
                applyResultToUi(result)
            }
            _uiState.update { it.copy(isLoading = false) }
        }
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

            val result = activityRepo.getActivityResult(aId)
            if (result != null) {
                applyResultToUi(result)
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    // ✅ ฟังก์ชันช่วยในการนำข้อมูลจาก Entity มาใส่ใน UI State (ลดการเขียนโค้ดซ้ำ)
    private fun applyResultToUi(result: ActivityResult) {
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
            "Lead"             to "Lead",
            "New Project"     to "New Project",
            "Quotation"         to "Quotation",
            "Bidding"         to "Bidding",
            "Decision Making"  to "Make a Decision",
            "Assured"        to "Assured",
            "PO"           to "PO",
            "Lost"              to "Lost",
            "Failed"        to "Failed"
        )

        val OPPORTUNITY_MAP = mapOf(
            "สูง (HOT)"  to "HOT",
            "กลาง (WARM)" to "WARM",
            "ต่ำ (COLD)"  to "COLD"
        )

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
                        val distance = calculateHaversine(
                            plannedLat, plannedLng,
                            latLong[0].toDouble(), latLong[1].toDouble()
                        )
                        distance <= 500.0
                    } else null
                } else null

                _uiState.update {
                    it.copy(
                        photoTakenAt         = dateTaken,
                        photoLat             = lat,
                        photoLng             = lng,
                        photoDeviceModel     = deviceModel,
                        isPhotoLocationValid = isLocationValid
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("SalesResult", "Error extracting EXIF: ${e.message}")
        }
    }

    private fun calculateHaversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371e3 // Earth radius in meters
        val phi1 = lat1 * PI / 180
        val phi2 = lat2 * PI / 180
        val deltaPhi = (lat2 - lat1) * PI / 180
        val deltaLambda = (lon2 - lon1) * PI / 180

        val a = sin(deltaPhi / 2).pow(2) +
                cos(phi1) * cos(phi2) *
                sin(deltaLambda / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return r * c
    }

    fun onProposalDateChanged(date: String)       { _uiState.update { it.copy(proposalDate = date) } }
    fun onCompetitorCountChanged(delta: Int)      {
        _uiState.update { it.copy(competitorCount = (it.competitorCount + delta).coerceAtLeast(0)) }
    }
    fun onSummaryChanged(text: String)            { _uiState.update { it.copy(visitSummary = text) } }

    fun uploadPhoto(context: Context) {
        val s = _uiState.value
        val uri = s.photoUri
        if (uri == null) return  // ← เช็คแค่ uri เท่านั้น

        // ✅ STANDALONE ใช้ projectId แทน activityId
        val uploadId = s.activityId ?: s.projectId
        if (uploadId == null) return

        viewModelScope.launch {
            _uiState.update { it.copy(isUploadingPhoto = true, error = null) }

            val bytes = try {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            } catch (e: Exception) { null }

            if (bytes == null) {
                _uiState.update { it.copy(error = "ไม่สามารถอ่านไฟล์รูปภาพได้", isUploadingPhoto = false) }
                return@launch
            }

            activityRepo.uploadVisitPhoto(uploadId, bytes).onSuccess { url ->
                _uiState.update { it.copy(photoUrl = url, isUploadingPhoto = false) }
            }.onFailure { e ->
                _uiState.update { it.copy(error = "อัปโหลดรูปไม่สำเร็จ: ${e.message}", isUploadingPhoto = false) }
            }
        }
    }

    fun save() {
        val s = _uiState.value
        android.util.Log.d("SalesResult", "save() called, mode=${s.mode}, projectId=${s.projectId}, activityId=${s.activityId}")

        if (s.visitSummary.isBlank()) {
            _uiState.update { it.copy(error = "กรุณากรอกสรุปการเข้าพบ") }
            return
        }

        if (s.isSaving) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }

            val user = authRepo.currentUser()

            try {
                val finalLossReason = if (s.isStatusUpdateEnabled && (s.newStatus == "Lost" || s.newStatus == "Failed")) {
                    if (s.lossReason == "อื่น ๆ") s.otherLossReason else s.lossReason
                } else null

                val resultToSave = ActivityResult(
                    resultId               = s.resultId ?: java.util.UUID.randomUUID().toString(), // ✅ ถ้ามี ID เดิมให้ใช้เดิม (เป็นการแก้) ถ้าไม่มีให้สร้างใหม่
                    activityId             = s.activityId,
                    projectId              = s.projectId,
                    createdBy              = user?.userId,
                    reportDate             = s.reportDate,
                    newStatus              = if (s.isStatusUpdateEnabled) STATUS_MAP[s.newStatus] ?: s.newStatus.ifBlank { null } else null,
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
                    ResultMode.FROM_APPOINTMENT -> {
                        if (s.activityId.isNullOrBlank()) {
                            _uiState.update { it.copy(isSaving = false, error = "ไม่พบรหัสนัดหมาย") }
                            return@launch
                        }
                        activityRepo.saveActivityResult(resultToSave)
                    }
                    ResultMode.STANDALONE -> {
                        if (s.projectId.isNullOrBlank()) {
                            _uiState.update { it.copy(isSaving = false, error = "ไม่พบรหัสโครงการ") }
                            return@launch
                        }
                        activityRepo.saveStandaloneResult(s.projectId!!, resultToSave)
                    }
                }

                if (saveResult.isSuccess) {
                    _uiState.update { it.copy(isSaving = false, isSaved = true) }
                } else {
                    _uiState.update {
                        it.copy(isSaving = false, error = saveResult.exceptionOrNull()?.message)
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }
}
