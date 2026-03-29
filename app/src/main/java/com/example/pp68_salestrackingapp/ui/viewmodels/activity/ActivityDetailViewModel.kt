package com.example.pp68_salestrackingapp.ui.viewmodels.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pp68_salestrackingapp.data.repository.ActivityRepository
import com.example.pp68_salestrackingapp.data.model.SalesActivity
import com.example.pp68_salestrackingapp.data.model.PlanItemDto
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
    val currentDistance: Double = 0.0,
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
            // Mocking plan items for now as repo might not have it yet
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

    fun confirmCheckin(lat: Double, lng: Double) {
        val act = _uiState.value.activity ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingIn = true, error = null) }
            repo.checkIn(act.activityId, lat, lng).fold(
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

        // ✅ บันทึกสถานะลง DB และ API ทันทีที่ tick
        val activityId = _uiState.value.activity?.activityId ?: return
        val isDone = current.contains(masterId)

        viewModelScope.launch {
            // อัปเดต local DB
            repo.updatePlanItemStatus(activityId, masterId, isDone)

            // ✅ อัปเดต activity_checklist ใน API ด้วย
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
                // ✅ set isFinished = true เพื่อ trigger navigate กลับ
                _uiState.update { it.copy(isFinishing = false, isFinished = true) }
            }.onFailure { e ->
                _uiState.update { it.copy(isFinishing = false, error = "Finish ไม่สำเร็จ: ${e.message}") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }


}
