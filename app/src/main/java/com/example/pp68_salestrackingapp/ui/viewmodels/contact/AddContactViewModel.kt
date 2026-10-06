package com.example.pp68_salestrackingapp.ui.viewmodels.contact

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pp68_salestrackingapp.data.model.ContactPerson
import com.example.pp68_salestrackingapp.data.model.Project
import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import com.example.pp68_salestrackingapp.data.repository.ContactRepository
import com.example.pp68_salestrackingapp.data.repository.CustomerRepository
import com.example.pp68_salestrackingapp.data.repository.ProjectRepository
import com.example.pp68_salestrackingapp.utils.DraftStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import com.example.pp68_salestrackingapp.utils.withUniqueLabels

data class AddContactUiState(
    val contactId: String? = null,
    val fullName:  String = "",
    val nickname:  String = "",
    val position:  String = "",
    val phoneNum:  String = "",
    val email:     String = "",
    val lineId:    String = "",
    val isActive:  Boolean = true,
    val isDecisionMaker: Boolean = false,
    val companyOptions:      List<Pair<String, String>> = emptyList(),
    val selectedCompanyId:   String? = null,
    val selectedCompanyName: String? = null,
    val isLoadingCompanies:  Boolean = false,
    val companySearchMessage: String? = null,
    val projectOptions:      List<Pair<String, String>> = emptyList(),
    val selectedProjectId:   String? = null,
    val selectedProjectName: String? = null,
    val isLoadingProjects:   Boolean = false,
    val companyError:  String? = null,
    val fullNameError: String? = null,
    val phoneError: String? = null,
    val emailError:    String? = null,
    val isLoading: Boolean = false,
    val isSaved:   Boolean = false,
    val saveError: String? = null,
    val draftAvailable: Boolean = false,

    // สร้างบริษัทด่วน — ผู้ติดต่อต้องผูกบริษัทเสมอ ถ้าบริษัทยังไม่มีในระบบจะกรอกต่อไม่ได้เลย
    val isQuickAddCompanyOpen: Boolean = false,
    val quickAddCompanyName: String = "",
    val quickAddCustType: String = "",
    val isSavingQuickCompany: Boolean = false,
    val quickAddCompanyError: String? = null,

    // สร้างโครงการด่วน — ช่องโครงการเป็นตัวเลือกไม่บังคับ แต่ถ้าจะผูกแล้วโครงการยังไม่มี
    // ก็ต้องออกไปสร้างที่หน้าอื่นแล้วกลับมากรอกใหม่ทั้งฟอร์ม
    val isQuickAddProjectOpen: Boolean = false,
    val quickAddProjectName: String = "",
    val quickAddProjectStatus: String = "",
    val isSavingQuickProject: Boolean = false,
    val quickAddProjectError: String? = null
)

data class AddContactDraft(
    val fullName: String = "",
    val nickname: String = "",
    val position: String = "",
    val phoneNum: String = "",
    val email: String = "",
    val lineId: String = "",
    val isActive: Boolean = true,
    val isDecisionMaker: Boolean = false,
    val selectedCompanyId: String? = null,
    val selectedCompanyName: String? = null,
    val selectedProjectId: String? = null,
    val selectedProjectName: String? = null
)

sealed class AddContactEvent {
    data class LoadContact(val id: String) : AddContactEvent()
    object CheckDraft : AddContactEvent()
    object RestoreDraft : AddContactEvent()
    object DismissDraftPrompt : AddContactEvent()
    data class CompanySelected(val id: String, val name: String) : AddContactEvent()
    data class CompanyQueryChanged(val value: String) : AddContactEvent()
    data class ProjectSelected(val id: String, val name: String) : AddContactEvent()
    data class FullNameChanged(val value: String)  : AddContactEvent()
    data class NicknameChanged(val value: String)  : AddContactEvent()
    data class PositionChanged(val value: String)  : AddContactEvent()
    data class PhoneChanged(val value: String)     : AddContactEvent()
    data class EmailChanged(val value: String)     : AddContactEvent()
    data class LineIdChanged(val value: String)    : AddContactEvent()
    object IsActiveToggled : AddContactEvent()
    object IsDecisionMakerToggled : AddContactEvent()
    object Save            : AddContactEvent()

    data class ToggleQuickAddCompany(val isOpen: Boolean) : AddContactEvent()
    data class QuickAddCompanyChanged(val name: String, val type: String) : AddContactEvent()
    object SaveQuickAddCompany : AddContactEvent()

    data class ToggleQuickAddProject(val isOpen: Boolean) : AddContactEvent()
    data class QuickAddProjectNameChanged(val value: String) : AddContactEvent()
    data class QuickAddProjectStatusChanged(val value: String) : AddContactEvent()
    object SaveQuickAddProject : AddContactEvent()
}

@HiltViewModel
class AddContactViewModel @Inject constructor(
    private val contactRepo:  ContactRepository,
    private val customerRepo: CustomerRepository,
    private val projectRepo:  ProjectRepository,
    private val authRepo:     AuthRepository,
    private val draftStore:   DraftStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddContactUiState())
    val uiState: StateFlow<AddContactUiState> = _uiState
    private var leadCompanyOptions: List<Pair<String, String>> = emptyList()
    private var companySearchJob: Job? = null

    private val draft = com.example.pp68_salestrackingapp.utils.DraftController(
        store = draftStore,
        type = AddContactDraft::class.java,
        initialBaseline = AddContactDraft(),
        keyOf = { "add_contact:${_uiState.value.contactId ?: "new"}" },
        currentOf = { _uiState.value.toDraft() }
    )

    private fun AddContactUiState.toDraft() = AddContactDraft(
        fullName, nickname, position, phoneNum, email, lineId, isActive, isDecisionMaker,
        selectedCompanyId, selectedCompanyName, selectedProjectId, selectedProjectName
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
                fullName = d.fullName,
                nickname = d.nickname,
                position = d.position,
                phoneNum = d.phoneNum,
                email = d.email,
                lineId = d.lineId,
                isActive = d.isActive,
                isDecisionMaker = d.isDecisionMaker,
                selectedCompanyId = d.selectedCompanyId,
                selectedCompanyName = d.selectedCompanyName,
                selectedProjectId = d.selectedProjectId,
                selectedProjectName = d.selectedProjectName,
                draftAvailable = false
            )
        }
        d.selectedCompanyId?.let { loadProjectsForCompany(it) }
    }

    private fun dismissDraftPrompt() {
        _uiState.update { it.copy(draftAvailable = false) }
    }

    init { loadCompanies() }

    // ✅ เจ้าของผู้ติดต่อ (คนสร้าง) ของแถวที่โหลดมาแก้ — ต้องคงไว้ตอนบันทึก
    // กติกา "ผู้ติดต่อเห็นได้เฉพาะคนสร้าง" ใช้ฟิลด์นี้ตัดสิน ถ้าเขียนเป็นตัวเองทับ เท่ากับแย่ง
    // ความเป็นเจ้าของไปจากคนสร้างจริง (บั๊กทรงเดียวกับที่เจอในหน้าแก้ไขลูกค้า)
    private var loadedCreatedBy: String? = null

    private fun loadContact(id: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val contact = contactRepo.getContactById(id)  // ✅ ดึงตรงจาก DB
                if (contact != null) {
                    loadedCreatedBy = contact.createdBy
                    val cName = customerRepo.getCustomerById(contact.custId).getOrNull()?.companyName ?: ""
                    _uiState.update { it.copy(
                        contactId = contact.contactId,
                        fullName = contact.fullName ?: "",
                        nickname = contact.nickname ?: "",
                        position = contact.position ?: "",
                        phoneNum = contact.phoneNumber ?: "",
                        email = contact.email ?: "",
                        lineId = contact.line ?: "",
                        isActive = contact.isActive ?: true,
                        isDecisionMaker = contact.isDmConfirmed ?: false,
                        selectedCompanyId = contact.custId,
                        selectedCompanyName = cName,
                        isLoading = false
                    ) }
                    loadProjectsForCompany(contact.custId)
                    draft.captureBaseline()
                    checkForDraft()
                } else {
                    _uiState.update { it.copy(isLoading = false, saveError = "ไม่พบข้อมูลผู้ติดต่อ") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, saveError = e.message) }
            }
        }
    }

    private fun loadCompanies() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingCompanies = true) }
            customerRepo.getCustomers().onSuccess { customers ->
                // ชื่อซ้ำ = เลือกผิดบริษัทเงียบ ๆ (หน้าจอส่งกลับมาแค่ "ชื่อ") ดู withUniqueLabels
                leadCompanyOptions = customers.map { it.custId to it.companyName }.withUniqueLabels()
                _uiState.update { it.copy(companyOptions = leadCompanyOptions, isLoadingCompanies = false) }
            }.onFailure { _uiState.update { it.copy(isLoadingCompanies = false) } }
        }
    }

    private fun searchCompanies(query: String) {
        companySearchJob?.cancel()
        val normalized = query.trim()
        if (normalized.length < 2) {
            _uiState.update {
                it.copy(
                    companyOptions = leadCompanyOptions,
                    companySearchMessage = if (normalized.isEmpty()) null else "พิมพ์อย่างน้อย 2 ตัวอักษรเพื่อค้นหาลูกค้า ERP"
                )
            }
            return
        }
        companySearchJob = viewModelScope.launch {
            delay(400)
            _uiState.update { it.copy(companySearchMessage = "กำลังค้นหาลูกค้า ERP…") }
            customerRepo.searchErpCustomers(normalized).fold(
                onSuccess = { remote ->
                    val remoteOptions = remote.map { it.customerCode to it.customerName }
                    _uiState.update {
                        it.copy(
                            companyOptions = (leadCompanyOptions + remoteOptions)
                                .distinctBy { option -> option.first }
                                .withUniqueLabels(),
                            companySearchMessage = if (remote.isEmpty()) "ไม่พบลูกค้า ERP จากคำค้นนี้" else null
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            companyOptions = leadCompanyOptions,
                            companySearchMessage = err.message ?: "ค้นหา ERP ไม่ได้ (Lead ในเครื่องยังเลือกได้)"
                        )
                    }
                }
            )
        }
    }

    private fun loadProjectsForCompany(custId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingProjects = true, projectOptions = emptyList()) }
            try {
                val projects = projectRepo.getAllProjectsFlow().first().filter { it.custId == custId }
                _uiState.update { it.copy(projectOptions = projects.map { it.projectId to it.projectName }, isLoadingProjects = false) }
            } catch (e: Exception) { _uiState.update { it.copy(isLoadingProjects = false) } }
        }
    }


    fun onEvent(event: AddContactEvent) {
        when (event) {
            is AddContactEvent.LoadContact -> loadContact(event.id)
            is AddContactEvent.CompanySelected -> {
                _uiState.update { it.copy(selectedCompanyId = event.id, selectedCompanyName = event.name, companyError = null, selectedProjectId = null, selectedProjectName = null, projectOptions = emptyList()) }
                loadProjectsForCompany(event.id)
            }
            is AddContactEvent.CompanyQueryChanged -> searchCompanies(event.value)
            is AddContactEvent.ProjectSelected -> _uiState.update { it.copy(selectedProjectId = event.id, selectedProjectName = event.name) }
            is AddContactEvent.FullNameChanged -> _uiState.update { it.copy(fullName = event.value, fullNameError = null) }
            is AddContactEvent.NicknameChanged -> _uiState.update { it.copy(nickname = event.value) }
            is AddContactEvent.PositionChanged -> _uiState.update { it.copy(position = event.value) }
            is AddContactEvent.PhoneChanged -> _uiState.update { it.copy(phoneNum = event.value, phoneError = null) }
            is AddContactEvent.EmailChanged -> _uiState.update { it.copy(email = event.value, emailError = null) }
            is AddContactEvent.LineIdChanged -> _uiState.update { it.copy(lineId = event.value) }
            is AddContactEvent.IsActiveToggled -> _uiState.update { it.copy(isActive = !it.isActive) }
            is AddContactEvent.IsDecisionMakerToggled -> _uiState.update { it.copy(isDecisionMaker = !it.isDecisionMaker) }
            is AddContactEvent.Save -> save()

            is AddContactEvent.ToggleQuickAddCompany ->
                _uiState.update {
                    it.copy(isQuickAddCompanyOpen = event.isOpen, quickAddCompanyName = "",
                            quickAddCustType = "", quickAddCompanyError = null)
                }
            is AddContactEvent.QuickAddCompanyChanged ->
                _uiState.update {
                    it.copy(quickAddCompanyName = event.name, quickAddCustType = event.type, quickAddCompanyError = null)
                }
            is AddContactEvent.SaveQuickAddCompany -> saveQuickCompany()

            is AddContactEvent.ToggleQuickAddProject ->
                _uiState.update {
                    it.copy(isQuickAddProjectOpen = event.isOpen, quickAddProjectName = "",
                            quickAddProjectStatus = "", quickAddProjectError = null)
                }
            is AddContactEvent.QuickAddProjectNameChanged ->
                _uiState.update { it.copy(quickAddProjectName = event.value, quickAddProjectError = null) }
            is AddContactEvent.QuickAddProjectStatusChanged ->
                _uiState.update { it.copy(quickAddProjectStatus = event.value, quickAddProjectError = null) }
            is AddContactEvent.SaveQuickAddProject -> saveQuickProject()
            is AddContactEvent.CheckDraft -> checkForDraft()
            is AddContactEvent.RestoreDraft -> restoreDraft()
            is AddContactEvent.DismissDraftPrompt -> dismissDraftPrompt()
        }
    }

    /**
     * สร้างบริษัทแบบ Lead ด่วน แล้วเลือกให้ทันที — ชุดเดียวกับที่หน้าสร้างนัดหมาย/โครงการใช้
     * (ไม่ตั้ง bizPostingGroup เพราะเป็นเทียร์ลูกค้าที่ MS Dynamics 365 เป็นเจ้าของ)
     */
    private fun saveQuickCompany() {
        val s = _uiState.value
        if (s.quickAddCompanyName.isBlank() || s.quickAddCustType.isBlank()) {
            _uiState.update { it.copy(quickAddCompanyError = "กรุณาระบุชื่อบริษัทและประเภทลูกค้าให้ครบ") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingQuickCompany = true, quickAddCompanyError = null) }
            val user = authRepo.currentUser()
            val newCust = com.example.pp68_salestrackingapp.data.model.Customer(
                // TEMP- จำเป็น — outbox ใช้คำนำหน้านี้ตัดสินว่าจะ POST หรือ PATCH
                custId = "TEMP-" + UUID.randomUUID().toString().take(8).uppercase(),
                companyName = s.quickAddCompanyName.trim(),
                custType = s.quickAddCustType,
                createdBy = user?.userId,
                isLead = true
            )
            customerRepo.addCustomer(newCust).fold(
                onSuccess = { realCustId ->
                    _uiState.update {
                        it.copy(
                            companyOptions = (it.companyOptions + (realCustId to newCust.companyName)).withUniqueLabels(),
                            selectedCompanyId = realCustId,
                            selectedCompanyName = newCust.companyName,
                            companyError = null,
                            // บริษัทใหม่ยังไม่มีโครงการ ล้างของบริษัทเดิมทิ้งไม่ให้ค้าง
                            selectedProjectId = null,
                            selectedProjectName = null,
                            projectOptions = emptyList(),
                            isQuickAddCompanyOpen = false,
                            isSavingQuickCompany = false,
                            quickAddCompanyName = "",
                            quickAddCustType = ""
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(isSavingQuickCompany = false,
                                quickAddCompanyError = e.message ?: "สร้างบริษัทไม่สำเร็จ")
                    }
                }
            )
        }
    }

    /** สร้างโครงการด่วนภายใต้บริษัทที่เลือกไว้ แล้วผูกกับผู้ติดต่อคนนี้ทันที */
    private fun saveQuickProject() {
        val s = _uiState.value
        val custId = s.selectedCompanyId
        if (custId.isNullOrBlank()) {
            _uiState.update { it.copy(quickAddProjectError = "เลือกบริษัทก่อนจึงจะสร้างโครงการได้") }
            return
        }
        if (s.quickAddProjectName.isBlank() || s.quickAddProjectStatus.isBlank()) {
            _uiState.update { it.copy(quickAddProjectError = "กรุณาระบุชื่อโครงการและสถานะให้ครบ") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingQuickProject = true, quickAddProjectError = null) }
            val user = authRepo.currentUser()
            val newProject = com.example.pp68_salestrackingapp.data.model.Project(
                projectId = "",   // repository เป็นคนสร้าง TEMP- id ให้เอง
                projectName = s.quickAddProjectName.trim(),
                projectStatus = s.quickAddProjectStatus,
                branchId = user?.teamId,
                custId = custId,
                createBy = user?.userId
            )
            projectRepo.createProject(newProject, user?.userId ?: "").fold(
                onSuccess = { created ->
                    _uiState.update {
                        it.copy(
                            projectOptions = it.projectOptions + (created.projectId to created.projectName),
                            selectedProjectId = created.projectId,
                            selectedProjectName = created.projectName,
                            isQuickAddProjectOpen = false,
                            isSavingQuickProject = false,
                            quickAddProjectName = "",
                            quickAddProjectStatus = ""
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(isSavingQuickProject = false,
                                quickAddProjectError = e.message ?: "สร้างโครงการไม่สำเร็จ")
                    }
                }
            )
        }
    }

    /**
     * ผูกผู้ติดต่อที่เพิ่งบันทึกเข้ากับโครงการที่ผู้ใช้เลือกไว้ (ถ้าเลือก)
     *
     * ไม่บล็อกผลการบันทึกตามผลของขั้นนี้ — ผู้ติดต่อถูกสร้างสำเร็จไปแล้ว ถ้าตีกลับว่าไม่สำเร็จ
     * ผู้ใช้จะกดบันทึกซ้ำแล้วได้ผู้ติดต่อซ้ำสองคน ส่วนกรณีออฟไลน์ saveProjectContacts เขียน Room
     * ไว้ก่อนแล้วปักธงให้ outbox ตามส่งเองอยู่แล้ว
     */
    private suspend fun linkToProjectIfChosen(contactId: String) {
        val projectId = _uiState.value.selectedProjectId ?: return
        val existing = projectRepo.getProjectContacts(projectId).getOrNull()?.map { it.contactId } ?: emptyList()
        if (contactId in existing) return
        projectRepo.saveProjectContacts(projectId, existing + contactId)
    }

    private fun save() {
        if (_uiState.value.selectedCompanyId.isNullOrBlank()) {
            _uiState.update { it.copy(companyError = "กรุณาเลือกบริษัท") }
            return
        }
        if (_uiState.value.fullName.isBlank()) {
            _uiState.update { it.copy(fullNameError = "กรุณากรอกชื่อ") }
            return
        }
        if (_uiState.value.phoneNum.isBlank()) {
            _uiState.update { it.copy(phoneError = "กรุณากรอกเบอร์โทรศัพท์") }
            return
        }
        val email = _uiState.value.email
        if (email.isNotBlank() && (!email.contains("@") || !email.substringAfter("@").contains("."))) {
            _uiState.update { it.copy(emailError = "รูปแบบ Email ไม่ถูกต้อง") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, saveError = null) }
            val s = _uiState.value
            val currentUserId = authRepo.currentUser()?.userId
            val contactToSave = ContactPerson(
                contactId   = s.contactId ?: ("TEMP-" + UUID.randomUUID().toString().take(8).uppercase()),
                custId      = s.selectedCompanyId!!,
                customerName = s.selectedCompanyName,
                fullName    = s.fullName,
                nickname    = s.nickname.ifBlank { null },
                position    = s.position.ifBlank { null },
                phoneNumber = s.phoneNum.ifBlank { null },
                email       = s.email.ifBlank { null },
                line        = s.lineId.ifBlank { null },
                isActive    = s.isActive,
                isDmConfirmed = s.isDecisionMaker,
                // สร้างใหม่ = ตัวเองเป็นผู้สร้าง, แก้ไข = คงเจ้าของเดิมไว้ (ดู loadedCreatedBy)
                createdBy   = loadedCreatedBy ?: currentUserId
            )

            // ✅ แยก edit vs create — คืน id ของแถวที่บันทึกทั้งสองทาง (แก้ไขก็คือ id เดิม) เพื่อเอาไป
            // ผูกกับโครงการต่อได้ ตอนสร้างใหม่ addContact คืน id จริงจาก server (หรือ TEMP- ถ้าออฟไลน์)
            val result: kotlin.Result<String> = if (s.contactId != null) {
                contactRepo.updateContact(s.contactId, contactToSave).map { s.contactId }  // PATCH
            } else {
                contactRepo.addContact(contactToSave)                   // POST
            }

            result.fold(
                onSuccess = { savedId ->
                    // ✅ ฟอร์มมีช่อง "เลือกโครงการ (ไม่บังคับ)" ที่เก็บค่าเข้า state และฉบับร่างแล้ว
                    // แต่ save() เดิมไม่เคยใช้เลย ผู้ใช้เลือกโครงการแล้วกดบันทึก ความผูกพันหายไปเงียบ ๆ
                    // (ContactPerson ไม่มีฟิลด์โครงการ ต้องผูกผ่านตาราง project_contact)
                    // saveProjectContacts เขียนทับรายชื่อทั้งชุด จึงต้องอ่านของเดิมมารวมก่อน ไม่ใช่ส่งไปแค่คนนี้
                    linkToProjectIfChosen(savedId)
                    discardDraft()
                    _uiState.update { it.copy(isLoading = false, isSaved = true) }
                },
                onFailure = { e -> _uiState.update { it.copy(isLoading = false, saveError = e.message) } }
            )
        }
    }

}
