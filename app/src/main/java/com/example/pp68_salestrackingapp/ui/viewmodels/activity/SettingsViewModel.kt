package com.example.pp68_salestrackingapp.ui.viewmodels.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pp68_salestrackingapp.data.model.AuthUser
import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import com.example.pp68_salestrackingapp.data.local.SyncConflictDao
import com.example.pp68_salestrackingapp.data.model.SyncConflict
import com.example.pp68_salestrackingapp.data.repository.syncAccountKey
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.example.pp68_salestrackingapp.utils.SyncDiagnostics
import com.example.pp68_salestrackingapp.utils.SyncRuntime
import com.example.pp68_salestrackingapp.utils.SyncStatus
import com.example.pp68_salestrackingapp.utils.SyncTrigger
import java.io.File

data class SettingsUiState(
    val user: AuthUser? = null,
    val isLoggingOut: Boolean = false,
    val isLoggedOut: Boolean = false,
    val logoutError: String? = null,
    // ต่างจาก logoutError: ออกจากระบบได้ แต่จะเสียข้อมูลที่เซิร์ฟเวอร์ไม่รับ ต้องให้ยืนยันก่อน
    val logoutWarning: String? = null,
    val pendingSummary: List<Pair<String, Int>> = emptyList(),
    val rejected: List<com.example.pp68_salestrackingapp.data.model.SyncRejection> = emptyList(),
    val conflicts: List<SyncConflict> = emptyList(),
    val syncStatus: SyncStatus = SyncStatus.Idle,
    val lastSuccessfulSync: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepo: AuthRepository,
    private val syncManager: com.example.pp68_salestrackingapp.utils.SyncManager,
    private val diagnostics: SyncDiagnostics,
    private val syncConflictDao: SyncConflictDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadUser()
        refreshSyncStatus()
        viewModelScope.launch {
            SyncRuntime.status.collect { status ->
                _uiState.update { it.copy(syncStatus = status) }
                refreshSyncStatus()
            }
        }
    }

    private fun loadUser() {
        val currentUser = authRepo.currentUser()
        _uiState.update { it.copy(user = currentUser) }
    }

    fun refreshUser() {
        loadUser()
    }

    fun refreshSyncStatus() {
        viewModelScope.launch {
            val pending = runCatching { syncManager.pendingSummary() }.getOrDefault(emptyList())
            val rejected = runCatching { syncManager.rejectedSummary() }.getOrDefault(emptyList())
            val conflicts = authRepo.currentUser()?.userId?.let { userId ->
                runCatching { syncConflictDao.getForAccount(syncAccountKey(userId)) }
                    .getOrDefault(emptyList())
            }.orEmpty()
            _uiState.update {
                it.copy(
                    pendingSummary = pending,
                    rejected = rejected,
                    conflicts = conflicts,
                    lastSuccessfulSync = diagnostics.lastSuccessfulSync()
                )
            }
        }
    }

    fun retrySync() = syncManager.scheduleSync(SyncTrigger.MANUAL)

    fun exportDiagnostics(): File = diagnostics.exportFile()

    fun logout(force: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoggingOut = true, logoutError = null, logoutWarning = null) }
            authRepo.logout(force).fold(
                onSuccess = { _uiState.update { it.copy(isLoggingOut = false, isLoggedOut = true) } },
                onFailure = { e ->
                    _uiState.update {
                        if (e is AuthRepository.PendingRejectionException) {
                            it.copy(isLoggingOut = false, logoutWarning = e.message)
                        } else {
                            it.copy(isLoggingOut = false, logoutError = e.message)
                        }
                    }
                }
            )
        }
    }

    fun dismissLogoutError() {
        _uiState.update { it.copy(logoutError = null, logoutWarning = null) }
    }
}
