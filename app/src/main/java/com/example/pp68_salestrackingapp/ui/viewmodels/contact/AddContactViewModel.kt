package com.example.pp68_salestrackingapp.ui.viewmodels.contact

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pp68_salestrackingapp.data.model.ContactPerson
import com.example.pp68_salestrackingapp.data.model.Project
import com.example.pp68_salestrackingapp.data.repository.AuthRepository
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
    object IsDecisionMakerToggled : AddContactEvent()
    object Save            : AddContactEvent()
}

@HiltViewModel
class AddContactViewModel @Inject constructor(
    private val contactRepo:  ContactRepository,
    private val customerRepo: CustomerRepository,
    private val projectRepo:  ProjectRepository,
    private val authRepo:     AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddContactUiState())
    val uiState: StateFlow<AddContactUiState> = _uiState

    init { loadCompanies() }

    private fun loadContact(id: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val contacts = contactRepo.getAllContactsFlow().first()
                val contact = contacts.find { it.contactId == id }
                if (contact != null) {
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
                _uiState.update { it.copy(companyOptions = customers.map { it.custId to it.companyName }, isLoadingCompanies = false) }
            }.onFailure { _uiState.update { it.copy(isLoadingCompanies = false) } }
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
            is AddContactEvent.ProjectSelected -> _uiState.update { it.copy(selectedProjectId = event.id, selectedProjectName = event.name) }
            is AddContactEvent.FullNameChanged -> _uiState.update { it.copy(fullName = event.value, fullNameError = null) }
            is AddContactEvent.NicknameChanged -> _uiState.update { it.copy(nickname = event.value) }
            is AddContactEvent.PositionChanged -> _uiState.update { it.copy(position = event.value) }
            is AddContactEvent.PhoneChanged -> _uiState.update { it.copy(phoneNum = event.value) }
            is AddContactEvent.EmailChanged -> _uiState.update { it.copy(email = event.value, emailError = null) }
            is AddContactEvent.LineIdChanged -> _uiState.update { it.copy(lineId = event.value) }
            is AddContactEvent.IsActiveToggled -> _uiState.update { it.copy(isActive = !it.isActive) }
            is AddContactEvent.IsDecisionMakerToggled -> _uiState.update { it.copy(isDecisionMaker = !it.isDecisionMaker) }
            is AddContactEvent.Save -> save()
        }
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
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, saveError = null) }
            val s = _uiState.value
            val currentUserId = authRepo.currentUser()?.userId
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
                isDmConfirmed = s.isDecisionMaker,
                createdBy = currentUserId // ✅ บันทึกผู้สร้าง
            )
            contactRepo.addContact(contactToSave).fold(
                onSuccess = { _uiState.update { it.copy(isLoading = false, isSaved = true) } },
                onFailure = { e -> _uiState.update { it.copy(isLoading = false, saveError = e.message) } }
            )
        }
    }
}
