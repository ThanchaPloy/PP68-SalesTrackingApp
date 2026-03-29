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
            val actResult = repo.getActivityById(id)
            val itemsResult = repo.getPlanItems(id)
            
            _uiState.update { 
                it.copy(
                    isLoading = false,
                    activity = actResult.getOrNull()?.firstOrNull(),
                    planItems = itemsResult.getOrDefault(emptyList()),
                    selectedItemIds = itemsResult.getOrDefault(emptyList())
                        .filter { item -> item.isDone }
                        .map { item -> item.masterId }
                        .toSet()
                )
            }
            if (actResult.isFailure) {
                _uiState.update { it.copy(error = "ไม่สามารถโหลดข้อมูลได้: ${actResult.exceptionOrNull()?.message}") }
            }
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
