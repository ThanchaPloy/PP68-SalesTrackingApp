package com.example.pp68_salestrackingapp.ui.screen.project

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pp68_salestrackingapp.data.model.Project
import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import com.example.pp68_salestrackingapp.data.repository.BranchRepository
import com.example.pp68_salestrackingapp.data.repository.CustomerRepository
import com.example.pp68_salestrackingapp.data.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class AddProjectUiState(
    val projectId:              String? = null,
    val generatedProjectNumber: String  = "รอกดบันทึกเพื่อสร้างหมายเลข",
    val projectName:            String  = "",
    val branch:                 String  = "",
    val expectedValue:          String  = "",
    val startDate:              String? = null,
    val closeDate:              String? = null,
    val projectStatus:          String? = null,
    val locationText:           String  = "",
    val siteLat:                Double? = null,
    val siteLong:               Double? = null,
    val customerOptions:        List<Pair<String, String>> = emptyList(),
    val selectedCustomerId:     String? = null,
    val selectedCustomerName:   String? = null,
    val isLoadingCustomers:     Boolean = false,
    val contactOptions:         List<Pair<String, String>> = emptyList(),
    val selectedContactIds:     Set<String> = emptySet(),
    val isLoadingContacts:      Boolean = false,
    val teamOptions:            List<Pair<String, String>> = emptyList(),
    val selectedTeamId:         String? = null,
    val selectedTeamName:       String? = null,
    val isLoadingTeams:         Boolean = false,
    val teamMemberOptions:      List<Pair<String, String>> = emptyList(),
    val selectedMemberIds:      Set<String> = emptySet(),
    val isLoadingMembers:       Boolean = false,
    val projectNameError:       String? = null,
    val customerError:          String? = null,
    val statusError:            String? = null,
    val isLoading:              Boolean = false,
    val isSaved:                Boolean = false,
    val saveError:              String? = null,
    val projectNumber:          String  = ""
)

sealed class AddProjectEvent {
    data class LoadProject(val id: String)                        : AddProjectEvent()
    data class ProjectNameChanged(val value: String)              : AddProjectEvent()
    data class BranchChanged(val value: String)                   : AddProjectEvent()
    data class CustomerSelected(val id: String, val name: String) : AddProjectEvent()
    data class ContactToggled(val id: String)                     : AddProjectEvent()
    data class ExpectedValueChanged(val value: String)            : AddProjectEvent()
    data class StartDateChanged(val value: String)                : AddProjectEvent()
    data class CloseDateChanged(val value: String)                : AddProjectEvent()
    data class StatusChanged(val value: String)                   : AddProjectEvent()
    data class TeamSelected(val id: String, val name: String)     : AddProjectEvent()
    data class MemberToggled(val userId: String)                  : AddProjectEvent()
    data class LocationPicked(val lat: Double, val lng: Double)   : AddProjectEvent()
    object Save                                                   : AddProjectEvent()
}

@HiltViewModel
class AddProjectViewModel @Inject constructor(
    private val projectRepo:  ProjectRepository,
    private val customerRepo: CustomerRepository,
    private val authRepo:     AuthRepository,
    private val branchRepo: BranchRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddProjectUiState())
    val uiState: StateFlow<AddProjectUiState> = _uiState

    init {
        loadCustomers()
        checkUserBranchAndLoadTeams()
    }

    private fun checkUserBranchAndLoadTeams() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingTeams = true) }
            val currentUser = authRepo.currentUser()
            val userBranchId = currentUser?.teamId ?: ""

            // 1. ถ้าเป็นทีมโปรเจค (PJ-001) ให้เลือกอัตโนมัติ
            if (userBranchId == "PJ-001") {
                val branch = branchRepo.getBranchById(userBranchId)
                _uiState.update {
                    it.copy(
                        selectedTeamId = userBranchId,
                        selectedTeamName = branch?.branchName ?: "ทีมโปรเจค",
                        teamOptions = listOf(userBranchId to (branch?.branchName ?: "ทีมโปรเจค")),
                        isLoadingTeams = false
                    )
                }
                loadMembersForTeam(userBranchId)
            } else {
                // 2. ถ้าไม่ใช่ทีมโปรเจค ให้กรองตาม Region
                try {
                    branchRepo.syncFromRemote()
                    val allBranches = branchRepo.observeBranches()
                    val userBranch = allBranches.find { it.branchId == userBranchId }
                    
                    val filteredBranches = if (userBranch != null) {
                        allBranches.filter { it.region == userBranch.region }
                    } else {
                        allBranches
                    }

                    _uiState.update {
                        it.copy(
                            teamOptions = filteredBranches.map { b -> b.branchId to b.branchName },
                            isLoadingTeams = false
                        )
                    }
                    
                    // ถ้าเหลือสาขาเดียวให้เลือกเลย (เช่น กรณีมีสาขาเดียวในภูมิภาค)
                    if (filteredBranches.size == 1) {
                        val b = filteredBranches.first()
                        onEvent(AddProjectEvent.TeamSelected(b.branchId, b.branchName))
                    } else if (userBranch != null) {
                        // หรือเลือกสาขาตัวเองเป็น default
                        onEvent(AddProjectEvent.TeamSelected(userBranch.branchId, userBranch.branchName))
                    }

                } catch (e: Exception) {
                    _uiState.update { it.copy(isLoadingTeams = false) }
                }
            }
        }
    }

    private fun loadProject(id: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            projectRepo.getProjectById(id).fold(
                onSuccess = { project ->
                    _uiState.update {
                        it.copy(
                            projectId             = project.projectId,
                            generatedProjectNumber = project.projectNumber ?: "",
                            projectName           = project.projectName,
                            projectStatus         = project.projectStatus,
                            expectedValue         = project.expectedValue?.toString() ?: "",
                            startDate             = project.startDate,
                            closeDate             = project.closingDate,
                            siteLat               = project.projectLat,
                            siteLong              = project.projectLong,
                            selectedCustomerId    = project.custId,
                            selectedTeamId        = project.branchId,
                            isLoading             = false
                        )
                    }
                    customerRepo.getCustomerById(project.custId).onSuccess { c ->
                        _uiState.update { it.copy(selectedCustomerName = c.companyName) }
                    }
                    loadContacts(project.custId)
                    
                    // Load associated contacts for project
                    projectRepo.getProjectContacts(id).onSuccess { contacts ->
                        _uiState.update { it.copy(selectedContactIds = contacts.map { it.contactId }.toSet()) }
                    }

                    project.branchId?.let { bid ->
                        branchRepo.observeBranches().find { it.branchId == bid }?.let { b ->
                            _uiState.update { it.copy(selectedTeamName = b.branchName) }
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
                    _uiState.update {
                        it.copy(
                            customerOptions    = list.map { c -> c.custId to c.companyName },
                            isLoadingCustomers = false
                        )
                    }
                },
                onFailure = {
                    _uiState.update { it.copy(isLoadingCustomers = false) }
                }
            )
        }
    }

    private fun loadContacts(custId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingContacts = true, contactOptions = emptyList()) }
            customerRepo.getContactPersons(custId).fold(
                onSuccess = { list ->
                    _uiState.update {
                        it.copy(
                            contactOptions    = list.map { c -> c.contactId to (c.fullName ?: "") },
                            isLoadingContacts = false
                        )
                    }
                },
                onFailure = { _uiState.update { it.copy(isLoadingContacts = false) } }
            )
        }
    }

    private fun loadMembersForTeam(teamId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMembers = true, teamMemberOptions = emptyList()) }
            projectRepo.getMembersByBranch(teamId).fold(
                onSuccess = { list ->
                    _uiState.update { it.copy(teamMemberOptions = list, isLoadingMembers = false) }
                },
                onFailure = { _uiState.update { it.copy(isLoadingMembers = false) } }
            )
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
                        selectedCustomerId   = event.id,
                        selectedCustomerName = event.name,
                        customerError        = null,
                        selectedContactIds   = emptySet(),
                        contactOptions       = emptyList()
                    )
                }
                loadContacts(event.id)
            }
            is AddProjectEvent.ContactToggled    -> {
                val current = _uiState.value.selectedContactIds.toMutableSet()
                if (event.id in current) current.remove(event.id)
                else current.add(event.id)
                _uiState.update { it.copy(selectedContactIds = current) }
            }
            is AddProjectEvent.ExpectedValueChanged ->
                _uiState.update { it.copy(expectedValue = event.value) }
            is AddProjectEvent.StartDateChanged   ->
                _uiState.update { it.copy(startDate = event.value.ifBlank { null }) }
            is AddProjectEvent.CloseDateChanged   ->
                _uiState.update { it.copy(closeDate = event.value.ifBlank { null }) }
            is AddProjectEvent.StatusChanged      ->
                _uiState.update { it.copy(projectStatus = event.value, statusError = null) }
            is AddProjectEvent.TeamSelected       -> {
                _uiState.update {
                    it.copy(
                        selectedTeamId    = event.id,
                        selectedTeamName  = event.name,
                        selectedMemberIds = emptySet(),
                        teamMemberOptions = emptyList()
                    )
                }
                loadMembersForTeam(event.id)
            }
            is AddProjectEvent.MemberToggled      -> {
                val current = _uiState.value.selectedMemberIds.toMutableSet()
                if (event.userId in current) current.remove(event.userId)
                else current.add(event.userId)
                _uiState.update { it.copy(selectedMemberIds = current) }
            }
            is AddProjectEvent.LocationPicked     ->
                _uiState.update {
                    it.copy(
                        siteLat      = event.lat,
                        siteLong     = event.lng,
                        locationText = "${"%.6f".format(event.lat)}, ${"%.6f".format(event.lng)}"
                    )
                }
            is AddProjectEvent.Save -> save()
        }
    }

    private fun validate(): Boolean {
        var valid = true
        val s = _uiState.value
        if (s.projectName.isBlank()) {
            _uiState.update { it.copy(projectNameError = "กรุณากรอกชื่อโครงการ") }
            valid = false
        }
        if (s.selectedCustomerId.isNullOrBlank()) {
            _uiState.update { it.copy(customerError = "กรุณาเลือกลูกค้า") }
            valid = false
        }
        if (s.projectStatus.isNullOrBlank()) {
            _uiState.update { it.copy(statusError = "กรุณาเลือกสถานะ") }
            valid = false
        }
        if (s.selectedTeamId.isNullOrBlank()) {
            valid = false // ต้องเลือกสาขา
        }
        return valid
    }

    private fun save() {
        if (!validate()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, saveError = null) }
            val s = _uiState.value
            try {
                val userId   = authRepo.currentUser()?.userId ?: "USR-0000"
                val branchId = s.selectedTeamId ?: authRepo.currentUser()?.teamId ?: "XX-0001"
                val projectId = s.projectId ?: ("PJ-" + UUID.randomUUID().toString().take(8).uppercase())

                val projectToSave = Project(
                    projectId             = projectId,
                    custId                = s.selectedCustomerId ?: "",
                    branchId              = branchId,
                    projectNumber         = if (s.projectId != null) s.generatedProjectNumber else null,
                    projectName           = s.projectName,
                    expectedValue         = s.expectedValue.replace(",", "").toDoubleOrNull(),
                    projectStatus         = s.projectStatus,
                    startDate             = s.startDate,
                    closingDate           = s.closeDate,
                    desiredCompletionDate = null,
                    projectLat            = s.siteLat,
                    projectLong           = s.siteLong,
                    opportunityScore      = null
                )

                val result = if (s.projectId != null) projectRepo.updateProject(projectToSave)
                else {
                    projectRepo.createProject(projectToSave, userId).fold(
                        onSuccess = { kotlin.Result.success(Unit) },
                        onFailure = { kotlin.Result.failure(it) }
                    )
                }

                result.onSuccess {
                    val memberIds = if (s.selectedMemberIds.isNotEmpty()) s.selectedMemberIds.toList() else listOf(userId)
                    projectRepo.addProjectMembers(projectId, memberIds, role = "owner")
                    
                    // Save contact persons for project
                    projectRepo.saveProjectContacts(projectId, s.selectedContactIds.toList())

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
