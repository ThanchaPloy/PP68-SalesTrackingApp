package com.example.pp68_salestrackingapp.ui.screen.contact

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pp68_salestrackingapp.data.model.ContactPerson
import com.example.pp68_salestrackingapp.data.model.Project
import com.example.pp68_salestrackingapp.data.repository.ContactRepository
import com.example.pp68_salestrackingapp.data.repository.CustomerRepository
import com.example.pp68_salestrackingapp.data.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

// ─── UI State ─────────────────────────────────────────────────
data class AddContactUiState(
    val contactId: String? = null,
    val fullName:  String = "",
    val nickname:  String = "",
    val position:  String = "",
    val phoneNum:  String = "",
    val email:     String = "",
    val lineId:    String = "",
    val isActive:  Boolean = true,

    val companyOptions:      List<Pair<String, String>> = emptyList(),
    val selectedCompanyId:   String? = null,
    val selectedCompanyName: String? = null,
    val isLoadingCompanies:  Boolean = false,

    val projectOptions:      List<Pair<String, String>> = emptyList(),
    val selectedProjectId:   String? = null,
    val selectedProjectName: String? = null,
    val isLoadingProjects:   Boolean = false,

    val companyError:  String? = null,
    val fullNameError: String? = null,
    val emailError:    String? = null,

    val isLoading: Boolean = false,
    val isSaved:   Boolean = false,
    val saveError: String? = null
)

// ─── Events ───────────────────────────────────────────────────
sealed class AddContactEvent {
    data class LoadContact(val id: String) : AddContactEvent()
    data class CompanySelected(val id: String, val name: String) : AddContactEvent()
    data class ProjectSelected(val id: String, val name: String) : AddContactEvent()
    data class FullNameChanged(val value: String)  : AddContactEvent()
    data class NicknameChanged(val value: String)  : AddContactEvent()
    data class PositionChanged(val value: String)  : AddContactEvent()
    data class PhoneChanged(val value: String)     : AddContactEvent()
    data class EmailChanged(val value: String)     : AddContactEvent()
    data class LineIdChanged(val value: String)    : AddContactEvent()
    object IsActiveToggled : AddContactEvent()
    object Save            : AddContactEvent()
}

// ─── ViewModel ────────────────────────────────────────────────
@HiltViewModel
class AddContactViewModel @Inject constructor(
    private val contactRepo:  ContactRepository,
    private val customerRepo: CustomerRepository,
    private val projectRepo:  ProjectRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddContactUiState())
    val uiState: StateFlow<AddContactUiState> = _uiState

    init { loadCompanies() }

    private fun loadContact(id: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // ดึงรายการทั้งหมดออกมาก่อน แล้วค้นหา ID ที่ต้องการ (เลี่ยงปัญหาไม่มีฟังก์ชัน getContactById)
                val contacts = contactRepo.getAllContactsFlow().first()
                val contact = contacts.find { it.contactId == id }

                if (contact != null) {
                    // ค้นหาชื่อบริษัทจาก custId
                    var cName = ""
                    customerRepo.getCustomerById(contact.custId).onSuccess { cust ->
                        cName = cust.companyName
                    }

                    _uiState.update {
                        it.copy(
                            contactId = contact.contactId,
                            fullName = contact.fullName ?: "",
                            nickname = contact.nickname ?: "",
                            position = contact.position ?: "",
                            phoneNum = contact.phoneNumber ?: "", // เปลี่ยนให้ตรงกับ Model
                            email = contact.email ?: "",
                            lineId = contact.line ?: "", // เปลี่ยนให้ตรงกับ Model
                            isActive = contact.isActive ?: true,
                            selectedCompanyId = contact.custId,
                            selectedCompanyName = cName, // ใช้ชื่อที่หามาได้
                            isLoading = false
                        )
                    }
                    loadProjectsForCompany(contact.custId)
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
            customerRepo.getCustomers().fold(
                onSuccess = { customers ->
                    _uiState.update {
                        it.copy(
                            companyOptions     = customers.map { c -> c.custId to c.companyName },
                            isLoadingCompanies = false
                        )
                    }
                },
                onFailure = { _uiState.update { it.copy(isLoadingCompanies = false) } }
            )
        }
    }

    private fun loadProjectsForCompany(custId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingProjects = true, projectOptions = emptyList()) }
            try {
                val projects: List<Project> = projectRepo.getAllProjectsFlow().first()
                    .filter { it.custId == custId }

                val options: List<Pair<String, String>> = projects.map { p ->
                    p.projectId to p.projectName
                }

                _uiState.update {
                    it.copy(
                        projectOptions    = options,
                        isLoadingProjects = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingProjects = false) }
            }
        }
    }

    fun onEvent(event: AddContactEvent) {
        when (event) {
            is AddContactEvent.LoadContact -> loadContact(event.id)
            is AddContactEvent.CompanySelected -> {
                _uiState.update {
                    it.copy(
                        selectedCompanyId   = event.id,
                        selectedCompanyName = event.name,
                        companyError        = null,
                        selectedProjectId   = null,
                        selectedProjectName = null,
                        projectOptions      = emptyList()
                    )
                }
                loadProjectsForCompany(event.id)
            }
            is AddContactEvent.ProjectSelected ->
                _uiState.update { it.copy(selectedProjectId = event.id, selectedProjectName = event.name) }
            is AddContactEvent.FullNameChanged ->
                _uiState.update { it.copy(fullName = event.value, fullNameError = null) }
            is AddContactEvent.NicknameChanged ->
                _uiState.update { it.copy(nickname = event.value) }
            is AddContactEvent.PositionChanged ->
                _uiState.update { it.copy(position = event.value) }
            is AddContactEvent.PhoneChanged ->
                _uiState.update { it.copy(phoneNum = event.value) }
            is AddContactEvent.EmailChanged ->
                _uiState.update { it.copy(email = event.value, emailError = null) }
            is AddContactEvent.LineIdChanged ->
                _uiState.update { it.copy(lineId = event.value) }
            is AddContactEvent.IsActiveToggled ->
                _uiState.update { it.copy(isActive = !it.isActive) }
            is AddContactEvent.Save -> save()
        }
    }

    private fun validate(): Boolean {
        var valid = true
        val s = _uiState.value

        if (s.selectedCompanyId.isNullOrBlank()) {
            _uiState.update { it.copy(companyError = "กรุณาเลือกบริษัท") }
            valid = false
        }
        if (s.fullName.isBlank()) {
            _uiState.update { it.copy(fullNameError = "กรุณากรอกชื่อ") }
            valid = false
        }
        if (s.email.isNotBlank() && !android.util.Patterns.EMAIL_ADDRESS.matcher(s.email).matches()) {
            _uiState.update { it.copy(emailError = "รูปแบบ Email ไม่ถูกต้อง") }
            valid = false
        }
        return valid
    }

    private fun save() {
        if (!validate()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, saveError = null) }
            val s = _uiState.value

            // สร้าง Object ตามหน้าตา Model จริงๆ ของคุณ
            val contactToSave = ContactPerson(
                contactId = s.contactId ?: ("CNT-" + UUID.randomUUID().toString().take(8).uppercase()),
                custId = s.selectedCompanyId!!,
                fullName = s.fullName,
                nickname = s.nickname.ifBlank { null },
                position = s.position.ifBlank { null },
                phoneNumber = s.phoneNum.ifBlank { null },
                email = s.email.ifBlank { null },
                line = s.lineId.ifBlank { null },
                isActive = s.isActive,
                isDmConfirmed = false // Default
            )

            // ตอนนี้ใช้ addContact ไปก่อนสำหรับทั้งสร้างใหม่และอัปเดต เพราะ ContactRepository ยังไม่มี updateContact
            val result = contactRepo.addContact(contactToSave)

            result.fold(
                onSuccess = { _uiState.update { it.copy(isLoading = false, isSaved = true) } },
                onFailure = { e ->
                    _uiState.update { it.copy(isLoading = false, saveError = e.message) }
                }
            )
        }
    }
}