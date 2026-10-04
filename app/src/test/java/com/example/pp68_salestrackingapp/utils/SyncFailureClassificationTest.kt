package com.example.pp68_salestrackingapp.utils

import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncFailureClassificationTest {
    @Test fun `classifies transport and http failures`() {
        assertEquals(SyncFailureType.NETWORK, classifySyncFailure(IOException("offline")))
        assertEquals(SyncFailureType.TIMEOUT, classifySyncFailure(SocketTimeoutException("slow")))
        assertEquals(SyncFailureType.AUTHENTICATION, classifySyncFailure(Exception("HTTP 401")))
        assertEquals(SyncFailureType.RATE_LIMITED, classifySyncFailure(Exception("API error: 429")))
        assertEquals(SyncFailureType.SERVER, classifySyncFailure(Exception("HTTP 503")))
        assertEquals(SyncFailureType.PERMANENT, classifySyncFailure(Exception("HTTP 422")))
        assertEquals(SyncFailureType.UNKNOWN, classifySyncFailure(IllegalStateException("broken")))
    }

    @Test fun `cancellation is propagated`() {
        var cancelled = false
        try {
            classifySyncFailure(CancellationException("cancelled"))
        } catch (_: CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
    }
}
