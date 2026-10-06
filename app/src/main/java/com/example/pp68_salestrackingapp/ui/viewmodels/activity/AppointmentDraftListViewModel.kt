package com.example.pp68_salestrackingapp.ui.viewmodels.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pp68_salestrackingapp.data.model.AppointmentDraft
import com.example.pp68_salestrackingapp.data.repository.AppointmentDraftRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppointmentDraftListUiState(
    val drafts: List<AppointmentDraft> = emptyList(),
    val isLoading: Boolean = true,
    val max: Int = AppointmentDraftRepository.MAX_DRAFTS_PER_ACCOUNT
) {
    val isFull: Boolean get() = drafts.size >= max
}

@HiltViewModel
class AppointmentDraftListViewModel @Inject constructor(
    private val repository: AppointmentDraftRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppointmentDraftListUiState())
    val uiState: StateFlow<AppointmentDraftListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // เก็บกวาดตอนเปิดหน้า ไม่ต้องมี worker แยกสำหรับงานที่เบาขนาดนี้ (แผนงาน C.2)
            // และย้ายร่างยุค SharedPreferences ให้ครั้งเดียวที่นี่ ซึ่งเป็นที่แรกที่ผู้ใช้จะมองหามัน
            repository.migrateLegacyDrafts()
            repository.deleteExpired()
            repository.observeDrafts().collect { drafts ->
                _uiState.update { it.copy(drafts = drafts, isLoading = false) }
            }
        }
    }

    fun delete(draftId: String) {
        viewModelScope.launch { repository.deleteDraft(draftId) }
    }
}
