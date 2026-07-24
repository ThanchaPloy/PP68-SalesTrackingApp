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
    val resultId: String? = null, // ✅ เพิ่มเพื่อรองรับการแก้ไขบันทึกเดิม
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
    val photos: List<ResultPhoto> = emptyList(),
    val lossReason: String = "",
    val otherLossReason: String = "",
    val lossReasonError: String? = null,

    // ✅ รองรับ version history ของบันทึกผลการขาย
    val resultGroupId: String? = null,
    val version: Int = 1,
    val isReadOnlyVersion: Boolean = false // true = กำลังดู version เก่า (ไม่ใช่ล่าสุด) แก้ไขไม่ได้
)

enum class ResultMode {
    FROM_APPOINTMENT,
    STANDALONE
}

// ✅ รูปภาพยืนยันการเข้าพบ 1 รูป — ถ่ายจากกล้องเท่านั้น ไม่รับรูปจากแกลเลอรี/อินเทอร์เน็ต
// เก็บ metadata (เวลาถ่าย/พิกัด/รุ่นเครื่อง) เฉพาะรูปแรก (index 0) เท่านั้น ตามที่ activity_result รองรับ
data class ResultPhoto(
    val localUri: Uri? = null,
    val url: String? = null,
    val isUploading: Boolean = false,
    val takenAt: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val deviceModel: String? = null,
    val isLocationValid: Boolean? = null
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
            _uiState.update { it.copy(projectId = pId, mode = ResultMode.STANDALONE) }
            pId?.let { loadProjectData(it) }
        } else {
            loadInitialData(idParam)
        }
    }

    private fun loadInitialData(id: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            // 1. ลองหาว่าเป็น Appointment ID หรือไม่
            val actResult = activityRepo.getActivityById(id)
            val activity = actResult.getOrNull()?.firstOrNull()
            
            if (activity != null) {
                _uiState.update { it.copy(
                    activityId = id, 
                    projectId = activity.projectId, 
                    mode = ResultMode.FROM_APPOINTMENT,
                    reportDate = activity.activityDate
                ) }
                activity.projectId?.let { loadProjectData(it) }
                // ดึงผลลัพธ์ล่าสุดที่ผูกกับ Appointment นี้ (ถ้ามี)
                activityRepo.getActivityResult(id)?.let { applyResultToState(it); loadPhotosForResult(it) }
            } else {
                // 2. ถ้าไม่ใช่ อาจเป็น Result ID โดยตรง (กรณี Standalone หรือคลิกจาก History)
                val result = activityRepo.getResultById(id)
                if (result != null) {
                    _uiState.update { it.copy(
                        resultId = result.resultId,
                        projectId = result.projectId,
                        mode = if (result.activityId != null) ResultMode.FROM_APPOINTMENT else ResultMode.STANDALONE,
                        activityId = result.activityId,
                        reportDate = result.reportDate ?: LocalDate.now().toString()
                    ) }
                    applyResultToState(result)
                    loadPhotosForResult(result)
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
                resultId               = result.resultId,
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
                lossReason             = reason,
                otherLossReason        = other,
                resultGroupId          = result.resultGroupId,
                version                = result.version,
                isReadOnlyVersion      = !result.isLatest
            )
        }
    }

    // ✅ โหลดรูปทั้งหมดจากตาราง activity_result_photo; รูปเก่าก่อนมี feature นี้จะมีแค่ photo_url เดียวบน activity_result
    private suspend fun loadPhotosForResult(result: ActivityResult) {
        val childUrls = activityRepo.getResultPhotos(result.resultId)
        val urls = childUrls.ifEmpty { listOfNotNull(result.photoUrl.takeUnless { it.isNullOrBlank() }) }
        val photos = urls.mapIndexed { index, url ->
            if (index == 0) {
                ResultPhoto(
                    url = url,
                    takenAt = result.photoTakenAt,
                    lat = result.photoLat,
                    lng = result.photoLng,
                    deviceModel = result.photoDeviceModel
                )
            } else {
                ResultPhoto(url = url)
            }
        }
        _uiState.update { it.copy(photos = photos) }
    }

    private fun loadProjectData(pId: String) {
        viewModelScope.launch {
            projectRepo.getProjectById(pId).fold(
                onSuccess = { p ->
                    custId = p.custId
                    _uiState.update {
                        it.copy(
                            project = p,
                            currentStatus = p.projectStatus ?: "",
                            opportunityScore = if (it.opportunityScore.isNullOrBlank()) p.opportunityScore else it.opportunityScore
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(error = "โหลดข้อมูลโครงการไม่สำเร็จ: ${e.message}") }
                }
            )
        }
    }

    companion object {
        const val MAX_PHOTOS = 5

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
            "Bidding" to "Bidding", "Make a Decision" to "Make a Decision", "Assured" to "Assured",
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
    fun onStatusToggle(enabled: Boolean)          { _uiState.update { it.copy(isStatusUpdateEnabled = enabled, lossReasonError = null) } }
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

    // ✅ ถ่ายรูปใหม่จากกล้อง — เพิ่มเข้า slot ถัดไป (สูงสุด MAX_PHOTOS รูป) แล้วอัปโหลดทันที
    fun onPhotoCaptured(context: Context, uri: Uri) {
        if (_uiState.value.photos.size >= MAX_PHOTOS) return
        addPhoto(context, uri, extractExifData(context, uri))
    }

    // ✅ เลือกได้หลายรูปพร้อมกันจากอัลบั้ม (สูงสุดเท่าที่ยัง slot ว่าง) — รับเฉพาะรูปที่มี EXIF ของกล้องจริง (ยี่ห้อ/รุ่นเครื่อง)
    // เพื่อกันรูป screenshot หรือรูปที่โหลด/แชร์มาจากอินเทอร์เน็ต-แชท ซึ่งปกติ EXIF จะถูกลบไปแล้ว
    fun onPhotosPicked(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        val remainingSlots = MAX_PHOTOS - _uiState.value.photos.size
        val toProcess = uris.take(remainingSlots)
        val skippedOverLimit = uris.size - toProcess.size
        var skippedNonCamera = 0

        toProcess.forEach { uri ->
            val exif = extractExifData(context, uri)
            if (exif.deviceModel.isNullOrBlank()) skippedNonCamera++
            else addPhoto(context, uri, exif)
        }

        val messages = buildList {
            if (skippedNonCamera > 0) add("ข้าม $skippedNonCamera รูปที่ไม่ใช่รูปจากกล้อง")
            if (skippedOverLimit > 0) add("เพิ่มรูปได้สูงสุด $MAX_PHOTOS รูป ข้าม $skippedOverLimit รูปที่เกิน")
        }
        if (messages.isNotEmpty()) _uiState.update { it.copy(error = messages.joinToString(" ")) }
    }

    private fun addPhoto(context: Context, uri: Uri, exif: ExifData) {
        val index = _uiState.value.photos.size
        _uiState.update {
            it.copy(photos = it.photos + ResultPhoto(
                localUri = uri,
                isUploading = true,
                takenAt = exif.takenAt,
                lat = exif.lat,
                lng = exif.lng,
                deviceModel = exif.deviceModel,
                isLocationValid = exif.isLocationValid
            ))
        }
        uploadPhotoAt(context, index, uri)
    }

    fun onRemovePhoto(index: Int) {
        _uiState.update { s -> s.copy(photos = s.photos.filterIndexed { i, _ -> i != index }) }
    }

    private data class ExifData(
        val takenAt: String? = null,
        val lat: Double? = null,
        val lng: Double? = null,
        val deviceModel: String? = null,
        val isLocationValid: Boolean? = null
    )

    private fun extractExifData(context: Context, uri: Uri): ExifData {
        return try {
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

                ExifData(dateTaken, lat, lng, deviceModel, isLocationValid)
            } ?: ExifData()
        } catch (e: Exception) {
            Log.e("SalesResult", "Error extracting EXIF: ${e.message}")
            ExifData()
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

    private fun uploadPhotoAt(context: Context, index: Int, uri: Uri) {
        val s = _uiState.value
        val uploadId = s.activityId ?: s.projectId
        if (uploadId == null) {
            updatePhotoAt(index) { it.copy(isUploading = false) }
            return
        }
        viewModelScope.launch {
            val bytes = try { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } } catch (e: Exception) { null }
            if (bytes == null) {
                updatePhotoAt(index) { it.copy(isUploading = false) }
                _uiState.update { it.copy(error = "ไม่สามารถอ่านไฟล์รูปภาพได้") }
                return@launch
            }

            activityRepo.uploadVisitPhoto(uploadId, bytes).onSuccess { url ->
                updatePhotoAt(index) { it.copy(url = url, isUploading = false) }
            }.onFailure { e ->
                updatePhotoAt(index) { it.copy(isUploading = false) }
                _uiState.update { it.copy(error = "อัปโหลดรูปไม่สำเร็จ: ${e.message}") }
            }
        }
    }

    private fun updatePhotoAt(index: Int, transform: (ResultPhoto) -> ResultPhoto) {
        _uiState.update { s ->
            val list = s.photos.toMutableList()
            if (index in list.indices) list[index] = transform(list[index])
            s.copy(photos = list)
        }
    }

    fun save() {
        val s = _uiState.value
        if (s.activityId.isNullOrBlank()) { _uiState.update { it.copy(error = "ไม่พบรหัสนัดหมาย") }; return }
        if (s.isReadOnlyVersion) { _uiState.update { it.copy(error = "กำลังดูเวอร์ชันเก่า ไม่สามารถแก้ไขได้") }; return }
        if (s.visitSummary.isBlank()) { _uiState.update { it.copy(error = "กรุณากรอกสรุปการเข้าพบ") }; return }
        if (s.photos.any { it.isUploading }) { _uiState.update { it.copy(error = "กรุณารอให้อัปโหลดรูปให้เสร็จก่อนบันทึก") }; return }

        if (s.isStatusUpdateEnabled) {
            if (s.newStatus.isBlank()) {
                _uiState.update { it.copy(error = "กรุณาเลือกสถานะใหม่") }
                return
            }
            
            if (s.newStatus == "Lost" || s.newStatus == "Failed") {
                if (s.lossReason.isBlank()) { 
                    _uiState.update { it.copy(lossReasonError = "กรุณาระบุเหตุผลที่ไม่ได้งาน", error = "กรุณาระบุเหตุผลที่ไม่ได้งาน") }
                    return 
                }
                if (s.lossReason == "อื่น ๆ" && s.otherLossReason.isBlank()) {
                    _uiState.update { it.copy(lossReasonError = "กรุณาระบุเหตุผลอื่น ๆ", error = "กรุณาระบุเหตุผลอื่น ๆ") }
                    return
                }
            }
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            val user = authRepo.currentUser()
            try {
                val finalLossReason = if (s.isStatusUpdateEnabled && (s.newStatus == "Lost" || s.newStatus == "Failed")) {
                    if (s.lossReason == "อื่น ๆ") s.otherLossReason else s.lossReason
                } else null

                val finalResultId = s.resultId ?: ""
                val photoUrls = s.photos.mapNotNull { it.url }
                val cover = s.photos.firstOrNull()

                val resultToSave = ActivityResult(
                    resultId               = finalResultId,
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
                    photoUrl               = cover?.url,
                    photoTakenAt           = cover?.takenAt,
                    photoLat               = cover?.lat,
                    photoLng               = cover?.lng,
                    photoDeviceModel       = cover?.deviceModel,
                    lossReason             = finalLossReason
                )

                val saveResult = when (s.mode) {
                    ResultMode.FROM_APPOINTMENT -> activityRepo.saveActivityResult(resultToSave, photoUrls)
                    ResultMode.STANDALONE -> activityRepo.saveStandaloneResult(s.projectId!!, resultToSave, photoUrls)
                }

                if (saveResult.isSuccess) { _uiState.update { it.copy(isSaving = false, isSaved = true) } }
                else { _uiState.update { it.copy(isSaving = false, error = saveResult.exceptionOrNull()?.message) } }
            } catch (e: Exception) { _uiState.update { it.copy(isSaving = false, error = e.message) } }
        }
    }
}
