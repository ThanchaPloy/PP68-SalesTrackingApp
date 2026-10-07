package com.example.pp68_salestrackingapp.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import com.example.pp68_salestrackingapp.data.repository.SyncManager
import com.example.pp68_salestrackingapp.data.repository.DeltaSyncRepository
import com.example.pp68_salestrackingapp.data.repository.DeltaSyncCursorExpiredException
import com.example.pp68_salestrackingapp.data.repository.DeltaSyncProtocolException
import com.example.pp68_salestrackingapp.data.model.SyncState
import com.example.pp68_salestrackingapp.utils.SyncDiagnostics
import com.example.pp68_salestrackingapp.utils.jsonPathOf
import com.example.pp68_salestrackingapp.utils.SyncFailureType
import com.example.pp68_salestrackingapp.utils.classifySyncFailure
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class DownloadSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val authRepository: AuthRepository,
    private val downloadSync: SyncManager,
    private val deltaSync: DeltaSyncRepository,
    private val diagnostics: SyncDiagnostics
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val user = authRepository.currentUser() ?: return@withContext Result.success()
        try {
            val state = deltaSync.getState(user.userId)
            val bootstrapRequired = state?.bootstrapStatus != SyncState.STATUS_READY
            if (bootstrapRequired) {
                deltaSync.bootstrapSnapshot(user.userId).getOrThrow()
            }
            val deltaPages = deltaSync.syncUntilCaughtUp(user.userId).getOrThrow()
            downloadSync.refreshOptionalMasterData()
            downloadSync.clearFailedParts()
            diagnostics.record(
                "download_finished",
                mapOf(
                    "bootstrap" to bootstrapRequired,
                    "delta_pages" to deltaPages,
                    "worker_attempt" to runAttemptCount
                )
            )
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val failureType = classifySyncFailure(e)
            downloadSync.reportFailedParts(listOf("ดาวน์โหลดข้อมูล"))
            diagnostics.record("download_failed", buildMap {
                put("worker_attempt", runAttemptCount)
                put("error_type", e::class.java.simpleName)
                put("failure_type", failureType.name)
                jsonPathOf(e)?.let { put("json_path", it) }
            })
            when {
                e is DeltaSyncCursorExpiredException -> Result.retry()
                e is DeltaSyncProtocolException -> Result.failure(
                    androidx.work.workDataOf("error_type" to e::class.java.simpleName)
                )
                failureType in setOf(
                    SyncFailureType.NETWORK,
                    SyncFailureType.TIMEOUT,
                    SyncFailureType.RATE_LIMITED,
                    SyncFailureType.SERVER,
                    SyncFailureType.UNKNOWN
                ) -> Result.retry()
                else -> Result.failure(
                    androidx.work.workDataOf("error_type" to e::class.java.simpleName)
                )
            }
        }
    }
}
