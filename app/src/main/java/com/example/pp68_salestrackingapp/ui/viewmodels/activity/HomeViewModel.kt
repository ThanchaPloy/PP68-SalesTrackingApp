package com.example.pp68_salestrackingapp.ui.viewmodels.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pp68_salestrackingapp.data.repository.ActivityRepository
import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import com.example.pp68_salestrackingapp.data.model.AuthUser
import com.example.pp68_salestrackingapp.data.repository.CallLogRepository
import com.example.pp68_salestrackingapp.data.repository.ContactRepository
import com.example.pp68_salestrackingapp.data.repository.CustomerRepository
import com.example.pp68_salestrackingapp.data.repository.ProjectRepository
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
    val customerId:    String? = null,
    val hasResult:     Boolean = false
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
    private val projectRepo:  ProjectRepository,
    private val contactRepo:  ContactRepository,
    private val callLogRepo:  CallLogRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState(authUser = authRepo.currentUser()))
    val uiState: StateFlow<HomeUiState> = _uiState

    init {
        if (authRepo.currentUser()?.userId != null) {
            refreshData()
            syncCallLogs()
        } else {
            loadActivities()
        }
        observeActivities()
    }

    private fun syncCallLogs() {
        viewModelScope.launch {
            try {
                val contacts = customerRepo.getAllContactPhoneMap()
                callLogRepo.syncCallLogs(contacts)
            } catch (e: Exception) {
                android.util.Log.w("CallLog", "Sync call logs ไม่สำเร็จ: ${e.message}")
            }
        }
    }

    private fun observeActivities() {
        viewModelScope.launch {
            // Observe multiple flows to update UI when any relevant data changes
            combine(
                activityRepo.getAllActivitiesFlow(),
                customerRepo.getAllCustomersFlow(),
                projectRepo.getAllProjectsFlow(),
                activityRepo.getAllResultIdsFlow()
            ) { _, _, _, _ -> 
                loadActivities()
            }.collect()
        }
    }

    fun loadActivities() {
        viewModelScope.launch {
            val currentMonth = _uiState.value.selectedMonth

            val resultIds = activityRepo.getAllResultIdsFlow().first().toSet()

            activityRepo.getMyActivitiesWithDetails().fold(
                onSuccess = { cards ->
                    val filteredCards = cards.map { card ->
                        card.copy(hasResult = card.activityId in resultIds)
                    }.filter { card ->
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

                    // Sort by plannedDate ascending, then by plannedTime ascending
                    val grouped = filteredCards
                        .sortedWith(compareBy({ it.plannedDate }, { it.plannedTime }))
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
                loadActivities()
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }
            
            // Refresh all relevant data from remote
            try {
                activityRepo.refreshActivities(userId)
                val branchId = authRepo.currentUser()?.teamId ?: return@launch
                customerRepo.refreshCustomers(branchId)
                projectRepo.refreshProjects(userId)
                // ✅ เพิ่มการ Sync ผู้ติดต่อตั้งแต่วันเริ่มต้นแอป
                contactRepo.refreshContacts(userId)
            } catch (e: Exception) {
                android.util.Log.e("HomeVM", "Error refreshing data: ${e.message}")
            }

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
