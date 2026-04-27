package com.example.pp68_salestrackingapp.ui.viewmodels.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pp68_salestrackingapp.data.repository.ActivityRepository
import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import com.example.pp68_salestrackingapp.data.repository.CustomerRepository
import com.example.pp68_salestrackingapp.data.repository.ProjectRepository
import com.example.pp68_salestrackingapp.data.model.ActivityMaster
import com.example.pp68_salestrackingapp.data.model.SalesActivity
import com.example.pp68_salestrackingapp.data.model.ActivityPlanItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

data class CreateAppointmentUiState(
    val activityId:          String? = null,
    val selectedProjectId:   String? = null,
    val selectedProjectName: String? = null,
    val selectedCustomerId:  String? = null,
    val titleTopic:          String  = "",
    val activityType:        String  = "onsite",
    val plannedDate:         String? = null,
    val startTime:           String? = null,
    val endTime:             String? = null,
    val lat:                 Double? = null,
    val lng:                 Double? = null,

    val selectedContactIds:  Set<String>          = emptySet(),
    val selectedMasterIds:   Set<Int>             = emptySet(),
    val isOtherSelected:     Boolean              = false,
    val otherObjectiveText:  String               = "",

    val projectOptions:  List<ProjectOption>   = emptyList(),
    val contactOptions:  List<ContactOption>   = emptyList(),
    val allContactOptions: List<ContactOption> = emptyList(), // สำหรับค้นหาทั้งหมด
    val masterOptions:   List<ActivityMaster>  = emptyList(),
    val allMasterOptions:  List<ActivityMaster> = emptyList(),

    val contactSearchQuery: String = "",

    val isLoading:         Boolean = false,
    val isLoadingProjects: Boolean = false,
    val isLoadingContacts: Boolean = false,
    val isLoadingMasters:  Boolean = false,
    val isSaved:           Boolean = false,

    val projectError: String? = null,
    val masterError:  String? = null,
    val saveError:    String? = null,

    val showStartTimePicker: Boolean = false,
    val showEndTimePicker:   Boolean = false
)

data class ProjectOption(val id: String, val name: String, val status: String)
data class ContactOption(val id: String, val name: String, val companyName: String? = null)

sealed class CreateAppointmentEvent {
    data class LoadActivity(val activityId: String)         : CreateAppointmentEvent()
    data class LoadInitialProject(val projectId: String)    : CreateAppointmentEvent()
    data class ProjectSelected(val id: String?, val name: String?, val status: String?) : CreateAppointmentEvent()
    data class TitleChanged(val value: String)              : CreateAppointmentEvent()
    data class TypeChanged(val value: String)               : CreateAppointmentEvent()
    data class ContactToggled(val id: String)               : CreateAppointmentEvent()
    data class ContactSearchQueryChanged(val value: String) : CreateAppointmentEvent()
    data class MasterToggled(val id: Int)                   : CreateAppointmentEvent()
    object OtherToggled                                     : CreateAppointmentEvent()
    data class OtherObjectiveTextChanged(val value: String) : CreateAppointmentEvent()
    data class DateChanged(val value: String)               : CreateAppointmentEvent()
    data class StartTimeSelected(val value: String)         : CreateAppointmentEvent()
    data class EndTimeSelected(val value: String)           : CreateAppointmentEvent()
    data class LocationPicked(val lat: Double, val lng: Double) : CreateAppointmentEvent()
    object ShowStartTimePicker  : CreateAppointmentEvent()
    object ShowEndTimePicker    : CreateAppointmentEvent()
    object DismissTimePicker    : CreateAppointmentEvent()
    object Save                 : CreateAppointmentEvent()
}

@HiltViewModel
class CreateAppointmentViewModel @Inject constructor(
    private val activityRepo: ActivityRepository,
    private val projectRepo:  ProjectRepository,
    private val customerRepo: CustomerRepository,
    private val authRepo:     AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateAppointmentUiState())
    val uiState: StateFlow<CreateAppointmentUiState> = _uiState

    init {
        loadProjects()
        loadMasterObjectives()
        loadAllContacts()
    }

    private fun loadAllContacts() {
        viewModelScope.launch {
            customerRepo.getAllContacts().collect { list ->
                val options = list.map { ContactOption(it.contactId, it.fullName ?: it.nickname ?: it.contactId) }
                _uiState.update { it.copy(allContactOptions = options) }
            }
        }
    }

    private fun loadMasterObjectives() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMasters = true) }
            try {
                val masters = activityRepo.getMasterActivities()
                val all = masters.ifEmpty { getDefaultMasters() }
                _uiState.update {
                    it.copy(
                        allMasterOptions = all,
                        masterOptions    = all, // Default show all if no project
                        isLoadingMasters = false
                    )
                }
            } catch (e: Exception) {
                val all = getDefaultMasters()
                _uiState.update {
                    it.copy(
                        allMasterOptions = all,
                        masterOptions    = all,
                        isLoadingMasters = false
                    )
                }
            }
        }
    }

    private fun getDefaultMasters(): List<ActivityMaster> = listOf(
        ActivityMaster(1,  "Lead",             "ระบุและบันทึกข้อมูลลูกค้า"),
        ActivityMaster(2,  "Lead",             "สำรวจความต้องการเบื้องต้น"),
        ActivityMaster(3,  "Lead",             "นำเสนอพอร์ตฟอลิโอสินค้าและ Reference"),
        ActivityMaster(4,  "Lead",             "ประเมินศักยภาพดีล"),
        ActivityMaster(5,  "New Project",      "รับแบบแปลน / Shop Drawing"),
        ActivityMaster(6,  "New Project",      "เคลียร์สเปคกับลูกค้า"),
        ActivityMaster(7,  "New Project",      "ส่งตัวอย่างกระจก (Sample)"),
        ActivityMaster(8,  "New Project",      "ยืนยันปริมาณและ Scope งาน"),
        ActivityMaster(9,  "New Project",      "ระบุผู้มีอำนาจตัดสินใจ"),
        ActivityMaster(10, "Quotation",        "จัดทำและส่ง Quotation"),
        ActivityMaster(11, "Quotation",        "ติดตาม Quotation"),
        ActivityMaster(12, "Quotation",        "ปรับ Quotation ตามข้อเจรจา"),
        ActivityMaster(13, "Quotation",        "ยืนยัน Decision Maker รับ Quotation"),
        ActivityMaster(14, "Bidding",          "เตรียมเอกสารประมูลครบถ้วน"),
        ActivityMaster(15, "Bidding",          "ยื่นราคาและนำเสนอ"),
        ActivityMaster(16, "Bidding",          "ติดตามผลและตอบข้อซักถาม"),
        ActivityMaster(17, "Bidding",          "ปรับราคาตาม Feedback"),
        ActivityMaster(18, "Make a Decision",  "นำเสนอจุดแข็งเทียบคู่แข่ง"),
        ActivityMaster(19, "Make a Decision",  "เจรจาต่อรองราคาและเงื่อนไข"),
        ActivityMaster(20, "Make a Decision",  "ส่ง Reference และ Testimonial"),
        ActivityMaster(21, "Make a Decision",  "ได้รับสัญญาณยืนยันจากลูกค้า"),
        ActivityMaster(22, "Assured",          "ยืนยันรายละเอียดสั่งซื้อ"),
        ActivityMaster(23, "Assured",          "ประสานงานฝ่ายผลิต / จัดซื้อ"),
        ActivityMaster(24, "Assured",          "ส่งร่าง PO หรือสัญญา"),
        ActivityMaster(25, "Assured",          "ยืนยัน Logistics"),
        ActivityMaster(26, "PO",               "รับ PO อย่างเป็นทางการ"),
        ActivityMaster(27, "PO",               "ยืนยันกำหนดส่งมอบ"),
        ActivityMaster(28, "PO",               "บันทึกดีลเข้าระบบ")
    )

    private fun loadProjects() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingProjects = true) }
            projectRepo.getAllProjectsFlow().collect { list ->
                _uiState.update {
                    it.copy(
                        projectOptions    = list.map { p ->
                            ProjectOption(p.projectId, p.projectName, p.projectStatus ?: "")
                        },
                        isLoadingProjects = false
                    )
                }
            }
        }
    }

    private fun loadContactsForProject(projectId: String, selectedContactIds: Set<String> = emptySet()) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingContacts = true, contactOptions = emptyList()) }
            try {
                val project = projectRepo.getProjectById(projectId).getOrNull()
                val custId  = project?.custId ?: return@launch
                val status  = project.projectStatus ?: ""

                _uiState.update { it.copy(selectedCustomerId = custId) }

                val category = getCategoryForProjectStatus(status)
                val filtered = if (category != null) {
                    _uiState.value.allMasterOptions.filter { it.category == category }
                } else {
                    _uiState.value.allMasterOptions
                }

                _uiState.update { it.copy(masterOptions = filtered) }

                customerRepo.getContactPersons(custId).onSuccess { contacts ->
                    _uiState.update {
                        it.copy(
                            contactOptions    = contacts
                                .filter { c -> c.isActive == true }
                                .map { c ->
                                    ContactOption(
                                        c.contactId,
                                        c.fullName ?: c.nickname ?: c.contactId
                                    )
                                },
                            selectedContactIds = if (selectedContactIds.isNotEmpty()) selectedContactIds else it.selectedContactIds,
                            isLoadingContacts = false
                        )
                    }
                }.onFailure {
                    _uiState.update { it.copy(isLoadingContacts = false) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingContacts = false) }
            }
        }
    }

    private fun loadActivity(id: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            activityRepo.getActivityById(id).onSuccess { list ->
                val activity = list.firstOrNull() ?: return@onSuccess

                val savedItems = activityRepo.getPlanItems(id).getOrDefault(emptyList())
                val selectedMasterIds = savedItems.filter { it.masterId != 999 }.map { it.masterId }.toSet()
                val otherItem = savedItems.find { it.masterId == 999 }
                
                val selectedContactIds = activityRepo.getAppointmentContacts(id).toSet()

                _uiState.update {
                    it.copy(
                        activityId        = activity.activityId,
                        selectedProjectId = activity.projectId,
                        selectedCustomerId = activity.customerId,
                        titleTopic        = activity.detail ?: "",
                        activityType      = activity.activityType,
                        plannedDate       = formatDateForUI(activity.activityDate),
                        startTime         = activity.plannedTime,
                        endTime           = activity.plannedEndTime,
                        lat               = activity.plannedLat,
                        lng               = activity.plannedLong,
                        selectedMasterIds = selectedMasterIds,
                        selectedContactIds = selectedContactIds,
                        isOtherSelected   = otherItem != null,
                        otherObjectiveText = otherItem?.masterDetails?.actName ?: "",
                        isLoading         = false
                    )
                }

                if (activity.projectId != null) {
                    loadContactsForProject(activity.projectId, selectedContactIds)
                    projectRepo.getProjectById(activity.projectId).onSuccess { p ->
                        _uiState.update { it.copy(selectedProjectName = p.projectName) }
                    }
                } else {
                    // ถ้าไม่มี Project ให้ใช้ allContactOptions
                    _uiState.update { state ->
                        state.copy(
                            contactOptions = state.allContactOptions,
                            masterOptions = state.allMasterOptions
                        )
                    }
                }
            }
        }
    }

    private fun formatTimeForUI(timeStr: String): String {
        return try {
            val cleanTime = timeStr.trim()
            val inputFormats = listOf("HH:mm:ss", "HH:mm", "hh:mm:ss a", "hh:mm a")
            var parsedDate: java.util.Date? = null

            for (format in inputFormats) {
                try {
                    val sdf = java.text.SimpleDateFormat(format, java.util.Locale.ENGLISH)
                    parsedDate = sdf.parse(cleanTime)
                    if (parsedDate != null) break
                } catch (e: Exception) { continue }
            }

            if (parsedDate == null) return timeStr
            val outputFormat = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.ENGLISH)
            outputFormat.format(parsedDate)
        } catch (e: Exception) {
            timeStr
        }
    }

    private fun loadInitialProject(projectId: String) {
        viewModelScope.launch {
            projectRepo.getProjectById(projectId).onSuccess { p ->
                _uiState.update {
                    it.copy(
                        selectedProjectId   = p.projectId,
                        selectedProjectName = p.projectName,
                        selectedCustomerId  = p.custId
                    )
                }
                loadContactsForProject(projectId)
            }
        }
    }

    fun onEvent(event: CreateAppointmentEvent) {
        when (event) {
            is CreateAppointmentEvent.LoadActivity ->
                loadActivity(event.activityId)

            is CreateAppointmentEvent.LoadInitialProject ->
                loadInitialProject(event.projectId)

            is CreateAppointmentEvent.ProjectSelected -> {
                if (event.id == null) {
                   _uiState.update {
                       it.copy(
                           selectedProjectId = null,
                           selectedProjectName = "ไม่ระบุโครงการ",
                           projectError = null,
                           contactOptions = it.allContactOptions,
                           masterOptions = it.allMasterOptions,
                           selectedContactIds = emptySet()
                       )
                   }
                } else {
                    _uiState.update {
                        it.copy(
                            selectedProjectId   = event.id,
                            selectedProjectName = event.name,
                            projectError        = null,
                            selectedContactIds  = emptySet(),
                            selectedMasterIds   = if (it.activityId == null) emptySet() else it.selectedMasterIds,
                            contactOptions      = emptyList()
                        )
                    }
                    loadContactsForProject(event.id)
                    filterMastersByProjectStatus(event.status ?: "")
                }
            }

            is CreateAppointmentEvent.TitleChanged ->
                _uiState.update { it.copy(titleTopic = event.value) }

            is CreateAppointmentEvent.TypeChanged ->
                _uiState.update { it.copy(activityType = event.value) }

            is CreateAppointmentEvent.ContactToggled -> {
                val current = _uiState.value.selectedContactIds.toMutableSet()
                if (event.id in current) current.remove(event.id) else current.add(event.id)
                _uiState.update { it.copy(selectedContactIds = current) }
            }

            is CreateAppointmentEvent.ContactSearchQueryChanged -> {
                _uiState.update { it.copy(contactSearchQuery = event.value) }
            }

            is CreateAppointmentEvent.MasterToggled -> {
                val current = _uiState.value.selectedMasterIds.toMutableSet()
                if (event.id in current) current.remove(event.id) else current.add(event.id)
                _uiState.update { it.copy(selectedMasterIds = current, masterError = null) }
            }

            CreateAppointmentEvent.OtherToggled -> {
                _uiState.update { it.copy(isOtherSelected = !it.isOtherSelected) }
            }

            is CreateAppointmentEvent.OtherObjectiveTextChanged -> {
                _uiState.update { it.copy(otherObjectiveText = event.value) }
            }

            is CreateAppointmentEvent.DateChanged ->
                _uiState.update { it.copy(plannedDate = event.value) }

            is CreateAppointmentEvent.StartTimeSelected ->
                _uiState.update { it.copy(startTime = event.value, showStartTimePicker = false) }

            is CreateAppointmentEvent.EndTimeSelected ->
                _uiState.update { it.copy(endTime = event.value, showEndTimePicker = false) }

            is CreateAppointmentEvent.LocationPicked ->
                _uiState.update { it.copy(lat = event.lat, lng = event.lng) }

            CreateAppointmentEvent.ShowStartTimePicker ->
                _uiState.update { it.copy(showStartTimePicker = true) }

            CreateAppointmentEvent.ShowEndTimePicker ->
                _uiState.update { it.copy(showEndTimePicker = true) }

            CreateAppointmentEvent.DismissTimePicker ->
                _uiState.update { it.copy(showStartTimePicker = false, showEndTimePicker = false) }

            CreateAppointmentEvent.Save -> save()
        }
    }

    private fun formatTimeToDb(uiTime: String?): String? {
        if (uiTime.isNullOrBlank()) return null
        return try {
            val inputFormat = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.ENGLISH)
            val outputFormat = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.ENGLISH)
            val date = inputFormat.parse(uiTime)
            date?.let { outputFormat.format(it) } ?: uiTime
        } catch (e: Exception) {
            uiTime
        }
    }

    private fun save() {
        val s = _uiState.value

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, saveError = null) }

            val userId = authRepo.currentUser()?.userId ?: run {
                _uiState.update { it.copy(isLoading = false, saveError = "ไม่พบข้อมูล User กรุณา Login ใหม่") }
                return@launch
            }

            var customerId = s.selectedCustomerId
            if (customerId == null && s.selectedProjectId != null) {
                val project = projectRepo.getProjectById(s.selectedProjectId).getOrNull()
                customerId = project?.custId
            }
            
            // ถ้ายังไม่มี customerId (กรณีไม่เลือกโปรเจกต์) ให้พยายามหาจากผู้ติดต่อที่เลือก
            if (customerId == null && s.selectedContactIds.isNotEmpty()) {
                val contactId = s.selectedContactIds.first()
                customerRepo.getAllContacts().first().find { it.contactId == contactId }?.let {
                    customerId = it.custId
                }
            }

            if (customerId == null && s.selectedProjectId != null) {
                _uiState.update { it.copy(isLoading = false, saveError = "ไม่พบข้อมูลลูกค้า") }
                return@launch
            }

            val isEditMode = s.activityId != null
            val appointmentId = s.activityId
                ?: ("APT-" + UUID.randomUUID().toString().take(8).uppercase())

            val isoDate = s.plannedDate?.let { parseToIsoDate(it) } ?: LocalDate.now().toString()

            val selectedContactNames = s.allContactOptions
                .filter { it.id in s.selectedContactIds }
                .joinToString(", ") { it.name }

            val activity = SalesActivity(
                activityId     = appointmentId,
                userId         = userId,
                customerId     = customerId ?: "CST-UNKNOWN",
                projectId      = s.selectedProjectId,
                activityType   = s.activityType,
                isAppointment  = true,
                detail         = s.titleTopic,
                activityDate   = s.plannedDate?.let { parseToIsoDate(it) }
                    ?: LocalDate.now().toString(),
                plannedTime    = s.startTime,
                plannedEndTime = s.endTime,
                plannedLat     = s.lat,
                plannedLong    = s.lng,
                status         = "planned",
                contactName    = selectedContactNames
            )


            val result = if (isEditMode) {
                val updates = mutableMapOf<String, Any>(
                    "type" to s.activityType,
                    "planned_date" to isoDate,
                    "topic" to s.titleTopic,
                    "planned_time" to (formatTimeToDb(s.startTime) ?: ""),
                    "planned_end_time" to (formatTimeToDb(s.endTime) ?: ""),
                    "planned_lat" to (s.lat ?: 0.0),
                    "planned_long" to (s.lng ?: 0.0),
                    "is_appointment" to s.selectedContactIds.isNotEmpty()
                )
                activityRepo.updateActivity(appointmentId, updates).onSuccess {
                    activityRepo.addActivity(activity)
                }
            } else {
                activityRepo.addActivity(activity)
            }

            if (result.isFailure) {
                _uiState.update { 
                    it.copy(
                        isLoading = false, 
                        saveError = result.exceptionOrNull()?.message ?: "บันทึกไม่สำเร็จ"
                    ) 
                }
                return@launch
            }

            // Save selected contacts
            activityRepo.saveAppointmentContacts(appointmentId, s.selectedContactIds.toList())

            // Checklist items
            val planItems = mutableListOf<ActivityPlanItem>()
            
            // Standard masters
            s.selectedMasterIds.forEach { mid ->
                planItems.add(
                    ActivityPlanItem(
                        appointmentId = appointmentId,
                        masterId      = mid,
                        isDone        = false,
                        actName       = s.allMasterOptions.find { it.masterId == mid }?.actName
                    )
                )
            }
            
            // "Other" objective
            if (s.isOtherSelected && s.otherObjectiveText.isNotBlank()) {
                planItems.add(
                    ActivityPlanItem(
                        appointmentId = appointmentId,
                        masterId      = 999, // Reserved for Other
                        isDone        = false,
                        actName       = s.otherObjectiveText
                    )
                )
            }

            if (planItems.isNotEmpty()) {
                activityRepo.savePlanItems(appointmentId, planItems)
            }

            _uiState.update { it.copy(isLoading = false, isSaved = true) }
        }
    }

    private fun parseToIsoDate(uiDate: String): String {
        return try {
            val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.ENGLISH)
            LocalDate.parse(uiDate, formatter).toString()
        } catch (e: Exception) {
            try {
                val formatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
                LocalDate.parse(uiDate, formatter).toString()
            } catch (e2: Exception) { uiDate }
        }
    }

    private fun formatDateForUI(isoDate: String?): String? {
        if (isoDate == null) return null
        return try {
            val date = LocalDate.parse(isoDate.take(10))
            date.format(DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.ENGLISH))
        } catch (e: Exception) { isoDate }
    }

    private fun getCategoryForProjectStatus(status: String): String? {
        return when (status.trim().lowercase()) {
            "lead"            -> "Lead"
            "new project"     -> "New Project"
            "quotation"       -> "Quotation"
            "bidding"         -> "Bidding"
            "make a decision" -> "Make a Decision"
            "assured"         -> "Assured"
            "po"              -> "PO"
            else              -> null
        }
    }

    private fun filterMastersByProjectStatus(status: String) {
        val category = getCategoryForProjectStatus(status)
        val allMasters = _uiState.value.allMasterOptions
        val filtered = if (category != null) {
            allMasters.filter { it.category == category }
        } else {
            allMasters
        }
        _uiState.update { it.copy(masterOptions = filtered) }
    }
}
