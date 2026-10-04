package com.example.pp68_salestrackingapp.ui.viewmodels.activity

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.example.pp68_salestrackingapp.data.repository.ActivityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class ResultVersionItem(
    val resultId: String,
    val version: Int,
    val isLatest: Boolean,
    val reportDate: String?,
    val newStatus: String?,
    val summary: String?
)

@HiltViewModel
class ResultHistoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    activityRepo: ActivityRepository
) : ViewModel() {

    private val resultGroupId: String = savedStateHandle.get<String>("resultGroupId").orEmpty()

    val versions: Flow<PagingData<ResultVersionItem>> = activityRepo
        .getResultVersionHistoryPaging(resultGroupId)
        .map { pagingData ->
            pagingData.map {
                ResultVersionItem(
                    resultId = it.resultId,
                    version = it.version,
                    isLatest = it.isLatest,
                    reportDate = it.reportDate,
                    newStatus = it.newStatus,
                    summary = it.summary
                )
            }
        }
        .cachedIn(viewModelScope)
}
