package com.example.pp68_salestrackingapp.ui.viewmodels.project

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pp68_salestrackingapp.data.model.Project
import com.example.pp68_salestrackingapp.data.remote.ApiService
import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import com.example.pp68_salestrackingapp.data.repository.BranchRepository
import com.example.pp68_salestrackingapp.data.repository.ContactRepository
import com.example.pp68_salestrackingapp.data.repository.CustomerRepository
import com.example.pp68_salestrackingapp.data.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.pp68_salestrackingapp.data.model.Customer
import com.example.pp68_salestrackingapp.utils.DraftStore
import java.util.UUID
import javax.inject.Inject
import com.example.pp68_salestrackingapp.utils.withUniqueLabels

data class AddProjectUiState(
    val projectId:              String? = null,
    val displayProjectId:       String  = "รอกดบันทึกเพื่อสร้างหมายเลข",
    val projectName:            String  = "",
    val branch:                 String  = "",
    val expectedValue:          String  = "",
    val startDate:              String? = null,
    val closeDate:              String? = null,
    val projectStatus:          String? = null,
    val opportunityScore:       String? = null, // ✅ เพิ่มฟิลด์ระดับความสำคัญ
    val locationText:           String  = "",
    val siteLat:                Double? = null,
    val siteLong:               Double? = null,
    val customerOptions:        List<Pair<String, String>> = emptyList(),
    val selectedCustomerId:     String? = null,
    val selectedCustomerName:   String? = null,
    val isLoadingCustomers:     Boolean = false,
    val customerSearchMessage:  String? = null,
    val contactOptions:         List<Pair<String, String>> = emptyList(),
    val selectedContactIds:     Set<String> = emptySet(),
    val isLoadingContacts:      Boolean = false,
    val teamOptions:            List<Pair<String, String>> = emptyList(),
    val selectedTeamId:         String? = null,
    val selectedTeamName:       String? = null,
    val isLoadingTeams:         Boolean = false,
    val billingBranchOptions:      List<Pair<String, String>> = emptyList(),
    val isLoadingBillingBranches:  Boolean = false,
    val selectedBillingBranchId:   String? = null,
    val selectedBillingBranchName: String? = null,
    val projectNameError:       String? = null,
    val customerError:          String? = null,
    val statusError:            String? = null,
    val billingBranchError:     String? = null,
    val isLoading:              Boolean = false,
    val isSaved:                Boolean = false,
    val saveError:    String? = null,
    val lossReason:             String  = "",
    val otherLossReason:        String  = "",
    val lossReasonError:        String? = null,
    // W6-2: ปัจจัยข้อ 4-7 — แก้ไขได้ทางเดียวคือที่นี่ หน้าบันทึกผลแค่ prefill มาโชว์เฉยๆ
    val dealPosition:           String  = "",
    val previousSolution:       String  = "",
    val counterpartyType:       String  = "",
    val responseSpeed:          String  = "",
    // Quick Add Customer
    val isQuickAddCustomerOpen: Boolean = false,
    val quickAddCompanyName:    String  = "",
    val quickAddCustType:       String  = "",
    // สร้างผู้ติดต่อด่วน — เจอบ่อยว่าเลือกบริษัทได้แต่คนที่ติดต่อจริงยังไม่มีในระบบ
    val isQuickAddContactOpen:  Boolean = false,
    val quickAddContactName:    String  = "",
    val quickAddContactPhone:   String  = "",
    val isSavingQuickContact:   Boolean = false,
    val quickAddContactError:   String? = null,
    val isSavingQuickCust:      Boolean = false,
    val draftAvailable: Boolean = false
)

// ไม่เก็บ selectedCustomerName/selectedTeamName/selectedBillingBranchName — เป็นแค่ label ที่ resolve
// จาก id แบบ async ทีหลัง (ดู loadProject) ไม่ใช่สิ่งที่ผู้ใช้กรอกเอง เก็บ id พออย่างเดียวพอสำหรับกู้คืน
data class AddProjectDraft(
    val projectName: String = "",
    val branch: String = "",
    val expectedValue: String = "",
    val startDate: String? = null,
    val closeDate: String? = null,
    val projectStatus: String? = null,
    val opportunityScore: String? = null,
    val siteLat: Double? = null,
    val siteLong: Double? = null,
    val selectedCustomerId: String? = null,
    val selectedContactIds: Set<String> = emptySet(),
    val selectedTeamId: String? = null,
    val selectedBillingBranchId: String? = null,
    val lossReason: String = "",
    val otherLossReason: String = "",
    val dealPosition: String = "",
    val previousSolution: String = "",
    val counterpartyType: String = "",
    val responseSpeed: String = ""
)

sealed class AddProjectEvent {
    data class LoadProject(val id: String)                        : AddProjectEvent()
    data class ProjectNameChanged(val value: String)              : AddProjectEvent()
    data class BranchChanged(val value: String)                   : AddProjectEvent()
    data class CustomerSelected(val id: String, val name: String) : AddProjectEvent()
    data class CustomerQueryChanged(val value: String)            : AddProjectEvent()
    data class ContactToggled(val id: String)                     : AddProjectEvent()
    data class ExpectedValueChanged(val value: String)            : AddProjectEvent()
    data class StartDateChanged(val value: String)                : AddProjectEvent()
    data class CloseDateChanged(val value: String)                : AddProjectEvent()
    data class StatusChanged(val value: String)                   : AddProjectEvent()
    data class OpportunityScoreChanged(val value: String)         : AddProjectEvent() // ✅ เพิ่ม Event
    data class TeamSelected(val id: String, val name: String)     : AddProjectEvent()
    data class BillingBranchSelected(val id: String, val name: String) : AddProjectEvent()
    data class LocationPicked(val lat: Double, val lng: Double)   : AddProjectEvent()
    data class LossReasonChanged(val value: String)               : AddProjectEvent()
    data class OtherLossReasonChanged(val value: String)          : AddProjectEvent()
    data class DealPositionChanged(val value: String)             : AddProjectEvent()
    data class PreviousSolutionChanged(val value: String)         : AddProjectEvent()
    data class CounterpartyTypeChanged(val value: String)         : AddProjectEvent()
    data class ResponseSpeedChanged(val value: String)            : AddProjectEvent()
    
    // Quick Add Events
    data class ToggleQuickAddCustomer(val isOpen: Boolean)        : AddProjectEvent()
    data class QuickAddCustomerChanged(val name: String, val type: String) : AddProjectEvent()
    object SaveQuickAddCustomer                                   : AddProjectEvent()
    data class ToggleQuickAddContact(val isOpen: Boolean)         : AddProjectEvent()
    data class QuickAddContactChanged(val name: String, val phone: String) : AddProjectEvent()
    object SaveQuickAddContact                                    : AddProjectEvent()

    object Save                                                   : AddProjectEvent()
    object CheckDraft         : AddProjectEvent()
    object RestoreDraft       : AddProjectEvent()
    object DismissDraftPrompt : AddProjectEvent()
}

/**
 * เลขสำหรับใส่ในช่องกรอก — Double.toString() เปลี่ยนเป็นรูปวิทยาศาสตร์เมื่อถึงสิบล้าน
 * (1.0E7) ผู้ใช้เปิดหน้าแก้ไขโครงการแล้วเจอค่าแบบนั้นในช่องมูลค่า แก้ต่อไม่ได้
 *
 * BigDecimal.valueOf ใช้ค่าตามที่พิมพ์ได้จริงของ Double ไม่ใช่ค่าไบนารีดิบ แล้ว toPlainString
 * รับประกันว่าไม่มีเลขยกกำลังออกมา ส่วน stripTrailingZeros ตัด .0 ของจำนวนเต็มทิ้ง
 */
internal fun Double.toPlainAmount(): String =
    java.math.BigDecimal.valueOf(this).stripTrailingZeros().toPlainString()

@HiltViewModel
class AddProjectViewModel @Inject constructor(
    private val projectRepo:  ProjectRepository,
    private val customerRepo: CustomerRepository,
    private val contactRepo:  ContactRepository,
    private val authRepo:     AuthRepository,
    private val branchRepo:   BranchRepository,
    private val apiService:   ApiService,
    private val draftStore:   DraftStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddProjectUiState())
    val uiState: StateFlow<AddProjectUiState> = _uiState
    private var leadCustomerOptions: List<Pair<String, String>> = emptyList()
    private var customerSearchJob: Job? = null

    private val draft = com.example.pp68_salestrackingapp.utils.DraftController(
        store = draftStore,
        type = AddProjectDraft::class.java,
        initialBaseline = AddProjectDraft(),
        keyOf = { "add_project:${_uiState.value.projectId ?: "new"}" },
        currentOf = { _uiState.value.toDraft() }
    )

    private fun AddProjectUiState.toDraft() = AddProjectDraft(
        projectName, branch, expectedValue, startDate, closeDate, projectStatus, opportunityScore,
        siteLat, siteLong, selectedCustomerId, selectedContactIds,
        selectedTeamId, selectedBillingBranchId,
        lossReason, otherLossReason, dealPosition, previousSolution, counterpartyType, responseSpeed
    )


    private fun checkForDraft() {
        if (draft.check()) _uiState.update { it.copy(draftAvailable = true) }
    }

    fun isDirty(): Boolean = draft.isDirty()

    fun saveDraft() = draft.save()

    fun discardDraft() = draft.discard()

    private fun restoreDraft() {
        val d = draft.takePending() ?: return
        _uiState.update {
            it.copy(
                projectName = d.projectName,
                branch = d.branch,
                expectedValue = d.expectedValue,
                startDate = d.startDate,
                closeDate = d.closeDate,
                projectStatus = d.projectStatus,
                opportunityScore = d.opportunityScore,
                siteLat = d.siteLat,
                siteLong = d.siteLong,
                selectedCustomerId = d.selectedCustomerId,
                selectedContactIds = d.selectedContactIds,
                selectedTeamId = d.selectedTeamId,
                selectedBillingBranchId = d.selectedBillingBranchId,
                lossReason = d.lossReason,
                otherLossReason = d.otherLossReason,
                dealPosition = d.dealPosition,
                previousSolution = d.previousSolution,
                counterpartyType = d.counterpartyType,
                responseSpeed = d.responseSpeed,
                draftAvailable = false
            )
        }
        // id อย่างเดียวไม่พอโชว์ผล ต้อง resolve ชื่อกลับมาเหมือนตอน loadProject()
        d.selectedCustomerId?.let { cId ->
            viewModelScope.launch {
                customerRepo.getCustomerById(cId).onSuccess { c ->
                    _uiState.update { it.copy(selectedCustomerName = c.companyName) }
                }
                val contactOptions = loadContactsAndReturn(cId)
                _uiState.update { it.copy(contactOptions = contactOptions) }
            }
        }
        d.selectedTeamId?.let { bid ->
            viewModelScope.launch {
                branchRepo.observeBranches().find { it.branchId == bid }?.let { b ->
                    _uiState.update { it.copy(selectedTeamName = b.branchName) }
                }
            }
        }
        d.selectedBillingBranchId?.let { bid ->
            viewModelScope.launch {
                val billingName = _uiState.value.billingBranchOptions.find { it.first == bid }?.second
                    ?: branchRepo.observeBranches().find { it.branchId == bid }?.branchName
                if (billingName != null) _uiState.update { it.copy(selectedBillingBranchName = billingName) }
            }
        }
    }

    private fun dismissDraftPrompt() {
        _uiState.update { it.copy(draftAvailable = false) }
    }

    val lossReasonOptions = com.example.pp68_salestrackingapp.utils.LossReasons.OPTIONS

    val opportunityOptions = listOf("HOT", "WARM", "COLD") // ✅ ตัวเลือกโอกาสการขาย

    // W6-2: ตัวเลือกปัจจัยข้อ 4-7 (เดียวกับที่ SalesResultViewModel ใช้ ที่มาจาก DealFactors master data)
    val dealPositionOptions   get() = com.example.pp68_salestrackingapp.utils.DealFactors.labelToCode(com.example.pp68_salestrackingapp.utils.DealFactors.DEAL_POSITION).keys.toList()
    val previousSolutionOptions get() = com.example.pp68_salestrackingapp.utils.DealFactors.labelToCode(com.example.pp68_salestrackingapp.utils.DealFactors.PREVIOUS_SOLUTION).keys.toList()
    val counterpartyTypeOptions get() = com.example.pp68_salestrackingapp.utils.DealFactors.labelToCode(com.example.pp68_salestrackingapp.utils.DealFactors.COUNTERPARTY_TYPE).keys.toList()
    val responseSpeedOptions  get() = com.example.pp68_salestrackingapp.utils.DealFactors.labelToCode(com.example.pp68_salestrackingapp.utils.DealFactors.RESPONSE_SPEED).keys.toList()

    init {
        loadCustomers()
        checkUserBranchAndLoadTeams()
        loadAllBillingBranches()
    }

    private fun loadAllBillingBranches() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingBillingBranches = true) }
            try {
                val result = branchRepo.getBranches()
                result.onSuccess { branches ->
                    _uiState.update {
                        it.copy(
                            billingBranchOptions     = branches.map { b -> b.branchId to b.branchName },
                            isLoadingBillingBranches = false
                        )
                    }
                }.onFailure {
                    branchRepo.syncFromRemote()
                    val cached = branchRepo.observeBranches()
                    _uiState.update {
                        it.copy(
                            billingBranchOptions     = cached.map { b -> b.branchId to b.branchName },
                            isLoadingBillingBranches = false
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingBillingBranches = false) }
            }
        }
    }

    private fun checkUserBranchAndLoadTeams() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingTeams = true) }
            val currentUser  = authRepo.currentUser()
            val userBranchId = currentUser?.teamId ?: ""

            if (currentUser?.empType == "Project") {
                _uiState.update {
                    it.copy(
                        selectedTeamId   = userBranchId,
                        selectedTeamName = "ทีมโปรเจค",
                        teamOptions      = listOf(userBranchId to "ทีมโปรเจค"),
                        isLoadingTeams   = false
                    )
                }
            } else {
                try {
                    branchRepo.syncFromRemote()
                    val allBranches = branchRepo.observeBranches()
                    val userBranch  = allBranches.find { it.branchId == userBranchId }

                    val filteredBranches = if (userBranch != null) {
                        allBranches.filter { it.region == userBranch.region }
                    } else {
                        allBranches
                    }

                    _uiState.update {
                        it.copy(
                            teamOptions    = filteredBranches.map { b -> b.branchId to b.branchName },
                            isLoadingTeams = false
                        )
                    }

                    if (filteredBranches.size == 1) {
                        val b = filteredBranches.first()
                        _uiState.update { current ->
                            current.copy(
                                selectedTeamId   = b.branchId,
                                selectedTeamName = b.branchName
                            )
                        }
                    } else if (userBranch != null && _uiState.value.projectId == null) {
                        onEvent(AddProjectEvent.TeamSelected(userBranch.branchId, userBranch.branchName))
                    } else if (userBranchId.isNotBlank() && _uiState.value.selectedTeamId.isNullOrBlank()) {
                        // ออฟไลน์และแคชสาขาว่าง — ยังรู้สาขาของผู้ใช้จาก token จึงเลือกให้ไว้ก่อน
                        // ดีกว่าปล่อยช่องว่างแล้วให้ผู้ใช้เดาว่าต้องทำอะไรต่อ
                        _uiState.update { it.copy(selectedTeamId = userBranchId) }
                    }
                } catch (e: Exception) {
                    _uiState.update { it.copy(isLoadingTeams = false) }
                }
            }

            // ✅ ทีมที่ระบบเลือกให้เองตอนเปิดฟอร์มโครงการใหม่ (สาขาของผู้ใช้ / สาขาเดียวที่มี) ไม่ใช่
            // สิ่งที่ผู้ใช้กรอก — ต้องดูดเข้า baseline ด้วย เพราะฟอร์มสร้างใหม่ไม่เคยเรียก
            // captureBaseline() เลย baseline จึงเป็นฟอร์มเปล่า การเติมทีมให้เองจะนับเป็น "ผู้ใช้แก้ไข"
            // ทันที เปิดหน้ามาเฉย ๆ แล้วกดย้อนกลับก็โดนถาม "มีข้อมูลที่ยังไม่ได้บันทึก"
            // (บั๊กเดียวกับ GPS auto-fill ในหน้าสร้างนัดหมายที่แก้ไปแล้วใน fce6dae คนละไฟล์)
            if (_uiState.value.projectId == null) {
                val autoTeamId = _uiState.value.selectedTeamId
                draft.absorbIntoBaseline { it.copy(selectedTeamId = autoTeamId) }
            }
        }
    }

    private suspend fun loadContactsAndReturn(custId: String): List<Pair<String, String>> {
        val result = customerRepo.getContactPersons(custId)
        return result.getOrNull()?.map { it.contactId.trim() to (it.fullName ?: it.nickname ?: "") } ?: emptyList()
    }

    private fun loadProject(id: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            projectRepo.getProjectById(id).fold(
                onSuccess = { project ->
                    // ✅ W4: อ่านตรงจากสองฟิลด์ที่แยกแล้ว (lossReason=รหัส, lossReasonNote=ข้อความอิสระ)
                    // เดางวนต่อเฉพาะแถวเก่าที่ backend ยังไม่ได้ migrate (lossReasonNote ว่าง แต่
                    // lossReason ไม่ตรงรหัสที่รู้จัก) — ครอบคลุมช่วงเปลี่ยนผ่านเท่านั้น
                    val (reason, other) = when {
                        !project.lossReasonNote.isNullOrBlank() ->
                            (project.lossReason ?: com.example.pp68_salestrackingapp.utils.LossReasons.OTHER) to project.lossReasonNote
                        project.lossReason.isNullOrBlank() -> "" to ""
                        project.lossReason in lossReasonOptions -> project.lossReason to ""
                        else -> com.example.pp68_salestrackingapp.utils.LossReasons.OTHER to project.lossReason
                    }

                    val dealFactors = com.example.pp68_salestrackingapp.utils.DealFactors
                    _uiState.update {
                        it.copy(
                            projectId              = project.projectId,
                            displayProjectId       = project.projectId,
                            projectName            = project.projectName,
                            projectStatus          = project.projectStatus,
                            opportunityScore      = project.opportunityScore, // ✅ Load opportunity score
                            expectedValue          = project.expectedValue?.toPlainAmount() ?: "",
                            startDate              = project.startDate,
                            closeDate              = project.closingDate,
                            siteLat                = project.projectLat,
                            siteLong               = project.projectLong,
                            selectedCustomerId     = project.custId,
                            selectedTeamId         = project.branchId,
                            selectedBillingBranchId = project.billingBranchId,
                            lossReason             = reason,
                            otherLossReason        = other,
                            dealPosition           = dealFactors.codeToLabel(dealFactors.DEAL_POSITION)[project.dealPosition] ?: "",
                            previousSolution       = dealFactors.codeToLabel(dealFactors.PREVIOUS_SOLUTION)[project.previousSolution] ?: "",
                            counterpartyType       = dealFactors.codeToLabel(dealFactors.COUNTERPARTY_TYPE)[project.counterpartyType] ?: "",
                            responseSpeed          = dealFactors.codeToLabel(dealFactors.RESPONSE_SPEED)[project.responseSpeed] ?: "",
                            isLoading              = false
                        )
                    }

                    project.custId?.let { cId ->
                        customerRepo.getCustomerById(cId).onSuccess { c ->
                            _uiState.update { it.copy(selectedCustomerName = c.companyName) }
                        }
                    }

                    val contactOptions = project.custId?.let { loadContactsAndReturn(it) } ?: emptyList()
                    _uiState.update { it.copy(contactOptions = contactOptions, isLoadingContacts = false) }

                    projectRepo.getProjectContacts(id).onSuccess { contacts ->
                        val selectedIds = contacts.mapNotNull { it.contactId.trim() }.toSet()
                        _uiState.update { it.copy(selectedContactIds = selectedIds) }
                        // ต้องตั้ง baseline หลังฟิลด์ที่นับใน draft (รวม selectedContactIds) โหลดครบแล้วเท่านั้น
                        // ไม่งั้นพอ contact โหลดเสร็จทีหลังจะดูเหมือนผู้ใช้แก้ไขทั้งที่ไม่ได้แตะอะไรเลย
                        draft.captureBaseline()
                        checkForDraft()
                    }

                    project.branchId?.let { bid ->
                        branchRepo.observeBranches().find { it.branchId == bid }?.let { b ->
                            _uiState.update { it.copy(selectedTeamName = b.branchName) }
                        }
                    }

                    project.billingBranchId?.let { bid ->
                        val billingName = _uiState.value.billingBranchOptions
                            .find { it.first == bid }?.second
                        if (billingName != null) {
                            _uiState.update { it.copy(selectedBillingBranchName = billingName) }
                        } else {
                            branchRepo.observeBranches().find { it.branchId == bid }?.let { b ->
                                _uiState.update { it.copy(selectedBillingBranchName = b.branchName) }
                            }
                        }
                    }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isLoading = false, saveError = e.message) }
                }
            )
        }
    }

    private fun loadCustomers() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingCustomers = true) }
            customerRepo.getLocalCustomers().fold(
                onSuccess = { list ->
                    leadCustomerOptions = list.map { c -> c.custId.trim() to c.companyName }.withUniqueLabels()
                    _uiState.update {
                        it.copy(
                            customerOptions    = leadCustomerOptions,
                            isLoadingCustomers = false
                        )
                    }
                },
                onFailure = { _uiState.update { it.copy(isLoadingCustomers = false) } }
            )
        }
    }

    private fun searchCustomers(query: String) {
        customerSearchJob?.cancel()
        val normalized = query.trim()
        if (normalized.length < 2) {
            _uiState.update {
                it.copy(
                    customerOptions = leadCustomerOptions,
                    customerSearchMessage = if (normalized.isEmpty()) null else "พิมพ์อย่างน้อย 2 ตัวอักษรเพื่อค้นหาลูกค้าเก่า(dynamic)"
                )
            }
            return
        }
        customerSearchJob = viewModelScope.launch {
            delay(400)
            _uiState.update { it.copy(customerSearchMessage = "กำลังค้นหาลูกค้าเก่า(dynamic)…") }
            customerRepo.searchErpCustomers(normalized).fold(
                onSuccess = { remote ->
                    val remoteOptions = remote.map { it.customerCode to it.customerName }
                    _uiState.update {
                        it.copy(
                            customerOptions = (leadCustomerOptions + remoteOptions)
                                .distinctBy { option -> option.first }
                                .withUniqueLabels(),
                            customerSearchMessage = if (remote.isEmpty()) "ไม่พบลูกค้าเก่า(dynamic) จากคำค้นนี้" else null
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            customerOptions = leadCustomerOptions,
                            customerSearchMessage = err.message ?: "ค้นหาลูกค้าเก่า(dynamic) ไม่ได้ (Lead ในเครื่องยังเลือกได้)"
                        )
                    }
                }
            )
        }
    }

    private fun loadContacts(custId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingContacts = true) }
            val options = loadContactsAndReturn(custId)
            _uiState.update { it.copy(contactOptions = options, isLoadingContacts = false) }
        }
    }

    fun onEvent(event: AddProjectEvent) {
        when (event) {
            is AddProjectEvent.LoadProject        -> loadProject(event.id)
            is AddProjectEvent.ProjectNameChanged ->
                _uiState.update { it.copy(projectName = event.value, projectNameError = null) }
            is AddProjectEvent.BranchChanged      ->
                _uiState.update { it.copy(branch = event.value) }
            is AddProjectEvent.CustomerSelected   -> {
                _uiState.update {
                    it.copy(
                        selectedCustomerId   = event.id.trim(),
                        selectedCustomerName = event.name,
                        customerError        = null,
                        selectedContactIds   = emptySet(),
                        contactOptions       = emptyList()
                    )
                }
                loadContacts(event.id.trim())
            }
            is AddProjectEvent.CustomerQueryChanged -> searchCustomers(event.value)
            is AddProjectEvent.ContactToggled -> {
                val current  = _uiState.value.selectedContactIds.toMutableSet()
                val targetId = event.id.trim()
                if (targetId in current) current.remove(targetId) else current.add(targetId)
                _uiState.update { it.copy(selectedContactIds = current) }
            }
            is AddProjectEvent.ExpectedValueChanged ->
                _uiState.update { it.copy(expectedValue = event.value) }
            is AddProjectEvent.StartDateChanged ->
                _uiState.update { it.copy(startDate = event.value.ifBlank { null }) }
            is AddProjectEvent.CloseDateChanged ->
                _uiState.update { it.copy(closeDate = event.value.ifBlank { null }) }
            is AddProjectEvent.StatusChanged ->
                _uiState.update { it.copy(projectStatus = event.value, statusError = null) }
            is AddProjectEvent.OpportunityScoreChanged -> // ✅ อัปเดตฟิลด์ใหม่
                _uiState.update { it.copy(opportunityScore = event.value) }
            is AddProjectEvent.TeamSelected -> {
                _uiState.update {
                    it.copy(
                        selectedTeamId   = event.id.trim(),
                        selectedTeamName = event.name
                    )
                }
            }
            is AddProjectEvent.BillingBranchSelected -> {
                _uiState.update {
                    it.copy(
                        selectedBillingBranchId   = event.id.trim(),
                        selectedBillingBranchName = event.name,
                        billingBranchError        = null
                    )
                }
            }
            is AddProjectEvent.LocationPicked ->
                _uiState.update {
                    it.copy(
                        siteLat      = event.lat,
                        siteLong     = event.lng,
                        locationText = "${"%.6f".format(event.lat)}, ${"%.6f".format(event.lng)}"
                    )
                }
            is AddProjectEvent.LossReasonChanged ->
                _uiState.update { it.copy(lossReason = event.value, lossReasonError = null) }
            is AddProjectEvent.OtherLossReasonChanged ->
                _uiState.update { it.copy(otherLossReason = event.value, lossReasonError = null) }
            is AddProjectEvent.DealPositionChanged ->
                _uiState.update { it.copy(dealPosition = event.value) }
            is AddProjectEvent.PreviousSolutionChanged ->
                _uiState.update { it.copy(previousSolution = event.value) }
            is AddProjectEvent.CounterpartyTypeChanged ->
                _uiState.update { it.copy(counterpartyType = event.value) }
            is AddProjectEvent.ResponseSpeedChanged ->
                _uiState.update { it.copy(responseSpeed = event.value) }
            is AddProjectEvent.ToggleQuickAddCustomer -> {
                _uiState.update { it.copy(isQuickAddCustomerOpen = event.isOpen, quickAddCompanyName = "", quickAddCustType = "") }
            }
            is AddProjectEvent.QuickAddCustomerChanged -> {
                _uiState.update { it.copy(quickAddCompanyName = event.name, quickAddCustType = event.type) }
            }
            is AddProjectEvent.ToggleQuickAddContact -> {
                _uiState.update {
                    it.copy(isQuickAddContactOpen = event.isOpen, quickAddContactName = "",
                            quickAddContactPhone = "", quickAddContactError = null)
                }
            }
            is AddProjectEvent.QuickAddContactChanged -> {
                _uiState.update {
                    it.copy(quickAddContactName = event.name, quickAddContactPhone = event.phone,
                            quickAddContactError = null)
                }
            }
            is AddProjectEvent.SaveQuickAddContact -> saveQuickContact()
            is AddProjectEvent.SaveQuickAddCustomer -> {
                saveQuickCustomer()
            }
            is AddProjectEvent.Save -> save()
            is AddProjectEvent.CheckDraft -> checkForDraft()
            is AddProjectEvent.RestoreDraft -> restoreDraft()
            is AddProjectEvent.DismissDraftPrompt -> dismissDraftPrompt()
        }
    }

    /**
     * ผู้ติดต่อผูกกับบริษัท จึงสร้างได้เฉพาะตอนเลือกบริษัทแล้ว และติดให้เลยหลังสร้างเสร็จ
     * เพราะคนที่กดสร้างตอนนี้ตั้งใจจะใส่เขาเป็นผู้ติดต่อของโครงการนี้อยู่แล้ว
     */
    private fun saveQuickContact() {
        val st = _uiState.value
        val custId = st.selectedCustomerId
        if (custId.isNullOrBlank()) {
            _uiState.update { it.copy(quickAddContactError = "เลือกบริษัทก่อนจึงจะเพิ่มผู้ติดต่อได้") }
            return
        }
        if (st.quickAddContactName.isBlank() || st.quickAddContactPhone.isBlank()) {
            _uiState.update { it.copy(quickAddContactError = "กรุณาระบุชื่อและเบอร์โทรศัพท์ให้ครบ") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingQuickContact = true, quickAddContactError = null) }
            val newContact = com.example.pp68_salestrackingapp.data.model.ContactPerson(
                // TEMP- จำเป็น — outbox ใช้คำนำหน้านี้ตัดสินว่าจะ POST หรือ PATCH
                contactId = "TEMP-" + UUID.randomUUID().toString().take(8).uppercase(),
                custId = custId,
                customerName = st.selectedCustomerName,
                fullName = st.quickAddContactName.trim(),
                phoneNumber = st.quickAddContactPhone.trim(),
                // เจ้าของผู้ติดต่อ = คนที่สร้าง (เหตุผลเดียวกับใน CreateAppointmentViewModel)
                createdBy = authRepo.currentUser()?.userId
            )
            contactRepo.addContact(newContact).fold(
                onSuccess = { contactId ->
                    _uiState.update {
                        it.copy(
                            contactOptions = it.contactOptions + (contactId to (newContact.fullName ?: "")),
                            selectedContactIds = it.selectedContactIds + contactId,
                            isQuickAddContactOpen = false,
                            isSavingQuickContact = false,
                            quickAddContactName = "",
                            quickAddContactPhone = ""
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(isSavingQuickContact = false,
                                quickAddContactError = e.message ?: "สร้างผู้ติดต่อไม่สำเร็จ")
                    }
                }
            )
        }
    }

    private fun saveQuickCustomer() {
        val st = _uiState.value
        if (st.quickAddCompanyName.isBlank() || st.quickAddCustType.isBlank()) { _uiState.update { it.copy(saveError = "กรุณาระบุชื่อบริษัทและประเภทลูกค้าให้ครบถ้วน") }; return }
        
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingQuickCust = true) }
            val newCust = Customer(
                // ต้องมีคำนำหน้า TEMP- — outbox ใช้มันตัดสินว่าจะ POST (สร้างใหม่)
                // หรือ PATCH (แก้ของเดิม) ถ้าเป็น UUID เปล่า การสร้างตอนไม่มีเน็ตจะกลายเป็น PATCH
                // ไปหาแถวที่ไม่มีจริง → 404 → ถูกบันทึกเป็นการปฏิเสธถาวร ลูกค้าหายไปเงียบ ๆ
                custId = "TEMP-" + UUID.randomUUID().toString().take(8).uppercase(),
                companyName = st.quickAddCompanyName.trim(),
                custType = st.quickAddCustType,
                createdBy = authRepo.currentUser()?.userId,
                // ไม่ตั้ง bizPostingGroup โดยตั้งใจ — มันคือเทียร์ลูกค้าที่ MS Dynamics 365 เป็นเจ้าของ
                // เดิมใส่รหัสสาขาของเซลส์ลงไป ทำให้ป้าย BizGroupBadge แสดงรหัสสาขาแทนเทียร์
                // และชิปกรอง R/W/I/P กรองลูกค้ากลุ่มนี้ไม่เจอ — ปล่อย null ให้ ERP เติม
                // (body ทั้ง create และ update มี filterValues ตัด null ทิ้ง จึงไม่ไปล้างค่าเดิมบน server)
                isLead = true
            )
            val result = customerRepo.addCustomer(newCust) // Using customerRepo per the constructor
            result.fold(
                onSuccess = { realCustId ->
                    // Update options and select it using the real generated ID
                    val newOption = Pair(realCustId, newCust.companyName)
                    _uiState.update { 
                        it.copy(
                            customerOptions = (it.customerOptions + newOption).withUniqueLabels(),
                            selectedCustomerId = realCustId,
                            selectedCustomerName = newCust.companyName,
                            isQuickAddCustomerOpen = false,
                            isSavingQuickCust = false,
                            quickAddCompanyName = "",
                            quickAddCustType = ""
                        )
                    }
                    // โหลดผู้ติดต่อของบริษัทใหม่ (ว่างอยู่แล้ว แต่ต้องล้างของบริษัทเดิมทิ้ง) — ต้องใช้
                    // realCustId ไม่ใช่ newCust.custId ที่เป็น TEMP- เพราะ state เลือก realCustId ไปแล้ว
                    // ถ้าใช้ id คนละตัวก็ไปยิงหาผู้ติดต่อของรหัสที่ไม่มีอยู่บน server เปล่า ๆ
                    loadContacts(realCustId)
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isSavingQuickCust = false, saveError = e.message) }
                }
            )
        }
    }

    private fun validate(): Boolean {
        var valid = true
        val s = _uiState.value
        if (s.projectName.isBlank()) {
            _uiState.update { it.copy(projectNameError = "กรุณากรอกชื่อโครงการ") }
            valid = false
        }
        if (s.projectStatus.isNullOrBlank()) {
            _uiState.update { it.copy(statusError = "กรุณาเลือกสถานะ") }
            valid = false
        } else if (s.projectStatus == "Lost" || s.projectStatus == "Failed") {
            if (s.lossReason.isBlank()) {
                _uiState.update { it.copy(lossReasonError = "กรุณาเลือกหรือระบุเหตุผลที่ไม่ได้งาน") }
                valid = false
            } else if (s.lossReason == com.example.pp68_salestrackingapp.utils.LossReasons.OTHER && s.otherLossReason.isBlank()) {
                _uiState.update { it.copy(lossReasonError = "กรุณาระบุเหตุผลอื่น ๆ") }
                valid = false
            }
        }
        // ❌ ไม่บังคับเลือกสาขาที่พนักงานขายรับผิดชอบ — รายชื่อสาขามาจาก server เท่านั้น
        // ตอนออฟไลน์และแคชสาขายังว่าง ดรอปดาวน์จะไม่มีอะไรให้เลือก ผู้ใช้จึงบันทึกโครงการ
        // ไม่ได้เลยทั้งที่ข้อมูลอื่นครบ ตอนบันทึกมี fallback เป็นสาขาของผู้ใช้เองอยู่แล้ว
        // ❌ เอาการเช็ค billingBranch ออก เพื่อให้ไม่บังคับกรอกตามที่ผู้ใช้แจ้ง
        return valid
    }

    private fun save() {
        if (!validate()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, saveError = null) }
            val s = _uiState.value
            // จับ key ตอนเริ่มเซฟ — ตอนสร้างใหม่ projectId ยังว่างตอนนี้ (key="new") แต่จะถูกเซ็ตเป็น
            // id จริงก่อน draftStore.clear() ด้านล่างจะรัน ถ้าอ่าน key จาก state สดตอนนั้นจะเคลียร์ผิด key
            val keyToClearOnSuccess = draft.currentKey()
            try {
                val user      = authRepo.currentUser()
                val userId    = user?.userId ?: "USR-0000"
                val branchId  = s.selectedTeamId ?: user?.teamId ?: "XX-0001"

                // ✅ W4: ส่งรหัสกับข้อความอิสระแยกกัน แทนการยุบเป็นก้อนเดียว
                val isLostOrFailed = s.projectStatus == "Lost" || s.projectStatus == "Failed"
                val finalLossReason = if (isLostOrFailed) s.lossReason else null
                val finalLossReasonNote = if (isLostOrFailed && s.lossReason == com.example.pp68_salestrackingapp.utils.LossReasons.OTHER) {
                    s.otherLossReason
                } else null

                // W6-2: แปลงป้าย -> รหัสก่อนส่ง เว้นว่างไว้เมื่อยังไม่เลือก (แก้ไขได้ที่นี่เท่านั้น)
                val dealFactors = com.example.pp68_salestrackingapp.utils.DealFactors

                // restoreDraft() ตั้ง selectedCustomerId ให้ทันที แต่ selectedCustomerName รอ resolve
                // แบบ async ทีหลัง — ถ้ากด Save ไวก่อนมันเสร็จ ชื่อจะยังว่าง ต้องกันด้วยการหาชื่อสดตรงนี้
                val resolvedCustomerName = s.selectedCustomerName
                    ?: s.selectedCustomerId?.let { customerRepo.getCustomerById(it).getOrNull()?.companyName }

                // ✅ projectId จะถูกสร้างใน repository โดยใช้รูปแบบ project number
                val projectToSave = Project(
                    projectId             = s.projectId ?: "",
                    custId                = if (s.selectedCustomerId.isNullOrBlank()) null else s.selectedCustomerId,
                    customerName          = resolvedCustomerName,
                    branchId              = branchId,
                    billingBranchId       = s.selectedBillingBranchId,
                    projectName           = s.projectName,
                    expectedValue         = s.expectedValue.replace(",", "").toDoubleOrNull(),
                    projectStatus         = s.projectStatus,
                    opportunityScore      = s.opportunityScore, // ✅ บันทึกค่าโอกาสการขาย
                    startDate             = s.startDate,
                    closingDate           = s.closeDate,
                    desiredCompletionDate = null,
                    projectLat            = s.siteLat,
                    projectLong           = s.siteLong,
                    lossReason            = finalLossReason,
                    lossReasonNote        = finalLossReasonNote,
                    dealPosition          = dealFactors.labelToCode(dealFactors.DEAL_POSITION)[s.dealPosition],
                    previousSolution      = dealFactors.labelToCode(dealFactors.PREVIOUS_SOLUTION)[s.previousSolution],
                    counterpartyType      = dealFactors.labelToCode(dealFactors.COUNTERPARTY_TYPE)[s.counterpartyType],
                    responseSpeed         = dealFactors.labelToCode(dealFactors.RESPONSE_SPEED)[s.responseSpeed],
                    createBy              = userId
                )

                var finalProjectId = s.projectId ?: ""

                val result = if (s.projectId != null) {
                    projectRepo.updateProject(projectToSave)
                } else {
                    projectRepo.createProject(projectToSave, userId).fold(
                        onSuccess = { createdProject ->
                            finalProjectId = createdProject.projectId
                            _uiState.update { it.copy(projectId = createdProject.projectId) }
                            kotlin.Result.success(Unit)
                        },
                        onFailure = { kotlin.Result.failure(it) }
                    )
                }

                result.onSuccess {
                    if (finalProjectId.isNotBlank()) {
                        val contactResult = projectRepo.saveProjectContacts(finalProjectId, s.selectedContactIds.map { it.trim() })

                        if (contactResult.isFailure) {
                            _uiState.update { it.copy(isLoading = false, saveError = "บันทึกผู้ติดต่อล้มเหลว: ${contactResult.exceptionOrNull()?.message}") }
                            return@onSuccess
                        }
                    }
                    keyToClearOnSuccess?.let { draft.discard(it) }
                    _uiState.update { it.copy(isLoading = false, isSaved = true) }
                }.onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, saveError = e.message) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, saveError = e.message) }
            }
        }
    }
}
