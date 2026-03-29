package com.example.pp68_salestrackingapp.ui.viewmodels.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pp68_salestrackingapp.data.repository.ActivityRepository
import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import com.example.pp68_salestrackingapp.data.model.AuthUser
import com.example.pp68_salestrackingapp.data.repository.CallLogRepository
import com.example.pp68_salestrackingapp.data.repository.CustomerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import javax.inject.Inject

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
    val customerId: String? = null
)

data class HomeUiState(
    val selectedMonth:   YearMonth              = YearMonth.now(),
    val groupedCards: Map<String, List<ActivityCard>> = emptyMap(),
    val isLoading:       Boolean                = false,
    val error:           String?                = null,
    val authUser:        AuthUser?              = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val activityRepo: ActivityRepository,
    private val authRepo:     AuthRepository,
    private val customerRepo: CustomerRepository,
    private val callLogRepo: CallLogRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState(authUser = authRepo.currentUser()))
    val uiState: StateFlow<HomeUiState> = _uiState

    init {
        // Kotlin prohibited 'return' in init block
        if (authRepo.currentUser()?.userId != null) {
            refreshData()
            syncCallLogs()  // ✅ sync call log ตอน init
        } else {
            loadActivities() // โหลด local แม้ไม่ได้ login (ถ้ามีข้อมูล)
        }
    }

    private fun syncCallLogs() {
        viewModelScope.launch {
            try {
                // ดึง contact phone map จาก local DB
                val contacts = customerRepo.getAllContactPhoneMap()
                callLogRepo.syncCallLogs(contacts)
                android.util.Log.d("CallLog", "Sync call logs สำเร็จ")
            } catch (e: Exception) {
                android.util.Log.w("CallLog", "Sync call logs ไม่สำเร็จ: ${e.message}")
            }
        }
    }

    private fun observeActivities() {
        viewModelScope.launch {
            // ผูก Flow จาก Local DB ให้หน้าจอ Update อัตโนมัติเมื่อข้อมูลเปลี่ยน
            activityRepo.getAllActivitiesFlow().collect { _ ->
                loadActivities()
            }
        }
    }

    fun loadActivities() {
        viewModelScope.launch {
            val currentMonth = _uiState.value.selectedMonth

            activityRepo.getMyActivitiesWithDetails().fold(
                onSuccess = { cards ->
                    val filteredCards = cards.filter { card ->
                        try {
                            if (card.plannedDate.isNullOrBlank()) false
                            else {
                                val date = LocalDate.parse(card.plannedDate)
                                YearMonth.from(date) == currentMonth
                            }
                        } catch (e: Exception) {
                            false
                        }
                    }

                    val grouped = filteredCards
                        .sortedByDescending { it.plannedDate }
                        .groupBy { card ->
                            card.plannedDate?.let { formatGroupHeader(it) } ?: "ไม่ระบุวันที่"
                        }

                    _uiState.update {
                        it.copy(groupedCards = grouped)
                    }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(error = e.message) }
                }
            )
        }
    }

    fun refreshData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val userId = authRepo.currentUser()?.userId ?: run {
                // ✅ ถ้าไม่มี userId ก็ยังโหลด local ได้
                loadActivities()
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }
            // refresh จาก API (ถ้า fail ก็ไม่เป็นไร local ยังอยู่)
            activityRepo.refreshActivities(userId)
            // โหลดจาก local เสมอ
            loadActivities()
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun selectMonth(month: YearMonth) {
        _uiState.update { it.copy(selectedMonth = month) }
        loadActivities()
    }

    fun logout() {
        viewModelScope.launch {
            authRepo.logout()
        }
    }

    private fun formatGroupHeader(dateStr: String): String {
        return try {
            val date = LocalDate.parse(dateStr)
            val formatter = DateTimeFormatter.ofPattern("d MMM yyyy",
                java.util.Locale("th", "TH"))
            date.format(formatter).uppercase()
        } catch (e: Exception) { dateStr }
    }

    fun deleteActivity(activityId: String) {
        viewModelScope.launch {
            activityRepo.deleteActivity(activityId)
            loadActivities()
        }
    }
}
