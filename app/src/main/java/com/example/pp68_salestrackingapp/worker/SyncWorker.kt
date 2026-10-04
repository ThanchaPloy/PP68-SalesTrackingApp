package com.example.pp68_salestrackingapp.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.pp68_salestrackingapp.utils.SyncManager
import com.example.pp68_salestrackingapp.utils.SyncDiagnostics
import com.example.pp68_salestrackingapp.utils.SyncRuntime
import com.example.pp68_salestrackingapp.utils.SyncTrigger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

internal enum class SyncWorkDecision { SUCCESS, RETRY, FAILURE }

internal fun decideSyncWork(result: com.example.pp68_salestrackingapp.utils.SyncRunResult): SyncWorkDecision = when {
    result.hasLocalFatalFailure || result.hasAuthenticationFailure -> SyncWorkDecision.FAILURE
    result.shouldRetry -> SyncWorkDecision.RETRY
    else -> SyncWorkDecision.SUCCESS
}

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncManager: SyncManager,
    private val diagnostics: SyncDiagnostics
) : CoroutineWorker(context, params) {

    companion object { const val KEY_TRIGGER = "trigger" }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val trigger = runCatching {
            SyncTrigger.valueOf(inputData.getString(KEY_TRIGGER) ?: SyncTrigger.PERIODIC.name)
        }.getOrDefault(SyncTrigger.PERIODIC)
        Log.d("SyncWorker", "Starting background sync ($trigger)...")
        SyncRuntime.running(id.toString())
        try {
            val result = syncManager.doSync()
            SyncRuntime.completed(result)
            diagnostics.record("sync_finished", mapOf(
                "run_id" to result.runId,
                "trigger" to trigger.name,
                "attempted" to result.attempted,
                "succeeded" to result.succeeded,
                "temporary_failures" to result.temporaryFailures,
                "permanent_failures" to result.permanentFailures,
                "rejected_pending" to result.rejectedPending,
                "still_pending" to result.stillPending,
                "failure_types" to result.failureTypes.entries.joinToString(",") { "${it.key}:${it.value}" },
                "worker_attempt" to runAttemptCount
            ))
            if (!result.shouldRetry && result.rejectedPending == 0 && !result.hasAuthenticationFailure) {
                diagnostics.markSuccessfulSync(result.finishedAt)
            }
            when (decideSyncWork(result)) {
                SyncWorkDecision.FAILURE -> Result.failure(
                    androidx.work.workDataOf("error_type" to "LOCAL_FATAL")
                )
                SyncWorkDecision.RETRY -> Result.retry()
                SyncWorkDecision.SUCCESS -> Result.success()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("SyncWorker", "Sync error (${e::class.java.simpleName})")
            SyncRuntime.failed(e)
            diagnostics.record("sync_failed", mapOf(
                "trigger" to trigger.name,
                "worker_attempt" to runAttemptCount,
                "error_type" to e::class.java.simpleName
            ))
            // Network/HTTP รายแถวถูก aggregate เป็น stillPending ด้านบนแล้ว; exception ที่หลุดมาถึงนี่
            // คือ invariant/local failure ที่ retry แบบไม่จำกัดมีแต่จะวนซ้ำและซ่อนสาเหตุ
            Result.failure(androidx.work.workDataOf("error_type" to e::class.java.simpleName))
        }
    }
}
