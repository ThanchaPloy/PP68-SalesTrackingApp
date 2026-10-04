package com.example.pp68_salestrackingapp.data.repository

import android.util.Log
import com.example.pp68_salestrackingapp.utils.DealFactors
import com.example.pp68_salestrackingapp.utils.LossReasons
import com.example.pp68_salestrackingapp.utils.ProjectStages
import com.example.pp68_salestrackingapp.utils.SyncFailureType
import com.example.pp68_salestrackingapp.utils.classifySyncFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.supervisorScope
import javax.inject.Inject
import javax.inject.Singleton

data class DownloadPartResult(
    val label: String,
    val failureType: SyncFailureType? = null,
    val errorType: String? = null
) {
    val isSuccess: Boolean get() = failureType == null
}

data class DownloadSyncResult(val parts: List<DownloadPartResult>) {
    val failedParts: List<DownloadPartResult> get() = parts.filterNot { it.isSuccess }
    val hasLocalFatalFailure: Boolean
        get() = failedParts.any { it.failureType == SyncFailureType.LOCAL_FATAL }
    val shouldRetry: Boolean
        get() = failedParts.any {
            it.failureType in setOf(
                SyncFailureType.NETWORK, SyncFailureType.TIMEOUT,
                SyncFailureType.RATE_LIMITED, SyncFailureType.SERVER,
                SyncFailureType.UNKNOWN
            )
        }
}

@Singleton
class SyncManager @Inject constructor(
    private val customerRepo: CustomerRepository,
    private val contactRepo: ContactRepository,
    private val projectRepo: ProjectRepository,
    private val activityRepo: ActivityRepository,
    private val masterDataRepo: MasterDataRepository
) {
    private val _failedParts = MutableStateFlow<List<String>>(emptyList())
    val failedParts: StateFlow<List<String>> = _failedParts.asStateFlow()

    fun clearFailedParts() { _failedParts.value = emptyList() }

    suspend fun syncAll(userId: String, branchId: String): DownloadSyncResult = supervisorScope {
        suspend fun step(label: String, block: suspend () -> Result<Unit>): DownloadPartResult {
            return try {
                block().fold(
                    onSuccess = { DownloadPartResult(label) },
                    onFailure = { error ->
                        val type = classifySyncFailure(error).let {
                            if (it == SyncFailureType.UNKNOWN) SyncFailureType.LOCAL_FATAL else it
                        }
                        Log.e("DownloadSync", "$label failed: ${type.name}")
                        DownloadPartResult(label, type, error::class.java.simpleName)
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val type = classifySyncFailure(e).let {
                    if (it == SyncFailureType.UNKNOWN) SyncFailureType.LOCAL_FATAL else it
                }
                Log.e("DownloadSync", "$label failed: ${type.name}")
                DownloadPartResult(label, type, e::class.java.simpleName)
            }
        }

        val project = async { step("โครงการ") { projectRepo.refreshProjects(userId) } }
        val customersAndContacts = async {
            val results = mutableListOf<DownloadPartResult>()
            val customerResult = if (branchId.isNotEmpty()) {
                step("ลูกค้า") { customerRepo.refreshCustomers(branchId) }
            } else DownloadPartResult("ลูกค้า")
            results += customerResult
            // ใช้ customer IDs เดิมใน Room ได้แม้ refresh ลูกค้ารอบนี้ล้มเหลว
            results += step("ผู้ติดต่อ") { contactRepo.refreshContacts() }
            results
        }
        val activities = async { step("นัดหมาย") { activityRepo.refreshActivities(userId) } }
        val results = async { step("บันทึกผล") { activityRepo.refreshResults(userId) } }

        val requiredParts = buildList {
            add(project.await())
            addAll(customersAndContacts.await())
            add(activities.await())
            add(results.await())
        }

        // Master data มี fallback ใน APK จึงไม่ทำให้ initial download ทั้งก้อนล้มเหลว
        runCatching {
            ProjectStages.applyServerData(masterDataRepo.getProjectStages())
            LossReasons.applyServerData(masterDataRepo.getLossReasons())
            DealFactors.applyServerData(masterDataRepo.getDealFactorQuestions())
        }.onFailure { error ->
            if (error is CancellationException) throw error
            Log.w("DownloadSync", "optional_master_data_failed type=${error::class.java.simpleName}")
        }

        val outcome = DownloadSyncResult(requiredParts)
        _failedParts.value = outcome.failedParts.map { it.label }
        outcome
    }
}
