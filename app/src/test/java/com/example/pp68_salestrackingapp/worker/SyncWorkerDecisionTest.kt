package com.example.pp68_salestrackingapp.worker

import com.example.pp68_salestrackingapp.utils.SyncFailureType
import com.example.pp68_salestrackingapp.utils.SyncRunResult
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncWorkerDecisionTest {
    private fun result(
        temporaryFailures: Int = 0,
        rejectedPending: Int = 0,
        skipped: Int = 0,
        stillPending: Int = 0,
        failures: Map<SyncFailureType, Int> = emptyMap()
    ) = SyncRunResult(
        runId = "run-1",
        attempted = 1,
        succeeded = 0,
        temporaryFailures = temporaryFailures,
        permanentFailures = failures[SyncFailureType.PERMANENT] ?: 0,
        rejectedPending = rejectedPending,
        skipped = skipped,
        stillPending = stillPending,
        failureTypes = failures,
        startedAt = "2026-10-04T00:00:00Z",
        finishedAt = "2026-10-04T00:00:01Z"
    )

    @Test fun `network failure retries`() {
        assertEquals(
            SyncWorkDecision.RETRY,
            decideSyncWork(result(temporaryFailures = 1, stillPending = 1, failures = mapOf(SyncFailureType.NETWORK to 1)))
        )
    }

    @Test fun `batch limit retries remaining rows`() {
        assertEquals(SyncWorkDecision.RETRY, decideSyncWork(result(skipped = 1, stillPending = 1)))
    }

    @Test fun `authentication failure stops automatic retry`() {
        assertEquals(
            SyncWorkDecision.FAILURE,
            decideSyncWork(result(stillPending = 1, failures = mapOf(SyncFailureType.AUTHENTICATION to 1)))
        )
    }

    @Test fun `local fatal failure returns failure`() {
        assertEquals(
            SyncWorkDecision.FAILURE,
            decideSyncWork(result(failures = mapOf(SyncFailureType.LOCAL_FATAL to 1)))
        )
    }

    @Test fun `permanent rejection alone finishes without retry loop`() {
        assertEquals(
            SyncWorkDecision.SUCCESS,
            decideSyncWork(result(rejectedPending = 1, failures = mapOf(SyncFailureType.PERMANENT to 1)))
        )
    }
}
