package com.example.pp68_salestrackingapp.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import com.example.pp68_salestrackingapp.data.repository.SyncManager
import com.example.pp68_salestrackingapp.utils.SyncDiagnostics
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
    private val diagnostics: SyncDiagnostics
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val user = authRepository.currentUser() ?: return@withContext Result.success()
        try {
            val outcome = downloadSync.syncAll(user.userId, user.teamId.orEmpty())
            val failed = outcome.failedParts
            diagnostics.record(
                if (failed.isEmpty()) "download_finished" else "download_partial",
                mapOf(
                    "temporary_failures" to failed.count { it.failureType != com.example.pp68_salestrackingapp.utils.SyncFailureType.LOCAL_FATAL },
                    "failure_types" to failed.groupingBy { it.failureType }.eachCount().entries.joinToString(",") { "${it.key}:${it.value}" },
                    "worker_attempt" to runAttemptCount
                )
            )
            when {
                outcome.hasLocalFatalFailure -> Result.failure(
                    androidx.work.workDataOf("error_type" to "LOCAL_FATAL")
                )
                outcome.shouldRetry -> Result.retry()
                else -> Result.success()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            diagnostics.record("download_failed", mapOf(
                "worker_attempt" to runAttemptCount,
                "error_type" to e::class.java.simpleName
            ))
            Result.retry()
        }
    }
}
