package com.example.pp68_salestrackingapp.ui.viewmodels.activity

import com.example.pp68_salestrackingapp.utils.AppointmentPolicy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pp68_salestrackingapp.data.repository.ActivityRepository
import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import com.example.pp68_salestrackingapp.data.repository.ContactRepository
import com.example.pp68_salestrackingapp.data.repository.CustomerRepository
import com.example.pp68_salestrackingapp.data.repository.ProjectRepository
import com.example.pp68_salestrackingapp.data.model.ActivityMaster
import com.example.pp68_salestrackingapp.data.model.SalesActivity
import com.example.pp68_salestrackingapp.data.model.ActivityPlanItem
import com.example.pp68_salestrackingapp.utils.DraftStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import com.example.pp68_salestrackingapp.utils.withUniqueLabels

data class CreateAppointmentUiState(
    val activityId:          String? = null,
    // W6: ค่าดั้งเดิมตอนโหลดมาแก้ไข ใช้เช็คว่าห้ามแก้ไข/ลบเพราะใกล้วันนัดหรือยัง (ไม่ใช้ค่าที่กำลังพิมพ์แก้)
    val originalStatus:      String? = null,
    val originalPlannedDate: String? = null,
    // กติกาแก้ไขตัดสินจาก "แผนเดิม" ไม่ใช่ค่าที่กำลังพิมพ์แก้อยู่ จึงต้องเก็บเวลาเริ่มและสถานะ
    // เช็คอินของแผนเดิมไว้ด้วย ไม่งั้นจะล็อกตั้งแต่ 00:00 ของทุกแถวที่มีเวลานัดจริง
    val originalPlannedTime: String? = null,
    val originalCheckedIn:   Boolean = false,
    /** เตือนก่อนกดบันทึก ไม่ใช่หลังบันทึก — ผู้ใช้ยังเลือกต่อเน็ตก่อนได้ (แผนงาน B.4 ข้อ 5) */
    val timeAnchorWarning:   String? = null,
    val selectedProjectId:   String? = null,
    val selectedProjectName: String? = null,
    val selectedCustomerId:  String? = null,
    val selectedCompanyName: String? = null,
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
    val companyOptions:  List<Pair<String, String>> = emptyList(), // custId to companyName สำหรับค้นหาบริษัท (กรณีไม่เลือกโครงการ)

    val contactSearchQuery: String = "",

    val isLoading:         Boolean = false,
    val isLoadingProjects: Boolean = false,
    val isLoadingContacts: Boolean = false,
    val isLoadingMasters:  Boolean = false,
    val isLoadingCompanies: Boolean = false,
    val companySearchMessage: String? = null,
    val isSaved:           Boolean = false,

    val projectError: String? = null,
    val masterError:  String? = null,
    val saveError:    String? = null,

    val showStartTimePicker: Boolean = false,
    val showEndTimePicker:   Boolean = false,

    val draftAvailable: Boolean = false,

    // สร้างบริษัทลูกค้า (Lead) ด่วน — กรอกแค่ชื่อ+ประเภท ฟิลด์อื่นเติมทีหลังที่หน้าลูกค้าได้
    val isQuickAddCustomerOpen: Boolean = false,
    val quickAddCompanyName: String = "",
    val quickAddCustType: String = "",
    val isSavingQuickCust: Boolean = false,
    val quickAddCustomerError: String? = null,

    // สร้างโครงการด่วน — กรอกแค่ชื่อ+สถานะ (เหมือนหน้าบันทึกผล)
    val isQuickAddProjectOpen: Boolean = false,
    val quickAddProjectName: String = "",
    val quickAddProjectStatus: String = "",
    val isSavingQuickProject: Boolean = false,
    val quickAddProjectError: String? = null,

    // สร้างผู้ติดต่อด่วน — เจอบ่อยว่าบริษัทมีแล้วแต่คนที่จะเข้าพบยังไม่เคยถูกบันทึก
    val isQuickAddContactOpen: Boolean = false,
    val quickAddContactName: String = "",
    val quickAddContactPhone: String = "",
    val isSavingQuickContact: Boolean = false,
    val quickAddContactError: String? = null
)

data class ProjectOption(val id: String, val name: String, val status: String)
data class ContactOption(val id: String, val name: String, val companyName: String? = null)

data class CreateAppointmentDraft(
    val selectedProjectId: String? = null,
    val selectedCustomerId: String? = null,
    val titleTopic: String = "",
    val activityType: String = "onsite",
    val plannedDate: String? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val selectedContactIds: Set<String> = emptySet(),
    val selectedMasterIds: Set<Int> = emptySet(),
    val isOtherSelected: Boolean = false,
    val otherObjectiveText: String = ""
)

sealed class CreateAppointmentEvent {
    data class LoadActivity(val activityId: String)         : CreateAppointmentEvent()
    data class LoadInitialProject(val projectId: String)    : CreateAppointmentEvent()
    object CheckDraft         : CreateAppointmentEvent()
    object RestoreDraft       : CreateAppointmentEvent()
    object DismissDraftPrompt : CreateAppointmentEvent()
    data class ProjectSelected(val id: String?, val name: String?, val status: String?) : CreateAppointmentEvent()
    data class CompanySelected(val id: String, val name: String) : CreateAppointmentEvent()
    data class CompanyQueryChanged(val value: String)           : CreateAppointmentEvent()
    data class ToggleQuickAddCustomer(val isOpen: Boolean)   : CreateAppointmentEvent()
    data class QuickAddCustomerChanged(val name: String, val type: String) : CreateAppointmentEvent()
    object SaveQuickAddCustomer                             : CreateAppointmentEvent()
    data class ToggleQuickAddProject(val isOpen: Boolean)   : CreateAppointmentEvent()
    data class QuickAddProjectNameChanged(val value: String)   : CreateAppointmentEvent()
    data class QuickAddProjectStatusChanged(val value: String) : CreateAppointmentEvent()
    object SaveQuickAddProject                              : CreateAppointmentEvent()
    data class ToggleQuickAddContact(val isOpen: Boolean)   : CreateAppointmentEvent()
    data class QuickAddContactChanged(val name: String, val phone: String) : CreateAppointmentEvent()
    object SaveQuickAddContact                              : CreateAppointmentEvent()
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
    data class LocationPicked(val lat: Double?, val lng: Double?) : CreateAppointmentEvent()
    object ShowStartTimePicker  : CreateAppointmentEvent()
    object ShowEndTimePicker    : CreateAppointmentEvent()
    object DismissTimePicker    : CreateAppointmentEvent()
    object Save                 : CreateAppointmentEvent()
}

@HiltViewModel
class CreateAppointmentViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val activityRepo: ActivityRepository,
    private val projectRepo:  ProjectRepository,
    private val customerRepo: CustomerRepository,
    private val contactRepo:  ContactRepository,
    private val authRepo:     AuthRepository,
    private val draftStore:   DraftStore,
    private val clock:        java.time.Clock,
    private val serverTimeAnchor: com.example.pp68_salestrackingapp.utils.ServerTimeAnchor
) : ViewModel() {

    /**
     * ไม่มีเวลาที่เชื่อถือได้ (ยังไม่เคยคุยกับเซิร์ฟเวอร์ หรือเครื่องรีบูต/เวลาถูกแก้) และนัดใกล้จะถึง
     *
     * เคสนี้ server จะตัดสินจากเวลาที่คำขอเดินทางไปถึง ซึ่งอาจเลยเวลานัดไปแล้วถ้าส่งขึ้นช้า
     * บอกตามตรงดีกว่าปล่อยให้กดบันทึกแล้วเข้าใจว่าเรียบร้อย แล้วไปตกตอน sync ทีหลัง
     */
    private fun timeAnchorWarningFor(
        activity: com.example.pp68_salestrackingapp.data.model.SalesActivity
    ): String? {
        if (serverTimeAnchor.nowOrNull() != null) return null
        val start = runCatching {
            java.time.LocalDate.parse(activity.activityDate.take(10))
                .atTime(
                    activity.plannedTime?.trim()?.take(5)
                        ?.let { runCatching { java.time.LocalTime.parse(it) }.getOrNull() }
                        ?: java.time.LocalTime.MIDNIGHT
                )
                .atZone(AppointmentPolicy.THAI_ZONE).toInstant()
        }.getOrNull() ?: return null
        val now = java.time.Instant.now(clock)
        if (start.isBefore(now) || start.isAfter(now.plus(java.time.Duration.ofHours(24)))) return null
        return "ยังไม่ได้เวลาอ้างอิงจากเซิร์ฟเวอร์ และนัดนี้ใกล้ถึงเวลาแล้ว " +
            "ถ้าบันทึกตอนไม่มีเน็ต การแก้อาจถูกปฏิเสธตอนส่งขึ้น กรุณาเชื่อมต่ออินเทอร์เน็ตก่อนบันทึก"
    }

    /** กติกาเดียวกับที่ ActivityRepository.updateActivity ใช้บล็อกจริง อ่านจากแผนเดิมเท่านั้น */
    private fun editDecision(s: CreateAppointmentUiState): AppointmentPolicy.Decision =
        AppointmentPolicy.canEdit(
            AppointmentPolicy.Facts(
                status = s.originalStatus,
                activityType = s.activityType,
                plannedDate = s.originalPlannedDate,
                plannedTime = s.originalPlannedTime,
                checkedIn = s.originalCheckedIn
            ),
            clock
        )

    private val _uiState = MutableStateFlow(CreateAppointmentUiState())
    val uiState: StateFlow<CreateAppointmentUiState> = _uiState
    private var leadCompanyOptions: List<Pair<String, String>> = emptyList()
    private var companySearchJob: Job? = null

    private val draft = com.example.pp68_salestrackingapp.utils.DraftController(
        store = draftStore,
        type = CreateAppointmentDraft::class.java,
        initialBaseline = CreateAppointmentDraft(),
        keyOf = { draftKey() },
        currentOf = { _uiState.value.toDraft() }
    )

    // นัดหมายใหม่แบบ onsite ตั้งพิกัดให้อัตโนมัติจาก GPS หลัง CheckDraft จับ baseline ไปแล้ว (เพราะ
    // ต้องรอ permission/GPS fetch ที่เป็น async แยกอยู่ในหน้าจอ) ถ้าไม่กันไว้ ผู้ใช้เปิดหน้าจอเฉยๆ
    // ไม่แตะอะไรเลยก็จะโดนถามว่า "มีข้อมูลยังไม่ได้บันทึก" ทันทีที่กดย้อนกลับ — เพราะ GPS auto-fill
    // นับเป็นความต่างจาก baseline ไปแล้ว จึงต้องดูด lat/lng ที่ได้จาก auto-fill ครั้งแรกเข้า baseline ด้วย
    private var awaitingInitialLocation = false

    private fun CreateAppointmentUiState.toDraft() = CreateAppointmentDraft(
        selectedProjectId, selectedCustomerId, titleTopic, activityType, plannedDate, startTime,
        endTime, lat, lng, selectedContactIds, selectedMasterIds, isOtherSelected, otherObjectiveText
    )

    // ✅ ไม่งั้นนัดหมายใหม่แบบไม่ผูกโครงการ กับนัดหมายใหม่ที่เปิดมาจากหน้าโครงการ (prefill
    // selectedProjectId ไว้แล้ว) จะใช้ key "new" ร่วมกัน — เจอ draft เก่าที่ไม่เกี่ยวข้องมาแล้วกู้คืน
    // ทับข้อมูลโครงการที่ prefill ไว้แบบไม่มีการเตือนเลย
    private fun draftKey(): String {
        val s = _uiState.value
        return "create_appointment:${s.activityId ?: "new:${s.selectedProjectId ?: "none"}"}"
    }

    fun checkForDraft() {
        val s = _uiState.value
        awaitingInitialLocation = s.activityId == null && s.activityType == "onsite" &&
            s.lat == null && s.lng == null
        if (draft.check()) _uiState.update { it.copy(draftAvailable = true) }
    }

    fun isDirty(): Boolean = draft.isDirty()

    fun saveDraft() = draft.save()

    fun discardDraft() = draft.discard()

    fun restoreDraft() {
        val d = draft.takePending() ?: return
        _uiState.update {
            it.copy(
                selectedProjectId = d.selectedProjectId,
                selectedCustomerId = d.selectedCustomerId,
                titleTopic = d.titleTopic,
                activityType = d.activityType,
                plannedDate = d.plannedDate,
                startTime = d.startTime,
                endTime = d.endTime,
                lat = d.lat,
                lng = d.lng,
                selectedContactIds = d.selectedContactIds,
                selectedMasterIds = d.selectedMasterIds,
                isOtherSelected = d.isOtherSelected,
                otherObjectiveText = d.otherObjectiveText,
                draftAvailable = false
            )
        }
        d.selectedProjectId?.let { loadContactsForProject(it, d.selectedContactIds) }
    }

    fun dismissDraftPrompt() {
        _uiState.update { it.copy(draftAvailable = false) }
    }

    init {
        loadProjects()
        loadMasterObjectives()
        loadAllContacts()
        loadAllCompanies()
    }

    private fun loadAllCompanies() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingCompanies = true) }
            customerRepo.getCustomers().onSuccess { customers ->
                leadCompanyOptions = customers.map { c -> c.custId to c.companyName }.withUniqueLabels()
                _uiState.update {
                    it.copy(
                        companyOptions    = leadCompanyOptions,
                        isLoadingCompanies = false
                    )
                }
            }.onFailure {
                _uiState.update { it.copy(isLoadingCompanies = false) }
            }
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

    private fun loadAllContacts() {
        viewModelScope.launch {
            customerRepo.getAllContacts().collect { contacts ->
                val options = contacts.map {
                    ContactOption(it.contactId, it.fullName ?: it.nickname ?: it.contactId)
                }
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

    // สร้างบริษัทลูกค้าแบบ Lead ด่วน — กรอกแค่ชื่อกับประเภท แล้วเลือกให้ทันที (เหมือน saveQuickCustomer
    // ใน AddProjectViewModel) isLead = true เสมอ เพราะยังไม่ผ่านการคัดกรองเป็นลูกค้าจริง
    /**
     * ผู้ติดต่อต้องผูกกับบริษัทเสมอ จึงเปิดให้สร้างได้เฉพาะตอนเลือกบริษัทแล้ว
     * เบอร์โทรบังคับกรอกเหมือนหน้าเพิ่มผู้ติดต่อเต็ม — ผู้ติดต่อที่โทรหาไม่ได้ไม่มีประโยชน์กับงานขาย
     */
    private fun saveQuickContact() {
        val s = _uiState.value
        val custId = s.selectedCustomerId
        if (custId.isNullOrBlank()) {
            _uiState.update { it.copy(quickAddContactError = "เลือกบริษัทก่อนจึงจะเพิ่มผู้ติดต่อได้") }
            return
        }
        if (s.quickAddContactName.isBlank() || s.quickAddContactPhone.isBlank()) {
            _uiState.update { it.copy(quickAddContactError = "กรุณาระบุชื่อและเบอร์โทรศัพท์ให้ครบ") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingQuickContact = true, quickAddContactError = null) }
            val newContact = com.example.pp68_salestrackingapp.data.model.ContactPerson(
                // TEMP- จำเป็น — outbox ใช้คำนำหน้านี้ตัดสินว่าจะ POST หรือ PATCH
                contactId = "TEMP-" + java.util.UUID.randomUUID().toString().take(8).uppercase(),
                custId = custId,
                customerName = s.selectedCompanyName,
                fullName = s.quickAddContactName.trim(),
                phoneNumber = s.quickAddContactPhone.trim(),
                // เจ้าของผู้ติดต่อ = คนที่สร้าง ใช้กรองการมองเห็น (บริษัทเห็นร่วมกันทั้งสาขา แต่
                // ผู้ติดต่อเห็นเฉพาะคนสร้าง) เซิร์ฟเวอร์บังคับค่านี้จาก JWT อยู่แล้ว แต่ต้องใส่ใน
                // แถวที่เขียนลง Room ด้วย ไม่งั้นช่วงที่ยังไม่ซิงค์ แถวจะไม่มีเจ้าของแล้วเห็นไม่ตรงกัน
                createdBy = authRepo.currentUser()?.userId
            )
            contactRepo.addContact(newContact).fold(
                onSuccess = { contactId ->
                    _uiState.update {
                        it.copy(
                            contactOptions = it.contactOptions + ContactOption(
                                id = contactId,
                                name = newContact.fullName ?: "",
                                companyName = it.selectedCompanyName
                            ),
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
                        it.copy(isSavingQuickContact = false, quickAddContactError = e.message ?: "สร้างผู้ติดต่อไม่สำเร็จ")
                    }
                }
            )
        }
    }

    private fun saveQuickCustomer() {
        val s = _uiState.value
        if (s.quickAddCompanyName.isBlank() || s.quickAddCustType.isBlank()) {
            _uiState.update { it.copy(quickAddCustomerError = "กรุณาระบุชื่อบริษัทและประเภทลูกค้าให้ครบ") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingQuickCust = true, quickAddCustomerError = null) }
            val user = authRepo.currentUser()
            val newCust = com.example.pp68_salestrackingapp.data.model.Customer(
                // ต้องมีคำนำหน้า TEMP- — outbox ใช้มันตัดสินว่าจะ POST (สร้างใหม่)
                // หรือ PATCH (แก้ของเดิม) ถ้าเป็น UUID เปล่า การสร้างตอนไม่มีเน็ตจะกลายเป็น PATCH
                // ไปหาแถวที่ไม่มีจริง → 404 → ถูกบันทึกเป็นการปฏิเสธถาวร ลูกค้าหายไปเงียบ ๆ
                custId = "TEMP-" + java.util.UUID.randomUUID().toString().take(8).uppercase(),
                companyName = s.quickAddCompanyName.trim(),
                custType = s.quickAddCustType,
                createdBy = user?.userId,
                // ไม่ตั้ง bizPostingGroup โดยตั้งใจ — มันคือเทียร์ลูกค้าที่ MS Dynamics 365 เป็นเจ้าของ
                // เดิมใส่รหัสสาขาของเซลส์ลงไป ทำให้ป้าย BizGroupBadge แสดงรหัสสาขาแทนเทียร์
                // และชิปกรอง R/W/I/P กรองลูกค้ากลุ่มนี้ไม่เจอ — ปล่อย null ให้ ERP เติม
                // (body ทั้ง create และ update มี filterValues ตัด null ทิ้ง จึงไม่ไปล้างค่าเดิมบน server)
                isLead = true
            )
            customerRepo.addCustomer(newCust).fold(
                onSuccess = { realCustId ->
                    _uiState.update {
                        it.copy(
                            companyOptions = (it.companyOptions + (realCustId to newCust.companyName)).withUniqueLabels(),
                            selectedCustomerId = realCustId,
                            selectedCompanyName = newCust.companyName,
                            // บริษัทใหม่ยังไม่มีผู้ติดต่อ ล้างของบริษัทเดิมทิ้งไม่ให้ค้าง
                            selectedContactIds = emptySet(),
                            contactOptions = emptyList(),
                            isQuickAddCustomerOpen = false,
                            isSavingQuickCust = false,
                            quickAddCompanyName = "",
                            quickAddCustType = ""
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isSavingQuickCust = false, quickAddCustomerError = e.message ?: "สร้างบริษัทไม่สำเร็จ") }
                }
            )
        }
    }

    // สร้างโครงการด่วน — กรอกแค่ชื่อกับสถานะ แล้วผูกกับนัดหมายนี้ทันที (เหมือน saveQuickProject
    // ใน SalesResultViewModel) ผู้ติดต่อจะถูกโหลดใหม่ตามลูกค้าของโครงการที่สร้าง
    private fun saveQuickProject() {
        val s = _uiState.value
        if (s.quickAddProjectName.isBlank() || s.quickAddProjectStatus.isBlank()) {
            _uiState.update { it.copy(quickAddProjectError = "กรุณาระบุชื่อโครงการและสถานะให้ครบ") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingQuickProject = true, quickAddProjectError = null) }
            val user = authRepo.currentUser()
            val newProject = com.example.pp68_salestrackingapp.data.model.Project(
                projectId = "",
                projectName = s.quickAddProjectName.trim(),
                projectStatus = s.quickAddProjectStatus,
                branchId = user?.teamId,
                // ผูกกับบริษัทที่เลือกไว้แล้ว (ถ้ามี) ไม่งั้นโครงการจะลอยไม่มีลูกค้า
                custId = s.selectedCustomerId,
                createBy = user?.userId
            )
            projectRepo.createProject(newProject, user?.userId ?: "").fold(
                onSuccess = { created ->
                    _uiState.update {
                        it.copy(
                            projectOptions = it.projectOptions +
                                ProjectOption(created.projectId, created.projectName, created.projectStatus ?: ""),
                            selectedProjectId = created.projectId,
                            selectedProjectName = created.projectName,
                            projectError = null,
                            selectedContactIds = emptySet(),
                            isQuickAddProjectOpen = false,
                            isSavingQuickProject = false,
                            quickAddProjectName = "",
                            quickAddProjectStatus = ""
                        )
                    }
                    // โหลดผู้ติดต่อ/วัตถุประสงค์ตามโครงการใหม่ เหมือนตอนเลือกโครงการจาก dropdown
                    loadContactsForProject(created.projectId)
                    filterMastersByProjectStatus(created.projectStatus ?: "")
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isSavingQuickProject = false, quickAddProjectError = e.message ?: "สร้างโครงการไม่สำเร็จ") }
                }
            )
        }
    }

    // เลือกบริษัทแล้วต้องเห็นผู้ติดต่อของบริษัทนั้นทันที ไม่ต้องพิมพ์ค้นหา — เดิมเลือกบริษัทแล้ว
    // ไม่มีอะไรเกิดขึ้นเลย ช่องผู้ติดต่อจึงตกไปใช้โหมดค้นหาทั้งฐานข้อมูล (ต้องพิมพ์ก่อนถึงจะขึ้น)
    // ใช้ตอนไม่ได้เลือกโครงการ ถ้าเลือกโครงการอยู่แล้ว loadContactsForProject จัดการให้แล้ว
    private fun loadContactsForCustomer(custId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingContacts = true, contactOptions = emptyList()) }
            customerRepo.getContactPersons(custId, null).fold(
                onSuccess = { contacts ->
                    _uiState.update { st ->
                        st.copy(
                            contactOptions = contacts
                                .filter { c -> c.isActive != false }
                                .map { c -> ContactOption(c.contactId, c.fullName ?: c.nickname ?: c.contactId) },
                            isLoadingContacts = false
                        )
                    }
                },
                onFailure = { _uiState.update { it.copy(isLoadingContacts = false) } }
            )
        }
    }

    private fun loadContactsForProject(projectId: String, selectedContactIds: Set<String> = emptySet()) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingContacts = true, contactOptions = emptyList()) }
            try {
                val project = projectRepo.getProjectById(projectId).getOrNull()
                // ✅ ทางออกก่อนกำหนดตรงนี้เดิมไม่ปิด isLoadingContacts ผู้ใช้จึงค้างอยู่กับวงกลม
                // หมุนตลอดไปในช่องผู้ติดต่อ (โหลดโครงการไม่ได้ หรือโครงการไม่ได้ผูกบริษัทไว้)
                // แทนที่จะได้เห็นว่าเกิดอะไรขึ้น
                if (project?.custId.isNullOrBlank()) {
                    _uiState.update { it.copy(isLoadingContacts = false, contactOptions = emptyList()) }
                    return@launch
                }
                val custId  = project!!.custId!!
                val status  = project.projectStatus ?: ""

                _uiState.update { it.copy(selectedCustomerId = custId) }
                customerRepo.getCustomerById(custId).onSuccess { c ->
                    _uiState.update { it.copy(selectedCompanyName = c.companyName) }
                }

                val category = getCategoryForProjectStatus(status)
                val filtered = if (category != null) {
                    _uiState.value.allMasterOptions.filter { it.category == category }
                } else {
                    _uiState.value.allMasterOptions
                }

                _uiState.update { it.copy(masterOptions = filtered) }

                customerRepo.getContactPersons(custId, null).onSuccess { contacts ->
                    _uiState.update {
                        it.copy(
                            contactOptions    = contacts
                                .filter { c -> c.isActive != false }
                                .map { c ->
                                    ContactOption(
                                        c.contactId,
                                        c.fullName ?: c.nickname ?: c.contactId
                                    )
                                },
                            selectedContactIds = if (selectedContactIds.isNotEmpty()) {
                                selectedContactIds
                            } else {
                                it.selectedContactIds
                            },
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
                        originalStatus      = activity.status,
                        originalPlannedDate = activity.activityDate,
                        originalPlannedTime = activity.plannedTime,
                        originalCheckedIn   = !activity.checkInTime.isNullOrBlank(),
                        timeAnchorWarning   = timeAnchorWarningFor(activity),
                        selectedProjectId = activity.projectId,
                        // ✅ CST-UNKNOWN เป็นค่า sentinel ของนัดที่ไม่ระบุลูกค้า ไม่ใช่รหัสลูกค้าจริง
                        // ถ้าปล่อยเข้ามาเป็น selectedCustomerId ตรง ๆ การ "สร้างผู้ติดต่อด่วน" และ
                        // "สร้างโครงการด่วน" จะเช็คแค่ isNullOrBlank แล้วผ่าน ได้แถวที่ผูกกับรหัส
                        // ลูกค้าที่ไม่มีอยู่จริง (server ไม่มี FK คอยดัก) แบบเงียบ ๆ
                        // ทางบันทึกใส่ sentinel กลับให้เองอยู่แล้ว จึงไม่มีอะไรหาย
                        selectedCustomerId = activity.customerId?.takeIf { it != "CST-UNKNOWN" },
                        titleTopic        = activity.detail ?: "",
                        activityType      = activity.activityType,
                        plannedDate       = activity.activityDate,
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
                draft.captureBaseline()
                checkForDraft()

                if (activity.projectId != null) {
                    loadContactsForProject(activity.projectId, selectedContactIds)
                    projectRepo.getProjectById(activity.projectId).onSuccess { p ->
                        _uiState.update { it.copy(selectedProjectName = p.projectName) }
                        filterMastersByProjectStatus(p.projectStatus ?: "")
                    }
                } else {
                    // ถ้าไม่มี Project ให้ใช้ allContactOptions
                    _uiState.update { state ->
                        state.copy(
                            contactOptions = state.allContactOptions,
                            masterOptions = state.allMasterOptions
                        )
                    }
                    // ใช้ค่าที่กรอง sentinel แล้ว ไม่ใช่ activity.customerId ดิบ — ไม่งั้นยิงหาลูกค้า
                    // รหัส CST-UNKNOWN ที่ไม่มีอยู่จริงทุกครั้งที่เปิดนัดที่ไม่ระบุลูกค้ามาแก้
                    _uiState.value.selectedCustomerId?.let { custId ->
                        customerRepo.getCustomerById(custId).onSuccess { c ->
                            _uiState.update { it.copy(selectedCompanyName = c.companyName) }
                        }
                    }
                }
            }
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
                // สร้างใหม่จากหน้าโครงการ ค่าที่ prefill มานี้ถือเป็นจุดเริ่มต้น ไม่ใช่ของที่ผู้ใช้แก้ไข
                draft.captureBaseline()
                checkForDraft()
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
                           selectedContactIds = emptySet(),
                           selectedCustomerId = null,
                           selectedCompanyName = null
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

            is CreateAppointmentEvent.CompanySelected -> {
                val custId = event.id.ifBlank { null }
                _uiState.update {
                    it.copy(
                        selectedCustomerId  = custId,
                        selectedCompanyName = event.name.ifBlank { null },
                        // เปลี่ยนบริษัท = ผู้ติดต่อที่เลือกไว้ของบริษัทเดิมใช้ไม่ได้แล้ว ต้องล้างทิ้ง
                        // ไม่งั้นจะบันทึกนัดที่ผูกผู้ติดต่อของบริษัทอื่นไปแบบไม่มีใครเห็น
                        selectedContactIds  = emptySet(),
                        contactOptions      = emptyList()
                    )
                }
                // ถ้าเลือกโครงการอยู่ ผู้ติดต่อมาจากโครงการนั้นแล้ว ไม่ต้องโหลดทับ
                if (custId != null && _uiState.value.selectedProjectId == null) {
                    loadContactsForCustomer(custId)
                }
            }
            is CreateAppointmentEvent.CompanyQueryChanged -> searchCompanies(event.value)

            is CreateAppointmentEvent.ToggleQuickAddCustomer ->
                _uiState.update {
                    it.copy(
                        isQuickAddCustomerOpen = event.isOpen,
                        quickAddCompanyName = "", quickAddCustType = "", quickAddCustomerError = null
                    )
                }
            is CreateAppointmentEvent.QuickAddCustomerChanged ->
                _uiState.update {
                    it.copy(quickAddCompanyName = event.name, quickAddCustType = event.type, quickAddCustomerError = null)
                }
            CreateAppointmentEvent.SaveQuickAddCustomer -> saveQuickCustomer()

            is CreateAppointmentEvent.ToggleQuickAddContact ->
                _uiState.update {
                    it.copy(
                        isQuickAddContactOpen = event.isOpen,
                        quickAddContactName = "", quickAddContactPhone = "", quickAddContactError = null
                    )
                }
            is CreateAppointmentEvent.QuickAddContactChanged ->
                _uiState.update {
                    it.copy(quickAddContactName = event.name, quickAddContactPhone = event.phone, quickAddContactError = null)
                }
            CreateAppointmentEvent.SaveQuickAddContact -> saveQuickContact()

            is CreateAppointmentEvent.ToggleQuickAddProject ->
                _uiState.update {
                    it.copy(
                        isQuickAddProjectOpen = event.isOpen,
                        quickAddProjectName = "", quickAddProjectStatus = "", quickAddProjectError = null
                    )
                }
            is CreateAppointmentEvent.QuickAddProjectNameChanged ->
                _uiState.update { it.copy(quickAddProjectName = event.value, quickAddProjectError = null) }
            is CreateAppointmentEvent.QuickAddProjectStatusChanged ->
                _uiState.update { it.copy(quickAddProjectStatus = event.value, quickAddProjectError = null) }
            CreateAppointmentEvent.SaveQuickAddProject -> saveQuickProject()

            is CreateAppointmentEvent.TitleChanged ->
                _uiState.update { it.copy(titleTopic = event.value) }

            is CreateAppointmentEvent.TypeChanged -> {
                val newType = event.value
                _uiState.update {
                    if (newType != "onsite") {
                        it.copy(activityType = newType, lat = null, lng = null)
                    } else {
                        it.copy(activityType = newType)
                    }
                }
            }

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

            is CreateAppointmentEvent.LocationPicked -> {
                _uiState.update { it.copy(lat = event.lat, lng = event.lng) }
                // ครั้งแรกหลัง CheckDraft ถือเป็น GPS auto-fill ไม่ใช่ผู้ใช้แก้ไข — ดูดเข้า baseline
                // ไปด้วย ครั้งต่อไป (ผู้ใช้ปักหมุดเองจริงๆ) จะไม่โดนดูดซ้ำ นับเป็นการแก้ไขตามปกติ
                if (awaitingInitialLocation) {
                    awaitingInitialLocation = false
                    draft.absorbIntoBaseline { it.copy(lat = event.lat, lng = event.lng) }
                }
            }

            CreateAppointmentEvent.ShowStartTimePicker ->
                _uiState.update { it.copy(showStartTimePicker = true) }

            CreateAppointmentEvent.ShowEndTimePicker ->
                _uiState.update { it.copy(showEndTimePicker = true) }

            CreateAppointmentEvent.DismissTimePicker ->
                _uiState.update { it.copy(showStartTimePicker = false, showEndTimePicker = false) }

            CreateAppointmentEvent.Save -> save()
            CreateAppointmentEvent.CheckDraft -> checkForDraft()
            CreateAppointmentEvent.RestoreDraft -> restoreDraft()
            CreateAppointmentEvent.DismissDraftPrompt -> dismissDraftPrompt()
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

    // ✅ บังคับกรอกแค่หัวข้อ + วันเวลานัด (ประเภทกิจกรรมมีค่า default อยู่แล้วไม่มีทางว่าง)
    // ส่วนโครงการ/บริษัท/ผู้ติดต่อ เป็น optional ทั้งหมด ไม่ใส่ก็เซฟได้
    // ยกเว้นนัดแบบ onsite ที่ต้องมีพิกัด เพราะการเช็คอินเอาไปวัดระยะ — ถ้าไม่มีจุดให้เทียบ
    // ระบบจะบันทึกว่า "ยืนยันตำแหน่งแล้ว ห่าง 0 เมตร" ทั้งที่ไม่ได้ตรวจอะไรเลย
    private fun validate(): Boolean {
        val s = _uiState.value
        return when {
            s.titleTopic.isBlank() -> {
                _uiState.update { it.copy(saveError = "กรุณากรอกหัวข้อกิจกรรม") }
                false
            }
            s.plannedDate.isNullOrBlank() -> {
                _uiState.update { it.copy(saveError = "กรุณาเลือกวันที่นัดหมาย") }
                false
            }
            s.startTime.isNullOrBlank() -> {
                _uiState.update { it.copy(saveError = "กรุณาเลือกเวลานัดหมาย") }
                false
            }
            // สร้างนัดหมายใหม่ย้อนหลังไม่ได้ — เช็คเฉพาะตอนสร้างใหม่ ไม่บล็อกการแก้ไขนัดหมายเดิมที่วันที่ผ่านไปแล้ว
            s.activityId == null && isPastDate(s.plannedDate) -> {
                _uiState.update { it.copy(saveError = "ไม่สามารถสร้างนัดหมายย้อนหลังได้") }
                false
            }
            // แก้แผนได้จนถึงก่อนเวลาเริ่มนัดเดิม — เช็คจากค่าดั้งเดิมตอนโหลดมา ไม่ใช่วันเวลาที่กำลัง
            // พิมพ์แก้อยู่ในฟอร์ม ไม่งั้นเลื่อนวันนัดไปอนาคตก่อนแล้วกดบันทึกจะหลุดกติกาได้
            s.activityId != null && editDecision(s) is AppointmentPolicy.Decision.Denied -> {
                val denied = editDecision(s) as AppointmentPolicy.Decision.Denied
                _uiState.update { it.copy(saveError = denied.message) }
                false
            }
            s.activityType == "onsite" && (s.lat == null || s.lng == null) -> {
                _uiState.update { it.copy(saveError = "กรุณาปักหมุดตำแหน่งนัดหมาย (จำเป็นสำหรับนัดแบบ On-site)") }
                false
            }
            else -> true
        }
    }

    private fun save() {
        if (!validate()) return
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

            val isEditMode = s.activityId != null
            val isoDate = s.plannedDate?.let { if (it.length >= 10) it.take(10) else parseToIsoDate(it) } ?: LocalDate.now().toString()

            val selectedContactNames = s.allContactOptions
                .filter { it.id in s.selectedContactIds }
                .joinToString(", ") { it.name }

            val activity = SalesActivity(
                activityId     = s.activityId ?: "",
                userId         = userId,
                customerId     = customerId ?: "CST-UNKNOWN",
                projectId      = s.selectedProjectId,
                activityType   = s.activityType,
                isAppointment  = true,
                detail         = s.titleTopic,
                activityDate   = isoDate,
                plannedTime    = s.startTime,
                plannedEndTime = s.endTime,
                plannedLat     = s.lat,
                plannedLong    = s.lng,
                status         = "planned",
                contactName    = selectedContactNames
            )

            val finalId: String
            if (isEditMode) {
                val appointmentId = s.activityId!!
                // ✅ ตอนสร้างใหม่ตั้ง isAppointment=true เสมอ (ไม่ขึ้นกับว่าเลือกผู้ติดต่อหรือยัง)
                // ตอนแก้ไขก็ต้องเหมือนกัน ไม่งั้นถอดผู้ติดต่อออกหมดแล้วบันทึก จะเผลอเปลี่ยนนัดหมายที่มีอยู่
                // ให้กลายเป็น "ไม่ใช่นัดหมาย" ทั้งที่ไม่ได้ตั้งใจ
                val updates = mutableMapOf<String, Any?>(
                    "type"           to s.activityType,
                    "planned_date"   to isoDate,
                    "topic"          to s.titleTopic,
                    "is_appointment" to true
                )
                s.startTime?.let { updates["planned_time"]     = formatTimeToDb(it) ?: it }
                s.endTime?.let   { updates["planned_end_time"] = formatTimeToDb(it) ?: it }
                s.lat?.let       { updates["planned_lat"]      = it }
                s.lng?.let       { updates["planned_long"]     = it }
                // ✅ ต้องใส่ key เสมอแม้ค่าเป็น null — เดิมใช้ ?.let จึงไม่ส่ง key เลยตอนผู้ใช้เลือก
                // "ไม่ระบุโครงการ" หรือล้างบริษัทออก ทั้ง Room และ server มองว่า "ไม่ได้แก้ฟิลด์นี้"
                // แล้วคงค่าเดิมไว้ กลับมาเปิดดูก็ยังผูกโครงการเดิมอยู่เหมือนไม่ได้กดอะไร
                // (ทั้ง ActivityRepository และ AppointmentRepositoryImpl เช็คด้วย containsKey
                // แล้วยอมรับ null เป็นการล้างค่าอยู่แล้ว)
                updates["project_code"] = s.selectedProjectId
                // บริษัทใช้ค่า sentinel เดียวกับทางสร้างใหม่ ไม่ใช่ null — ฝั่ง Room ตั้งใจไม่ยอมล้าง
                // cust_code เป็น null ถ้าส่ง null ไปจะได้ server ล้างแต่ในเครื่องไม่ล้าง กลายเป็นคนละค่า
                updates["cust_code"] = s.selectedCustomerId ?: "CST-UNKNOWN"
                // เดิมทิ้ง Result ทิ้งไปเฉย ๆ ต่างจากทางสร้างใหม่ที่อยู่ถัดลงไปซึ่งเช็ค isFailure
                // แก้ไขที่บันทึกไม่ลงจึงเด้งกลับหน้ารายการเหมือนสำเร็จ ทั้งที่ไม่มีอะไรเปลี่ยน
                val updateResult = activityRepo.updateActivity(appointmentId, updates)
                if (updateResult.isFailure) {
                    _uiState.update {
                        it.copy(isLoading = false, saveError = updateResult.exceptionOrNull()?.message ?: "บันทึกไม่สำเร็จ")
                    }
                    return@launch
                }
                finalId = appointmentId
            } else {
                val addResult = activityRepo.addActivity(activity)
                if (addResult.isFailure) {
                    _uiState.update {
                        it.copy(isLoading = false, saveError = addResult.exceptionOrNull()?.message ?: "บันทึกไม่สำเร็จ")
                    }
                    return@launch
                }
                finalId = addResult.getOrThrow()
            }

            // Save selected contacts
            activityRepo.saveAppointmentContacts(finalId, s.selectedContactIds.toList())

            // Checklist items
            val planItems = mutableListOf<ActivityPlanItem>()
            s.selectedMasterIds.forEach { mid ->
                planItems.add(
                    ActivityPlanItem(
                        appointmentId = finalId,
                        masterId      = mid,
                        isDone        = false,
                        actName       = s.allMasterOptions.find { it.masterId == mid }?.actName
                    )
                )
            }
            if (s.isOtherSelected && s.otherObjectiveText.isNotBlank()) {
                planItems.add(
                    ActivityPlanItem(
                        appointmentId = finalId,
                        masterId      = 999,
                        isDone        = false,
                        actName       = s.otherObjectiveText
                    )
                )
            }

            // ✅ ต้องเรียกเสมอแม้รายการว่าง — เดิมครอบด้วย isNotEmpty() ทำให้การ "ติ๊กออกจนหมด"
            // ไม่เคยถูกส่งไปไหน ทั้งในเครื่องและบน server ยังเหลือเช็คลิสต์ชุดเดิมอยู่
            // (savePlanItems ลบของเก่าก่อนเสมออยู่แล้ว ส่งลิสต์ว่างจึงหมายถึงล้างทิ้งพอดี)
            activityRepo.savePlanItems(finalId, planItems)

            // ⏰ ตั้งเวลาแจ้งเตือนล่วงหน้า 30 นาที
            try {
                val alarmScheduler = com.example.pp68_salestrackingapp.utils.AppointmentAlarmScheduler(context)
                // ✅ แก้ไขนัดหมาย (เวลา/หัวข้อ) ต้องยกเลิกของเดิมก่อนเสมอ — scheduleAlarm() เองจะ
                // ข้ามเงียบๆ ถ้าช่วงเวลาแจ้งเตือนใดกลายเป็นอดีตไปแล้วหลังแก้ (เช่น เลื่อนนัดเร็วขึ้น)
                // ถ้าไม่ยกเลิกก่อน alarm เก่าที่ตั้งไว้ล่วงหน้าตอนยังไม่แก้จะยังค้างแจ้งข้อมูลเดิมอยู่
                alarmScheduler.cancelAlarm(finalId)
                alarmScheduler.scheduleAlarm(
                    activityId = finalId,
                    companyName = s.selectedCompanyName ?: "สถานที่นัดหมาย",
                    topic = s.titleTopic,
                    plannedDateStr = isoDate,
                    plannedTimeStr = s.startTime ?: "09:00"
                )
            } catch (e: Exception) {
                android.util.Log.e("CreateApptVM", "ตั้ง Alarm ไม่สำเร็จ: ${e.message}")
            }

            // 📍 เริ่มติดตามตำแหน่งเพื่อแจ้งเตือนเมื่อเข้าใกล้สถานที่นัดหมาย (เฉพาะนัดหมายวันนี้)
            try {
                if (isoDate == java.time.LocalDate.now().toString()) {
                    com.example.pp68_salestrackingapp.service.ProximityMonitorService.startIfNeeded(context)
                }
            } catch (e: Exception) {
                android.util.Log.e("CreateApptVM", "เริ่มติดตามตำแหน่งไม่สำเร็จ: ${e.message}")
            }

            discardDraft()
            _uiState.update { it.copy(isLoading = false, isSaved = true) }
        }
    }

    // plannedDate ปกติเป็น ISO "yyyy-MM-dd" (DatePickerField ส่งมาแบบนี้) แต่รับ fallback รูปแบบ
    // "MMM dd, yyyy" ด้วยเหมือน parseToIsoDate — เผื่อ caller อื่นส่งมาคนละแบบ
    private fun isPastDate(plannedDate: String?): Boolean {
        if (plannedDate.isNullOrBlank()) return false
        val date = runCatching { LocalDate.parse(plannedDate.take(10)) }
            .getOrElse { runCatching { LocalDate.parse(parseToIsoDate(plannedDate)) }.getOrNull() }
            ?: return false
        return date.isBefore(LocalDate.now())
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
