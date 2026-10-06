package com.example.pp68_salestrackingapp.ui.viewmodels.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pp68_salestrackingapp.data.remote.ApiService
import com.example.pp68_salestrackingapp.data.repository.ActivityRepository
import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import com.example.pp68_salestrackingapp.data.model.AuthUser
import com.example.pp68_salestrackingapp.data.repository.CustomerRepository
import com.example.pp68_salestrackingapp.data.repository.ProjectRepository
import com.example.pp68_salestrackingapp.data.local.SyncConflictDao
import com.example.pp68_salestrackingapp.data.repository.syncAccountKey
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import com.example.pp68_salestrackingapp.utils.SyncRuntime
import com.example.pp68_salestrackingapp.utils.SyncStatus
import com.example.pp68_salestrackingapp.utils.SyncTrigger

data class ActivityCard(
    val activityId:    String,
    val activityType:  String,
    val projectName:   String?,
    val companyName:   String?,
    val contactName:   String?,
    val objective:     String?,
    val planStatus:    String,
    val plannedDate:   String?,
    val plannedTime:   String?,
    val plannedEndTime:String?,
    val weeklyNote:    String? = null,
    val customerId:    String? = null,
    val hasResult:     Boolean = false,
    val checkInTime:   String? = null,
    val isLocationVerified: Boolean? = null,
    val plannedLat:    Double? = null,
    val plannedLong:   Double? = null,
    val locationName:  String? = null
)

data class HomeUiState(
    val selectedMonth:   YearMonth              = YearMonth.now(),
    val groupedCards: Map<String, List<ActivityCard>> = emptyMap(),
    val isLoading:       Boolean                = false,
    val error:           String?                = null,
    val authUser:        AuthUser?              = null,
    // แสดงเป็นการ์ดที่กดไปหน้ากรอกได้ ไม่ใช่ dialog ที่ปิดไม่ได้แบบเดิม — ผู้ใช้ยังทำงานอย่างอื่น
    // ต่อได้ระหว่างที่ยังไม่กรอก และถ้าบันทึกพลาดก็ไม่ติดค้างจนใช้แอปไม่ได้
    val needsPhoneNumber: Boolean = false,

    // ส่วนที่ซิงค์ตอน login ไม่สำเร็จ (ว่าง = ครบดี) — เดิมล้มแล้วเงียบ ผู้ใช้เห็นหน้าจอว่าง
    // แล้วเข้าใจว่าข้อมูลหาย ทั้งที่แค่โหลดไม่สำเร็จรอบนั้นและกดใหม่ก็ได้
    val syncFailures: List<String> = emptyList(),
    val pendingSummary: List<Pair<String, Int>> = emptyList(),
    val rejectedCount: Int = 0,
    val conflictCount: Int = 0,
    val syncStatus: SyncStatus = SyncStatus.Idle
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val activityRepo: ActivityRepository,
    private val authRepo:     AuthRepository,
    private val customerRepo: CustomerRepository,
    private val projectRepo:  ProjectRepository,
    private val apiService:   ApiService,
    // ตัวดาวน์โหลดข้อมูลหลัง login (data.repository) — คนละตัวกับ outbox ใน utils ที่ชื่อซ้ำกัน
    private val downloadSync: com.example.pp68_salestrackingapp.data.repository.SyncManager,
    private val outboxSync: com.example.pp68_salestrackingapp.utils.SyncManager,
    private val syncConflictDao: SyncConflictDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState(authUser = authRepo.currentUser()))
    val uiState: StateFlow<HomeUiState> = _uiState
    private val _selectedMonth = MutableStateFlow(YearMonth.now())
    private val _activityReload = MutableStateFlow(0L)

    init {
        // แสดง Room ก่อนเสมอ งาน network ถูกเข้าคิวและไม่ขวางการเปิดหน้าหลัก
        observeActivities()
        if (authRepo.currentUser()?.userId != null) {
            outboxSync.scheduleSync(SyncTrigger.APP_FOREGROUND)
            outboxSync.scheduleDownload()
        }
        observeSyncFailures()
        observeSyncStatus()
    }

    private fun observeSyncStatus() {
        viewModelScope.launch {
            SyncRuntime.status.collect { status ->
                val pending = runCatching { outboxSync.pendingSummary() }.getOrDefault(emptyList())
                val rejected = runCatching { outboxSync.rejectedSummary().size }.getOrDefault(0)
                val conflicts = authRepo.currentUser()?.userId?.let { userId ->
                    runCatching { syncConflictDao.countForAccount(syncAccountKey(userId)) }.getOrDefault(0)
                } ?: 0
                _uiState.update {
                    it.copy(
                        syncStatus = status,
                        pendingSummary = pending,
                        rejectedCount = rejected,
                        conflictCount = conflicts
                    )
                }
            }
        }
    }

    private fun observeSyncFailures() {
        viewModelScope.launch {
            downloadSync.failedParts.collect { parts ->
                _uiState.update { it.copy(syncFailures = parts) }
            }
        }
    }

    /**
     * เช็คว่ายังไม่ได้กรอกเบอร์โทรหรือเปล่า — เบอร์ไม่ได้เก็บในเครื่อง ต้องถาม server
     *
     * แยกออกมาเพื่อให้หน้าแรกเรียกซ้ำได้ตอนกลับเข้าหน้า (ON_RESUME) ด้วย เดิมอยู่ใน refreshData()
     * อย่างเดียว ผู้ใช้กรอกเบอร์ในหน้าโปรไฟล์แล้วย้อนกลับมา การ์ดทวงเบอร์จึงยังค้างอยู่
     * จนกว่าจะลากรีเฟรชเอง
     *
     * เช็ค isSuccessful ก่อนเสมอ — .body() คืน null เวลา server ตอบ 4xx/5xx ด้วย
     * ถ้าไม่ดูจะกลายเป็น "ตอบไม่ได้ = ไม่มีเบอร์" แล้วการ์ดเด้งใส่คนที่กรอกไว้แล้ว
     */
    fun recheckPhoneNumber() {
        val userId = authRepo.currentUser()?.userId ?: return
        viewModelScope.launch {
            runCatching { apiService.getUserById("eq.$userId") }.onSuccess { response ->
                if (response.isSuccessful) {
                    val phone = response.body()?.firstOrNull()?.phoneNumber
                    _uiState.update { it.copy(needsPhoneNumber = phone.isNullOrBlank()) }
                }
            }
        }
    }

    /** กด "ลองใหม่" บนแถบเตือน — ซิงค์ใหม่ทั้งชุดเหมือนตอน login ไม่ใช่แค่ refresh หน้านี้ */
    fun retrySync() {
        if (authRepo.currentUser() == null) return
        outboxSync.scheduleSync(SyncTrigger.MANUAL)
        outboxSync.scheduleDownload()
    }

    /** ปิดแถบเตือนทิ้ง — ผู้ใช้รับรู้แล้วและเลือกทำงานต่อ */
    fun dismissSyncFailures() {
        downloadSync.clearFailedParts()
    }

    private fun observeActivities() {
        viewModelScope.launch {
            combine(
                _selectedMonth,
                _activityReload
            ) { month, _ -> month }
                .flatMapLatest { month ->
                    val userId = authRepo.currentUser()?.userId
                    if (userId.isNullOrBlank()) {
                        flowOf(Result.success(emptyList()))
                    } else {
                        activityRepo.getActivityCardsForMonthFlow(
                            userId = userId.removePrefix("eq."),
                            startDate = month.atDay(1).toString(),
                            endDateExclusive = month.plusMonths(1).atDay(1).toString()
                        )
                            .map<List<ActivityCard>, Result<List<ActivityCard>>> { Result.success(it) }
                            .catch { emit(Result.failure(it)) }
                    }
                }
                .collect { result ->
                    result.fold(
                        onSuccess = { cards ->
                            val grouped = cards.groupBy { card ->
                                card.plannedDate?.let { formatGroupHeader(it) } ?: "ไม่ระบุวันที่"
                            }
                            _uiState.update { it.copy(groupedCards = grouped) }
                        },
                        onFailure = { error ->
                            _uiState.update { it.copy(error = error.message) }
                        }
                    )
                }
        }
    }

    fun loadActivities() {
        _activityReload.update { it + 1 }
    }

    fun refreshData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val userId = authRepo.currentUser()?.userId ?: run {
                loadActivities()
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }
            
            try {
                // เบอร์โทรไม่ได้เก็บในเครื่อง ต้องถาม server — พลาดก็แค่ไม่ขึ้นการ์ด ไม่ทำให้ refresh พัง
                // ✅ ต้องเช็ค isSuccessful ก่อน: เดิมอ่าน .body() ตรง ๆ ซึ่งคืน null เวลา server ตอบ
                // 4xx/5xx ด้วย แล้วถูกตีความว่า "ผู้ใช้ไม่มีเบอร์โทร" การ์ดทวงเบอร์จึงเด้งขึ้นมา
                // หาคนที่กรอกเบอร์ไว้เรียบร้อยแล้วทุกครั้งที่เซิร์ฟเวอร์สะดุด — ตอบไม่ได้ ≠ ไม่มีเบอร์
                recheckPhoneNumber()

                // ✅ ทั้ง 4 อย่างเป็นอิสระต่อกัน (คนละตารางคนละ endpoint) รันพร้อมกันแทนรอทีละตัว
                coroutineScope {
                    awaitAll(
                        async { activityRepo.refreshActivities(userId) },
                        async { activityRepo.refreshResults(userId) },
                        async { customerRepo.refreshCustomers(authRepo.currentUser()?.teamId ?: "") },
                        async { projectRepo.refreshProjects(userId) }
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("HomeVM", "Error refreshing data: ${e.message}")
            }

            loadActivities()
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun selectMonth(month: YearMonth) {
        _uiState.update { it.copy(selectedMonth = month) }
        _selectedMonth.value = month
    }


    private fun formatGroupHeader(dateStr: String): String {
        return try {
            // ✅ take(10) เหมือนทุกที่ในแอป — ค่าที่มาจาก server อาจเป็น "2026-04-06T00:00:00"
            // ถ้า parse ทั้งก้อนจะพังแล้วตกไปใช้สตริงดิบเป็นหัวกลุ่ม ผู้ใช้เห็นหัวข้อว่า
            // "2026-04-06T00:00:00" และนัดของวันเดียวกันที่รูปแบบต่างกันจะถูกแยกเป็นสองกลุ่ม
            val date = LocalDate.parse(dateStr.take(10))
            val formatter = DateTimeFormatter.ofPattern("d MMM yyyy",
                java.util.Locale("th", "TH"))
            date.format(formatter).uppercase()
        } catch (e: Exception) { dateStr }
    }

    fun deleteActivity(activityId: String) {
        viewModelScope.launch {
            val result = activityRepo.deleteActivity(activityId)
            if (result.isFailure) {
                _uiState.update { it.copy(error = result.exceptionOrNull()?.message) }
            }
            loadActivities()
        }
    }
}
