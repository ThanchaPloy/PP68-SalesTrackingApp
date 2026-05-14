package com.example.pp68_salestrackingapp.ui.screen.project

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pp68_salestrackingapp.data.model.Project
import com.example.pp68_salestrackingapp.data.remote.ApiService
import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import com.example.pp68_salestrackingapp.data.repository.BranchRepository
import com.example.pp68_salestrackingapp.data.repository.CustomerRepository
import com.example.pp68_salestrackingapp.data.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddProjectUiState(
    val projectId:              String? = null,
    val displayProjectId:       String  = "รอกดบันทึกเพื่อสร้างหมายเลข",
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
    val saveError:              String? = null,
    val lossReason:             String  = "",
    val otherLossReason:        String  = "",
    val lossReasonError:        String? = null
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
    data class BillingBranchSelected(val id: String, val name: String) : AddProjectEvent()
    data class MemberToggled(val userId: String)                  : AddProjectEvent()
    data class LocationPicked(val lat: Double, val lng: Double)   : AddProjectEvent()
    data class LossReasonChanged(val value: String)               : AddProjectEvent()
    data class OtherLossReasonChanged(val value: String)          : AddProjectEvent()
    object Save                                                   : AddProjectEvent()
}

@HiltViewModel
class AddProjectViewModel @Inject constructor(
    private val projectRepo:  ProjectRepository,
    private val customerRepo: CustomerRepository,
    private val authRepo:     AuthRepository,
    private val branchRepo:   BranchRepository,
    private val apiService:   ApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddProjectUiState())
    val uiState: StateFlow<AddProjectUiState> = _uiState

    val lossReasonOptions = listOf(
        "ผลิตไม่ได้/ผลิตไม่ทัน",
        "เทคโนโลยีไม่ผ่าน",
        "สู้ราคาไม่ไหว",
        "อื่น ๆ"
    )

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

            if (userBranchId == "PJ-001") {
                val branch = branchRepo.getBranchById(userBranchId)
                _uiState.update {
                    it.copy(
                        selectedTeamId   = userBranchId,
                        selectedTeamName = branch?.branchName ?: "ทีมโปรเจค",
                        teamOptions      = listOf(userBranchId to (branch?.branchName ?: "ทีมโปรเจค")),
                        isLoadingTeams   = false
                    )
                }
                loadMembersForTeam(userBranchId)
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
                        onEvent(AddProjectEvent.TeamSelected(b.branchId, b.branchName))
                    } else if (userBranch != null && _uiState.value.projectId == null) {
                        onEvent(AddProjectEvent.TeamSelected(userBranch.branchId, userBranch.branchName))
                    }
                } catch (e: Exception) {
                    _uiState.update { it.copy(isLoadingTeams = false) }
                }
            }
        }
    }

    private suspend fun loadContactsAndReturn(custId: String): List<Pair<String, String>> {
        return try {
            val resp = apiService.getContactsByCustomerIds("eq.$custId")
            if (resp.isSuccessful && resp.body() != null) {
                resp.body()!!.map { c -> c.contactId.trim() to (c.fullName ?: "") }
            } else emptyList()
        } catch (e: Exception) { emptyList() }
    }

    private fun loadProject(id: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            projectRepo.getProjectById(id).fold(
                onSuccess = { project ->
                    val existingReason = project.lossReason ?: ""
                    val (reason, other) = if (existingReason in lossReasonOptions) {
                        existingReason to ""
                    } else if (existingReason.isNotBlank()) {
                        "อื่น ๆ" to existingReason
                    } else {
                        "" to ""
                    }

                    _uiState.update {
                        it.copy(
                            projectId              = project.projectId,
                            displayProjectId       = project.projectId,
                            projectName            = project.projectName,
                            projectStatus          = project.projectStatus,
                            expectedValue          = project.expectedValue?.toString() ?: "",
                            startDate              = project.startDate,
                            closeDate              = project.closingDate,
                            siteLat                = project.projectLat,
                            siteLong               = project.projectLong,
                            selectedCustomerId     = project.custId,
                            selectedTeamId         = project.branchId,
                            selectedBillingBranchId = project.billingBranchId,
                            lossReason             = reason,
                            otherLossReason        = other,
                            isLoading              = false
                        )
                    }

                    customerRepo.getCustomerById(project.custId).onSuccess { c ->
                        _uiState.update { it.copy(selectedCustomerName = c.companyName) }
                    }

                    val contactOptions = loadContactsAndReturn(project.custId)
                    _uiState.update { it.copy(contactOptions = contactOptions, isLoadingContacts = false) }

                    projectRepo.getProjectContacts(id).onSuccess { contacts ->
                        val selectedIds = contacts.mapNotNull { it.contactId.trim() }.toSet()
                        _uiState.update { it.copy(selectedContactIds = selectedIds) }
                    }

                    project.branchId?.let { bid ->
                        branchRepo.observeBranches().find { it.branchId == bid }?.let { b ->
                            _uiState.update { it.copy(selectedTeamName = b.branchName) }
                        }
                        loadMembersForTeam(bid)

                        val existingMembers = projectRepo.getProjectMembersDetailed(id)
                        _uiState.update {
                            it.copy(selectedMemberIds = existingMembers.map { m -> m.first.trim() }.toSet())
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
                    _uiState.update {
                        it.copy(
                            customerOptions    = list.map { c -> c.custId.trim() to c.companyName },
                            isLoadingCustomers = false
                        )
                    }
                },
                onFailure = { _uiState.update { it.copy(isLoadingCustomers = false) } }
            )
        }
    }

    private fun loadContacts(custId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingContacts = true) }
            try {
                val resp = apiService.getContactsByCustomerIds("eq.$custId")
                if (resp.isSuccessful && resp.body() != null) {
                    val options = resp.body()!!.map { c -> c.contactId.trim() to (c.fullName ?: "") }
                    _uiState.update { it.copy(contactOptions = options, isLoadingContacts = false) }
                } else {
                    _uiState.update { it.copy(contactOptions = emptyList(), isLoadingContacts = false) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingContacts = false) }
            }
        }
    }

    private fun loadMembersForTeam(teamId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMembers = true) }
            projectRepo.getMembersByBranch(teamId).fold(
                onSuccess = { list ->
                    _uiState.update {
                        it.copy(
                            teamMemberOptions = list.map { it.first.trim() to it.second },
                            isLoadingMembers  = false
                        )
                    }
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
                        selectedCustomerId   = event.id.trim(),
                        selectedCustomerName = event.name,
                        customerError        = null,
                        selectedContactIds   = emptySet(),
                        contactOptions       = emptyList()
                    )
                }
                loadContacts(event.id.trim())
            }
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
            is AddProjectEvent.TeamSelected -> {
                _uiState.update {
                    it.copy(
                        selectedTeamId    = event.id.trim(),
                        selectedTeamName  = event.name,
                        selectedMemberIds = emptySet(),
                        teamMemberOptions = emptyList()
                    )
                }
                loadMembersForTeam(event.id.trim())
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
            is AddProjectEvent.MemberToggled -> {
                val current      = _uiState.value.selectedMemberIds.toMutableSet()
                val targetUserId = event.userId.trim()
                if (targetUserId in current) current.remove(targetUserId) else current.add(targetUserId)
                _uiState.update { it.copy(selectedMemberIds = current) }
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
        } else if (s.projectStatus == "Lost" || s.projectStatus == "Failed") {
            if (s.lossReason.isBlank()) {
                _uiState.update { it.copy(lossReasonError = "กรุณาเลือกหรือระบุเหตุผลที่ไม่ได้งาน") }
                valid = false
            } else if (s.lossReason == "อื่น ๆ" && s.otherLossReason.isBlank()) {
                _uiState.update { it.copy(lossReasonError = "กรุณาระบุเหตุผลอื่น ๆ") }
                valid = false
            }
        }
        if (s.selectedTeamId.isNullOrBlank()) valid = false
        if (s.selectedBillingBranchId.isNullOrBlank()) {
            _uiState.update { it.copy(billingBranchError = "กรุณาเลือกสาขาที่เปิดบิล") }
            valid = false
        }
        return valid
    }

    private fun save() {
        if (!validate()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, saveError = null) }
            val s = _uiState.value
            try {
                val user      = authRepo.currentUser()
                val userId    = user?.userId ?: "USR-0000"
                val branchId  = s.selectedTeamId ?: user?.teamId ?: "XX-0001"

                val finalLossReason = if (s.projectStatus == "Lost" || s.projectStatus == "Failed") {
                    if (s.lossReason == "อื่น ๆ") s.otherLossReason else s.lossReason
                } else null

                // ✅ projectId จะถูกสร้างใน repository โดยใช้รูปแบบ project number
                val projectToSave = Project(
                    projectId             = s.projectId ?: "",
                    custId                = s.selectedCustomerId ?: "",
                    branchId              = branchId,
                    billingBranchId       = s.selectedBillingBranchId,
                    projectName           = s.projectName,
                    expectedValue         = s.expectedValue.replace(",", "").toDoubleOrNull(),
                    projectStatus         = s.projectStatus,
                    startDate             = s.startDate,
                    closingDate           = s.closeDate,
                    desiredCompletionDate = null,
                    projectLat            = s.siteLat,
                    projectLong           = s.siteLong,
                    opportunityScore      = null,
                    lossReason            = finalLossReason
                )

                val result = if (s.projectId != null) {
                    projectRepo.updateProject(projectToSave)
                } else {
                    projectRepo.createProject(projectToSave, userId).fold(
                        onSuccess = { createdProject ->
                            // อัปเดต ID เพื่อใช้ในการเพิ่มสมาชิก/ติดต่อ
                            _uiState.update { it.copy(projectId = createdProject.projectId) }
                            kotlin.Result.success(Unit)
                        },
                        onFailure = { kotlin.Result.failure(it) }
                    )
                }

                result.onSuccess {
                    val finalProjectId = _uiState.value.projectId ?: ""
                    val memberIds = s.selectedMemberIds.map { it.trim() }.ifEmpty { listOf(userId.trim()) }
                    
                    // ✅ ในตาราง project_sales_member ไม่มี project_number แล้ว
                    projectRepo.addProjectMembers(
                        projectId = finalProjectId,
                        userIds   = memberIds,
                        role      = "owner"
                    )
                    projectRepo.saveProjectContacts(finalProjectId, s.selectedContactIds.map { it.trim() })
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
