package com.example.pp68_salestrackingapp.ui.viewmodels.activity

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pp68_salestrackingapp.data.repository.ActivityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ResultVersionItem(
    val resultId: String,
    val version: Int,
    val isLatest: Boolean,
    val reportDate: String?,
    val newStatus: String?,
    val summary: String?
)

data class ResultHistoryUiState(
    val versions: List<ResultVersionItem> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class ResultHistoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    activityRepo: ActivityRepository
) : ViewModel() {

    private val resultGroupId: String = savedStateHandle.get<String>("resultGroupId") ?: ""

    val uiState: StateFlow<ResultHistoryUiState> = activityRepo
        .getResultVersionHistory(resultGroupId)
        .map { list ->
            ResultHistoryUiState(
                versions = list.map {
                    ResultVersionItem(
                        resultId   = it.resultId,
                        version    = it.version,
                        isLatest   = it.isLatest,
                        reportDate = it.reportDate,
                        newStatus  = it.newStatus,
                        summary    = it.summary
                    )
                },
                isLoading = false
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ResultHistoryUiState())
}
