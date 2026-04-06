package com.example.pp68_salestrackingapp.ui.viewmodels.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pp68_salestrackingapp.data.repository.ActivityRepository
import com.example.pp68_salestrackingapp.data.model.SalesActivity
import com.example.pp68_salestrackingapp.data.model.PlanItemDto
import com.example.pp68_salestrackingapp.data.model.MasterActDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlin.math.*

data class ActivityDetailUiState(
    val isLoading: Boolean = false,
    val activity: SalesActivity? = null,
    val planItems: List<PlanItemDto> = emptyList(),
    val selectedItemIds: Set<Int> = emptySet(),
    
    // Check-in
    val showCheckinDialog: Boolean = false,
    val isLocationMismatch: Boolean = false,
    val currentDistance: Double = 0.0, // in meters
    val isCheckingIn: Boolean = false,
    
    // Finish
    val isFinishing: Boolean = false,
    val isFinished:  Boolean = false,
    val isCompleted: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ActivityDetailViewModel @Inject constructor(
    private val repo: ActivityRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ActivityDetailUiState())
    val uiState: StateFlow<ActivityDetailUiState> = _uiState

    fun loadActivity(id: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val actResult  = repo.getActivityById(id)
            val itemsResult = repo.getPlanItems(id)

            val rawActivity = actResult.getOrNull()?.firstOrNull()

            // ✅ Join ข้อมูล customer, project, contact จาก local DB
            val enrichedActivity = if (rawActivity != null) {
                repo.enrichActivity(rawActivity)
            } else null

            // ✅ จัดรูปแบบวันที่และเวลาให้สวยงามก่อนแสดงผล
            val finalActivity = enrichedActivity?.let { act ->
                act.copy(
                    activityDate = formatDateForUI(act.activityDate) ?: act.activityDate,
                    plannedTime = formatTimeForUI(act.plannedTime),
                    plannedEndTime = formatTimeForUI(act.plannedEndTime)
                )
            }

            _uiState.update {
                it.copy(
                    isLoading  = false,
                    activity   = finalActivity ?: enrichedActivity ?: rawActivity,
                    planItems  = itemsResult.getOrDefault(emptyList()),
                    selectedItemIds = itemsResult.getOrDefault(emptyList())
                        .filter { item -> item.isDone }
                        .map { item -> item.masterId }
                        .toSet()
                )
            }

            if (actResult.isFailure) {
                _uiState.update {
                    it.copy(error = "ไม่สามารถโหลดข้อมูลได้: ${actResult.exceptionOrNull()?.message}")
                }
            }
        }
    }

    private fun formatDateForUI(isoDate: String?): String? {
        if (isoDate == null) return null
        return try {
            val date = LocalDate.parse(isoDate.take(10))
            date.format(DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.ENGLISH))
        } catch (e: Exception) { isoDate }
    }

    private fun formatTimeForUI(timeStr: String?): String? {
        if (timeStr.isNullOrBlank()) return null
        return try {
            val cleanTime = timeStr.trim()
            // รองรับทั้ง HH:mm:ss และ hh:mm a
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

    fun updateCurrentLocation(lat: Double, lng: Double) {
        val act = _uiState.value.activity ?: return
        val plannedLat = act.plannedLat ?: return
        val plannedLng = act.plannedLong ?: return

        val distance = calculateDistance(lat, lng, plannedLat, plannedLng)
        _uiState.update { 
            it.copy(
                currentDistance = distance,
                isLocationMismatch = distance > 200 // 200 meters threshold
            )
        }
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371e3 // Earth radius in meters
        val phi1 = lat1 * PI / 180
        val phi2 = lat2 * PI / 180
        val deltaPhi = (lat2 - lat1) * PI / 180
        val deltaLambda = (lon2 - lon1) * PI / 180

        val a = sin(deltaPhi / 2).pow(2) +
                cos(phi1) * cos(phi2) *
                sin(deltaLambda / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return r * c
    }

    fun confirmCheckin(lat: Double, lng: Double) {
        val act = _uiState.value.activity ?: return
        val isVerified = !(_uiState.value.isLocationMismatch)

        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingIn = true, error = null) }
            repo.checkIn(act.activityId, lat, lng, isVerified).fold(
                onSuccess = {
                    _uiState.update { it.copy(isCheckingIn = false, showCheckinDialog = false) }
                    loadActivity(act.activityId)
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isCheckingIn = false, error = "เช็คอินไม่สำเร็จ: ${e.message}") }
                }
            )
        }
    }

    fun toggleItem(masterId: Int) {
        val current = _uiState.value.selectedItemIds.toMutableSet()
        if (current.contains(masterId)) current.remove(masterId)
        else current.add(masterId)
        _uiState.update { it.copy(selectedItemIds = current) }

        val activityId = _uiState.value.activity?.activityId ?: return
        val isDone = current.contains(masterId)

        viewModelScope.launch {
            repo.updatePlanItemStatus(activityId, masterId, isDone)
            repo.updateChecklistItem(activityId, masterId, isDone)
        }
    }

    fun finishActivity() {
        val activityId = _uiState.value.activity?.activityId ?: return
        val doneIds    = _uiState.value.selectedItemIds.toList()

        viewModelScope.launch {
            _uiState.update { it.copy(isFinishing = true, error = null) }

            repo.finishActivity(
                activityId    = activityId,
                doneMasterIds = doneIds,
                note          = null
            ).onSuccess {
                _uiState.update { it.copy(isFinishing = false, isFinished = true) }
            }.onFailure { e ->
                _uiState.update { it.copy(isFinishing = false, error = "Finish ไม่สำเร็จ: ${e.message}") }
            }
        }
    }

    fun setShowCheckinDialog(show: Boolean) {
        _uiState.update { it.copy(showCheckinDialog = show) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
