package com.example.pp68_salestrackingapp.ui.viewmodels.activity

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pp68_salestrackingapp.data.model.ActivityResult
import com.example.pp68_salestrackingapp.data.model.PlanItemDto
import com.example.pp68_salestrackingapp.data.model.Project
import com.example.pp68_salestrackingapp.data.repository.ActivityRepository
import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import com.example.pp68_salestrackingapp.data.repository.ProjectRepository
import com.example.pp68_salestrackingapp.utils.DraftStore
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

    // ✅ เช็คลิสต์วัตถุประสงค์ของนัดหมาย — ย้ายมาจาก ActivityDetailScreen เดิม ให้ติ๊กพร้อมกับ
    // ตอนเขียนสรุปแทนที่จะเป็นอีกหน้าที่ต้องแวะก่อน (ว่างเปล่าเมื่อเป็น STANDALONE ที่ไม่มีนัดหมายผูกอยู่)
    val planItems: List<PlanItemDto> = emptyList(),
    val selectedItemIds: Set<Int> = emptySet(),
    // true หลังกดบันทึกแล้วยังมีข้อ 4-7 ที่ไม่ได้เลือก — ให้หน้าจอกางแท็บที่ผิดและขึ้นข้อความใต้ข้อนั้น
    val showRequiredErrors: Boolean = false,

    // ✅ รองรับ version history ของบันทึกผลการขาย
    val resultGroupId: String? = null,
    val version: Int = 1,
    val isReadOnlyVersion: Boolean = false, // true = กำลังดู version เก่า (ไม่ใช่ล่าสุด) แก้ไขไม่ได้

    val draftAvailable: Boolean = false,

    // ✅ ผูกโครงการเพิ่มตอนบันทึกผล — เฉพาะนัดหมายที่ไม่ได้ผูกโครงการไว้แต่แรก (ดู onProjectSelected/saveQuickProject)
    val projectOptions: List<Pair<String, String>> = emptyList(),
    val isLoadingProjectOptions: Boolean = false,
    val isQuickAddProjectOpen: Boolean = false,
    val quickAddProjectName: String = "",
    val quickAddProjectStatus: String = "",
    val isSavingQuickProject: Boolean = false,
    val quickAddProjectError: String? = null
)

// ไม่รวมรูป (photos) — Uri ท้องถิ่น/ไฟล์ที่อัปโหลดแล้วกู้คืนข้ามเซสชันไม่ได้อย่างปลอดภัย ผู้ใช้ต้อง
// แนบรูปใหม่เองถ้ากู้คืนฉบับร่าง ส่วนอื่นที่เป็นแค่ข้อความ/ตัวเลข/ตัวเลือกกู้คืนได้ตามปกติ
data class SalesResultDraft(
    // ผูกโครงการเพิ่มตอนบันทึกผล (onProjectSelected/saveQuickProject) ต้องนับเป็นการแก้ไขด้วย —
    // saveQuickProject() เขียนโครงการจริงลง server แล้ว ถ้าไม่ track ตรงนี้ ผู้ใช้กดย้อนกลับจะไม่มี
    // คำเตือนใดๆ ทั้งที่เพิ่งสร้างโครงการทิ้งไว้ลอยๆ ไม่ได้ผูกกับอะไรเลย
    val projectId: String? = null,
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
    val lossReason: String = "",
    val otherLossReason: String = "",
    val selectedItemIds: Set<Int> = emptySet()
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
    private val authRepo: AuthRepository,
    private val draftStore: DraftStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(SalesResultUiState())
    val uiState: StateFlow<SalesResultUiState> = _uiState

    private var custId: String? = null

    private var baseline: SalesResultDraft = SalesResultDraft()
    private var pendingDraft: SalesResultDraft? = null

    private fun SalesResultUiState.toDraft() = SalesResultDraft(
        projectId, isStatusUpdateEnabled, newStatus, opportunityScore, dealPosition, previousSolution,
        counterpartyMultiplier, responseSpeed, isProposalSent, proposalDate, competitorCount,
        dmInvolved, visitSummary, lossReason, otherLossReason, selectedItemIds
    )

    // เวอร์ชันเก่าดูอย่างเดียวแก้ไม่ได้อยู่แล้ว ไม่ต้องมี draft/dirty-check
    private fun draftKey(): String? {
        val s = _uiState.value
        if (s.isReadOnlyVersion) return null
        val id = s.activityId ?: s.projectId ?: s.resultId ?: return null
        return "sales_result:$id"
    }

    fun checkForDraft() {
        val key = draftKey() ?: return
        val draft = draftStore.load(key, SalesResultDraft::class.java) ?: return
        pendingDraft = draft
        _uiState.update { it.copy(draftAvailable = true) }
    }

    fun isDirty(): Boolean = draftKey() != null && _uiState.value.toDraft() != baseline

    fun saveDraft() { draftKey()?.let { draftStore.save(it, _uiState.value.toDraft()) } }

    fun discardDraft() { draftKey()?.let { draftStore.clear(it) } }

    fun restoreDraft() {
        val d = pendingDraft ?: return
        _uiState.update {
            it.copy(
                projectId = d.projectId ?: it.projectId,
                isStatusUpdateEnabled = d.isStatusUpdateEnabled,
                newStatus = d.newStatus,
                opportunityScore = d.opportunityScore,
                dealPosition = d.dealPosition,
                previousSolution = d.previousSolution,
                counterpartyMultiplier = d.counterpartyMultiplier,
                responseSpeed = d.responseSpeed,
                isProposalSent = d.isProposalSent,
                proposalDate = d.proposalDate,
                competitorCount = d.competitorCount,
                dmInvolved = d.dmInvolved,
                visitSummary = d.visitSummary,
                lossReason = d.lossReason,
                otherLossReason = d.otherLossReason,
                selectedItemIds = d.selectedItemIds,
                draftAvailable = false
            )
        }
        // draft ที่กู้มาอาจมีโครงการที่เพิ่งผูก/สร้างด่วนไว้ — ต้องโหลดข้อมูลโครงการนั้นกลับมาด้วย
        // ไม่งั้น projectId ตั้งแล้วแต่ project/currentStatus ยังว่างอยู่เหมือนไม่ได้ผูกอะไรเลย
        d.projectId?.let { viewModelScope.launch { loadProjectData(it) } }
        pendingDraft = null
    }

    fun dismissDraftPrompt() {
        pendingDraft = null
        _uiState.update { it.copy(draftAvailable = false) }
    }

    val lossReasonOptions = com.example.pp68_salestrackingapp.utils.LossReasons.OPTIONS

    init {
        val pId = savedStateHandle.get<String>("projectId")
        val idParam = savedStateHandle.get<String>("activityId")

        if (idParam.isNullOrBlank()) {
            _uiState.update { it.copy(projectId = pId, mode = ResultMode.STANDALONE) }
            viewModelScope.launch {
                pId?.let { loadProjectData(it) }
                baseline = _uiState.value.toDraft()
                checkForDraft()
            }
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
                loadChecklist(id)
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
                    result.activityId?.let { loadChecklist(it) }
                }
            }
            // นัดหมายที่ไม่ได้ผูกโครงการไว้แต่แรก ให้ผูกเพิ่มได้ตอนบันทึกผล (ดู onProjectSelected/saveQuickProject)
            if (_uiState.value.mode == ResultMode.FROM_APPOINTMENT && _uiState.value.projectId == null) {
                loadProjectOptions()
            }
            baseline = _uiState.value.toDraft()
            checkForDraft()
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private fun loadProjectOptions() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingProjectOptions = true) }
            projectRepo.getAllProjectsFlow().collect { list ->
                _uiState.update {
                    it.copy(
                        projectOptions = list.map { p -> p.projectId to p.projectName },
                        isLoadingProjectOptions = false
                    )
                }
            }
        }
    }

    // เลือกโครงการที่มีอยู่แล้วมาผูกกับนัดหมายนี้ — ตัวนัดหมายจริงจะถูกอัปเดตให้ผูกด้วยตอนกดบันทึกผล (ดู save())
    fun onProjectSelected(projectId: String) {
        _uiState.update { it.copy(projectId = projectId) }
        viewModelScope.launch { loadProjectData(projectId) }
    }

    fun onQuickAddProjectToggle(isOpen: Boolean) {
        _uiState.update {
            it.copy(
                isQuickAddProjectOpen = isOpen,
                quickAddProjectName = "",
                quickAddProjectStatus = "",
                quickAddProjectError = null
            )
        }
    }

    fun onQuickAddProjectNameChanged(value: String) {
        _uiState.update { it.copy(quickAddProjectName = value, quickAddProjectError = null) }
    }

    fun onQuickAddProjectStatusChanged(value: String) {
        _uiState.update { it.copy(quickAddProjectStatus = value, quickAddProjectError = null) }
    }

    // สร้างโครงการด่วน — กรอกแค่ชื่อ+สถานะ ฟิลด์อื่นเติมทีหลังได้ที่หน้าโครงการ (เหมือน saveQuickCustomer
    // ใน AddProjectViewModel) แล้วผูกโครงการที่สร้างใหม่นี้เข้ากับนัดหมายทันที
    fun saveQuickProject() {
        val s = _uiState.value
        if (s.quickAddProjectName.isBlank() || s.quickAddProjectStatus.isBlank()) {
            _uiState.update { it.copy(quickAddProjectError = "กรุณาระบุชื่อโครงการและสถานะให้ครบถ้วน") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingQuickProject = true) }
            val userId = authRepo.currentUser()?.userId
            val newProject = Project(
                projectId = "",
                projectName = s.quickAddProjectName.trim(),
                projectStatus = s.quickAddProjectStatus,
                branchId = authRepo.currentUser()?.teamId,
                createBy = userId
            )
            projectRepo.createProject(newProject, userId ?: "").fold(
                onSuccess = { created ->
                    _uiState.update {
                        it.copy(
                            projectOptions = it.projectOptions + (created.projectId to created.projectName),
                            projectId = created.projectId,
                            isQuickAddProjectOpen = false,
                            isSavingQuickProject = false,
                            quickAddProjectName = "",
                            quickAddProjectStatus = ""
                        )
                    }
                    loadProjectData(created.projectId)
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isSavingQuickProject = false, quickAddProjectError = e.message) }
                }
            )
        }
    }

    // ✅ เช็คลิสต์วัตถุประสงค์ของนัดหมาย — โหลดเฉพาะโหมด FROM_APPOINTMENT ที่มี activityId จริง
    private suspend fun loadChecklist(activityId: String) {
        val items = activityRepo.getPlanItems(activityId).getOrDefault(emptyList())
        _uiState.update {
            it.copy(
                planItems       = items,
                selectedItemIds = items.filter { item -> item.isDone }.map { item -> item.masterId }.toSet()
            )
        }
    }

    // ก็อปพฤติกรรมจาก ActivityDetailViewModel.toggleItem() เดิม — ติ๊กแล้วซิงค์ขึ้น server ทันที
    // ทีละรายการ ไม่รอรวมส่งตอนกด "บันทึก" เพื่อให้ไม่หายถ้าปิดแอประหว่างทาง
    fun toggleChecklistItem(masterId: Int) {
        val current = _uiState.value.selectedItemIds.toMutableSet()
        if (current.contains(masterId)) current.remove(masterId) else current.add(masterId)
        _uiState.update { it.copy(selectedItemIds = current) }

        val activityId = _uiState.value.activityId ?: return
        val isDone = current.contains(masterId)
        viewModelScope.launch {
            activityRepo.updatePlanItemStatus(activityId, masterId, isDone)
            activityRepo.updateChecklistItem(activityId, masterId, isDone)
        }
    }

    // ข้อ 4-7 เพิ่งกลายเป็นข้อบังคับ บันทึกที่สร้างก่อนหน้านั้นจึงมีค่าว่างได้ — ถ้าปล่อยว่างไว้
    // เซลส์ที่เปิดมาแก้แค่คำผิดจะติด validation ทั้งที่จำคำตอบตอนนั้นไม่ได้แล้ว จึงเติมค่าตั้งต้นให้
    // แทน (ข้อ 4-6 ได้ "ยังระบุไม่ได้", ข้อ 7 ได้ค่ากลาง) ค่าที่เคยบันทึกไว้จริงไม่ถูกแตะ
    private fun restoreChoice(stored: String?, reverse: Map<String, String>, fallback: String): String =
        if (stored.isNullOrBlank()) fallback else reverse[stored] ?: stored

    private fun applyResultToState(result: ActivityResult) {
        // ✅ W4: อ่านตรงจากสองฟิลด์ที่แยกแล้ว (lossReason=รหัส, lossReasonNote=ข้อความอิสระ)
        // เดางวนต่อเฉพาะแถวเก่าที่ backend ยังไม่ได้ migrate
        val (reason, other) = when {
            !result.lossReasonNote.isNullOrBlank() ->
                (result.lossReason ?: com.example.pp68_salestrackingapp.utils.LossReasons.OTHER) to result.lossReasonNote
            result.lossReason.isNullOrBlank() -> "" to ""
            result.lossReason in lossReasonOptions -> result.lossReason to ""
            else -> com.example.pp68_salestrackingapp.utils.LossReasons.OTHER to result.lossReason
        }

        _uiState.update {
            it.copy(
                resultId               = result.resultId,
                reportDate             = result.reportDate ?: it.reportDate,
                newStatus              = STATUS_REVERSE[result.newStatus] ?: result.newStatus ?: "",
                isStatusUpdateEnabled  = !result.newStatus.isNullOrBlank(),
                opportunityScore       = OPPORTUNITY_REVERSE[result.opportunityScore] ?: result.opportunityScore ?: it.opportunityScore,
                dealPosition           = restoreChoice(result.dealPosition, DEAL_POSITION_REVERSE, UNDETERMINED_LABEL),
                previousSolution       = restoreChoice(result.previousSolution, SOLUTION_REVERSE, UNDETERMINED_LABEL),
                counterpartyMultiplier = restoreChoice(result.counterpartyMultiplier, COUNTERPARTY_REVERSE, UNDETERMINED_LABEL),
                responseSpeed          = restoreChoice(result.responseSpeed, RESPONSE_SPEED_REVERSE, RESPONSE_SPEED_DEFAULT),
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

    // W6: เดิมฟังก์ชันนี้ห่อด้วย viewModelScope.launch{} ของตัวเอง (fire-and-forget) ทำให้ผู้เรียก
    // ไม่มีทาง await ผลลัพธ์ก่อนไปทำ logic ถัดไปได้ (เช่น applyResultToState ที่ควรรันหลังจากนี้เสมอ
    // เพื่อให้ค่าจริงของ result ทับ prefill จากโครงการได้ถูกต้อง) เปลี่ยนเป็น suspend fun ธรรมดา
    // ให้ผู้เรียกที่อยู่ใน coroutine อยู่แล้ว (init{}/loadInitialData) await ได้ตรงๆ
    private suspend fun loadProjectData(pId: String) {
        projectRepo.getProjectById(pId).fold(
            onSuccess = { p ->
                custId = p.custId
                _uiState.update {
                    it.copy(
                        project = p,
                        currentStatus = p.projectStatus ?: "",
                        opportunityScore = if (it.opportunityScore.isNullOrBlank()) p.opportunityScore else it.opportunityScore,
                        // W6-2: ดึงปัจจัยข้อ 4-7 ที่เคยตอบไว้ของโครงการนี้มา prefill — เฉพาะฟิลด์
                        // ที่ยังว่าง (ไม่แตะถ้ามีคำตอบจริงของ result นี้โดยเฉพาะอยู่แล้วจาก applyResultToState)
                        dealPosition = it.dealPosition.ifBlank { DEAL_POSITION_REVERSE[p.dealPosition] ?: "" },
                        previousSolution = it.previousSolution.ifBlank { SOLUTION_REVERSE[p.previousSolution] ?: "" },
                        counterpartyMultiplier = it.counterpartyMultiplier.ifBlank { COUNTERPARTY_REVERSE[p.counterpartyType] ?: "" },
                        responseSpeed = it.responseSpeed.ifBlank { RESPONSE_SPEED_REVERSE[p.responseSpeed] ?: "" }
                    )
                }
            },
            onFailure = { e ->
                _uiState.update { it.copy(error = "โหลดข้อมูลโครงการไม่สำเร็จ: ${e.message}") }
            }
        )
    }

    // W6-2: โครงการนี้เคยมีคำตอบข้อ 4-7 ครบแล้วหรือยัง — ถ้าครบ หน้านี้ไม่ต้องถามซ้ำ (แก้ได้แค่หน้าโครงการ)
    // ถ้ายังไม่ครบ (โครงการใหม่/ยังไม่เคยตอบ) หน้านี้ยังต้องถามและบังคับตอบเหมือนเดิม
    private fun projectHasAllDealFactors(p: Project?): Boolean =
        p != null && !p.dealPosition.isNullOrBlank() && !p.previousSolution.isNullOrBlank() &&
            !p.counterpartyType.isNullOrBlank() && !p.responseSpeed.isNullOrBlank()

    companion object {
        const val MAX_PHOTOS = 5

        // ข้อ 4-6 บังคับเลือก จึงต้องมีทางออกให้เซลส์ที่ยังไม่รู้คำตอบ — ไม่งั้นจะกดมั่วเพื่อให้ผ่าน
        // แล้วได้ข้อมูลเพี้ยนแทนที่จะได้ค่าว่าง
        const val UNDETERMINED_LABEL = "ยังระบุไม่ได้"

        // W5b: ตัวเลือกทั้ง 4 ข้อนี้มาจาก DealFactors (master data + fallback) แทนการ hardcode
        // ในไฟล์นี้ตรงๆ — ชื่อ/ชนิดคงเดิมทุกตัว (Map<Label, Code>) เพื่อไม่ต้องแก้จุดเรียกใช้เดิม
        val DEAL_POSITION_MAP: Map<String, String>
            get() = com.example.pp68_salestrackingapp.utils.DealFactors.labelToCode(
                com.example.pp68_salestrackingapp.utils.DealFactors.DEAL_POSITION
            )
        val SOLUTION_MAP: Map<String, String>
            get() = com.example.pp68_salestrackingapp.utils.DealFactors.labelToCode(
                com.example.pp68_salestrackingapp.utils.DealFactors.PREVIOUS_SOLUTION
            )
        val COUNTERPARTY_MAP: Map<String, String>
            get() = com.example.pp68_salestrackingapp.utils.DealFactors.labelToCode(
                com.example.pp68_salestrackingapp.utils.DealFactors.COUNTERPARTY_TYPE
            )
        val RESPONSE_SPEED_MAP: Map<String, String>
            get() = com.example.pp68_salestrackingapp.utils.DealFactors.labelToCode(
                com.example.pp68_salestrackingapp.utils.DealFactors.RESPONSE_SPEED
            )
        // ข้อ 7 ไม่มีตัวเลือก "ยังระบุไม่ได้" จึงใช้ค่ากลางเป็นค่าตั้งต้นให้บันทึกเก่าที่ยังว่าง
        const val RESPONSE_SPEED_DEFAULT = "ปกติ"
        val STATUS_MAP = mapOf(
            "Lead" to "Lead", "New Project" to "New Project", "Quotation" to "Quotation",
            "Bidding" to "Bidding", "Make a Decision" to "Make a Decision", "Assured" to "Assured",
            "PO" to "PO", "Lost" to "Lost", "Failed" to "Failed"
        )
        val OPPORTUNITY_MAP = mapOf("สูง (HOT)" to "HOT", "กลาง (WARM)" to "WARM", "ต่ำ (COLD)" to "COLD")

        // W5b: อ่านจาก DealFactors สดทุกครั้ง (ไม่ใช่ derive จาก MAP ด้านบนตอน class-init เพราะ
        // companion object เริ่มทำงานก่อน SyncManager เรียก applyServerData() เสมอ — ถ้า derive
        // ครั้งเดียวตอน init ค่าจะถูกแช่แข็งไว้ที่ fallback ตลอดไป ไม่มีวันเห็นข้อมูลจาก server เลย)
        val DEAL_POSITION_REVERSE: Map<String, String>
            get() = com.example.pp68_salestrackingapp.utils.DealFactors.codeToLabel(
                com.example.pp68_salestrackingapp.utils.DealFactors.DEAL_POSITION
            )
        val SOLUTION_REVERSE: Map<String, String>
            get() = com.example.pp68_salestrackingapp.utils.DealFactors.codeToLabel(
                com.example.pp68_salestrackingapp.utils.DealFactors.PREVIOUS_SOLUTION
            )
        val COUNTERPARTY_REVERSE: Map<String, String>
            get() = com.example.pp68_salestrackingapp.utils.DealFactors.codeToLabel(
                com.example.pp68_salestrackingapp.utils.DealFactors.COUNTERPARTY_TYPE
            )
        val RESPONSE_SPEED_REVERSE: Map<String, String>
            get() = com.example.pp68_salestrackingapp.utils.DealFactors.codeToLabel(
                com.example.pp68_salestrackingapp.utils.DealFactors.RESPONSE_SPEED
            )
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
        when (s.mode) {
            ResultMode.FROM_APPOINTMENT ->
                if (s.activityId.isNullOrBlank()) { _uiState.update { it.copy(error = "ไม่พบรหัสนัดหมาย") }; return }
            ResultMode.STANDALONE ->
                if (s.projectId.isNullOrBlank()) { _uiState.update { it.copy(error = "ไม่พบรหัสโครงการ") }; return }
        }
        if (s.isReadOnlyVersion) { _uiState.update { it.copy(error = "กำลังดูเวอร์ชันเก่า ไม่สามารถแก้ไขได้") }; return }
        if (s.visitSummary.isBlank()) { _uiState.update { it.copy(error = "กรุณากรอกสรุปการเข้าพบ") }; return }
        if (s.photos.any { it.isUploading }) { _uiState.update { it.copy(error = "กรุณารอให้อัปโหลดรูปให้เสร็จก่อนบันทึก") }; return }

        // ข้อ 4-7 วิเคราะห์ดีลผูกกับโครงการ (เขียนลง project_code) — นัดหมาย/แผนที่ไม่ได้ผูกโครงการ
        // ไม่มีที่เก็บค่าพวกนี้ จึงไม่ต้องถามและไม่บังคับตอบ
        // W6-2: โครงการที่เคยตอบครบแล้วก็ไม่ต้องบังคับซ้ำ — ค่าที่ prefill มาจาก loadProjectData
        // จะถูกส่งไปพร้อม result อยู่แล้ว, ให้บังคับเฉพาะโครงการใหม่/ยังไม่เคยตอบเท่านั้น
        // ข้อ 4-7 อยู่ในแท็บที่พับไว้ ผู้ใช้จึงอาจไม่เคยเห็นว่ายังไม่ได้ตอบ — ตั้ง flag ให้หน้าจอกางแท็บ
        // ที่ยังขาดและชี้ทีละข้อ แทนที่จะขึ้นแค่ข้อความรวมแล้วผู้ใช้หาไม่เจอ
        if (!s.projectId.isNullOrBlank() && !projectHasAllDealFactors(s.project) &&
            (s.dealPosition.isBlank() || s.previousSolution.isBlank() ||
             s.counterpartyMultiplier.isBlank() || s.responseSpeed.isBlank())
        ) {
            _uiState.update {
                it.copy(showRequiredErrors = true, error = "กรุณาตอบข้อ 4-7 ในหัวข้อวิเคราะห์ข้อมูลให้ครบ")
            }
            return
        }

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
                // ✅ W4: ส่งรหัสกับข้อความอิสระแยกกัน แทนการยุบเป็นก้อนเดียว
                val isLostOrFailed = s.isStatusUpdateEnabled && (s.newStatus == "Lost" || s.newStatus == "Failed")
                val finalLossReason = if (isLostOrFailed) s.lossReason else null
                val finalLossReasonNote = if (isLostOrFailed && s.lossReason == com.example.pp68_salestrackingapp.utils.LossReasons.OTHER) {
                    s.otherLossReason
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
                    lossReason             = finalLossReason,
                    lossReasonNote         = finalLossReasonNote
                )

                val saveResult = when (s.mode) {
                    ResultMode.FROM_APPOINTMENT -> activityRepo.saveActivityResult(resultToSave, photoUrls)
                    ResultMode.STANDALONE -> activityRepo.saveStandaloneResult(s.projectId!!, resultToSave, photoUrls)
                }

                if (saveResult.isSuccess) {
                    // ✅ นี่คือจุดเดียวที่นัดหมายเปลี่ยนเป็น completed — ไม่มีปุ่ม "Finish" แยกอีกต่อไป
                    // (เดิมอยู่ที่ ActivityDetailViewModel.finishActivity() ถูกเรียกจากปุ่มที่ไม่ได้
                    // บันทึกข้อมูลอะไรเลย) standalone ไม่มีนัดหมายจริงให้ปิด จึงข้ามขั้นนี้ไป
                    if (s.mode == ResultMode.FROM_APPOINTMENT) {
                        s.activityId?.let { activityRepo.finishActivity(it, s.selectedItemIds.toList(), note = null) }
                        // ผูกโครงการเพิ่มตอนบันทึกผล (นัดหมายที่ไม่ได้ผูกไว้แต่แรก) ต้องอัปเดตกลับไปที่
                        // ตัวนัดหมายจริงด้วย ไม่งั้นเปิดนัดหมายนี้ครั้งหน้าจะยังว่างเหมือนเดิม
                        if (s.activityId != null && s.projectId != null) {
                            activityRepo.updateActivity(s.activityId, mapOf("project_code" to s.projectId))
                        }
                    }
                    discardDraft()
                    _uiState.update { it.copy(isSaving = false, isSaved = true) }
                } else {
                    _uiState.update { it.copy(isSaving = false, error = saveResult.exceptionOrNull()?.message) }
                }
            } catch (e: Exception) { _uiState.update { it.copy(isSaving = false, error = e.message) } }
        }
    }
}
