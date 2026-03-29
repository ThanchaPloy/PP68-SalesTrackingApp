package com.example.pp68_salestrackingapp.ui.screen.project

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pp68_salestrackingapp.data.model.Project
import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import com.example.pp68_salestrackingapp.data.repository.BranchRepository
import com.example.pp68_salestrackingapp.data.repository.CustomerRepository
import com.example.pp68_salestrackingapp.data.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

data class AddProjectUiState(
    val projectId:              String? = null,
    val generatedProjectNumber: String  = "",
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
    val selectedContactId:      String? = null,
    val selectedContactName:    String? = null,
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
    val projectNumber:          String  = ""   // ✅ เพิ่ม default value
)

sealed class AddProjectEvent {
    data class LoadProject(val id: String)                        : AddProjectEvent()
    data class ProjectNameChanged(val value: String)              : AddProjectEvent()
    data class BranchChanged(val value: String)                   : AddProjectEvent()
    data class CustomerSelected(val id: String, val name: String) : AddProjectEvent()
    data class ContactSelected(val id: String, val name: String)  : AddProjectEvent()
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
        loadTeams()
        generateAndSetProjectNumber()
    }

    // ── Gen Project Number ────────────────────────────────────
    private fun generateAndSetProjectNumber() {
        viewModelScope.launch {
            val userId   = authRepo.currentUser()?.userId ?: return@launch
            val branchId = authRepo.currentUser()?.teamId ?: return@launch
            val number   = generateProjectNumber(branchId, userId)
            _uiState.update { it.copy(generatedProjectNumber = number) }
        }
    }

    private suspend fun generateProjectNumber(branchId: String, userId: String): String {
        return withContext(Dispatchers.IO) {
            try {
                // TS-0001 → TS
                val branchCode = branchId.substringBefore("-").uppercase()

                // USR-0002 → S002
                val saleCode = "S" + userId.filter { it.isDigit() }.takeLast(3)

                // 2026 → 26
                val year = java.time.LocalDate.now().year.toString().takeLast(2)

                val prefix = "$branchCode-$year-$saleCode-"

                // นับจาก local DB
                val count = projectRepo.countProjectsByPrefix(prefix)
                val seq   = "%03d".format(count + 1)

                "$branchCode-$year-$saleCode-$seq"
            } catch (e: Exception) {
                "PRJ-" + UUID.randomUUID().toString().take(6).uppercase()
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
                            isLoading             = false
                        )
                    }
                    customerRepo.getCustomerById(project.custId).onSuccess { c ->
                        _uiState.update { it.copy(selectedCustomerName = c.companyName) }
                    }
                    loadContacts(project.custId)
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
            // ✅ ใช้ getLocalCustomers เท่านั้น ไม่ fallback ไป API
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

    private fun loadTeams() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingTeams = true) }
            try {
                // ✅ sync จาก API ก่อน
                branchRepo.syncFromRemote()

                // ✅ ดึงทั้งหมดจาก local DB
                val branches = branchRepo.observeBranches()

                android.util.Log.d("BranchDebug", "AddProject branches: ${branches.size}")

                _uiState.update {
                    it.copy(
                        teamOptions    = branches.map { b -> b.branchId to b.branchName },
                        isLoadingTeams = false
                    )
                }
            } catch (e: Exception) {
                // fallback
                projectRepo.getBranches().fold(
                    onSuccess = { list ->
                        _uiState.update { it.copy(teamOptions = list, isLoadingTeams = false) }
                    },
                    onFailure = {
                        _uiState.update { it.copy(isLoadingTeams = false) }
                    }
                )
            }
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
                        selectedContactId    = null,
                        selectedContactName  = null,
                        contactOptions       = emptyList()
                    )
                }
                loadContacts(event.id)
            }
            is AddProjectEvent.ContactSelected    ->
                _uiState.update {
                    it.copy(selectedContactId = event.id, selectedContactName = event.name)
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

                // ✅ re-gen project number เมื่อเปลี่ยน branch
                viewModelScope.launch {
                    val userId = authRepo.currentUser()?.userId ?: return@launch
                    val number = generateProjectNumber(event.id, userId)
                    _uiState.update { it.copy(generatedProjectNumber = number) }
                }
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
        return valid
    }

    private fun save() {
        if (!validate()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, saveError = null) }
            val s = _uiState.value

            try {
                val userId   = authRepo.currentUser()?.userId ?: "USR-0000"
                val branchId = s.selectedTeamId
                    ?: authRepo.currentUser()?.teamId
                    ?: "XX-0001"

                // ✅ ใช้ที่ gen ไว้แล้ว
                val projectNumber = s.generatedProjectNumber.ifBlank {
                    generateProjectNumber(branchId, userId)
                }

                val projectId = s.projectId
                    ?: ("PJ-" + UUID.randomUUID().toString().take(8).uppercase())

                val projectToSave = Project(
                    projectId             = projectId,
                    custId                = s.selectedCustomerId ?: "",
                    branchId              = branchId,
                    projectNumber         = projectNumber,        // ✅ ชื่อต้องตรงกับ model
                    projectName           = s.projectName,
                    expectedValue         = s.expectedValue.replace(",", "").toDoubleOrNull(),
                    projectStatus         = s.projectStatus,
                    startDate             = s.startDate,
                    closingDate           = s.closeDate,          // ✅ ต้องเป็น closingDate ไม่ใช่ closeDate
                    desiredCompletionDate = null,
                    projectLat            = s.siteLat,
                    projectLong           = s.siteLong,
                    opportunityScore      = null
                )

                val result = if (s.projectId != null) {
                    projectRepo.updateProject(projectToSave)
                } else {
                    projectRepo.createProject(projectToSave)
                }

                result.onSuccess {
                    // ✅ เพิ่ม user ที่สร้างเป็น owner
                    val memberIds = if (s.selectedMemberIds.isNotEmpty()) {
                        s.selectedMemberIds.toList()
                    } else {
                        listOf(userId)
                    }
                    projectRepo.addProjectMembers(projectId, memberIds, role = "owner")
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