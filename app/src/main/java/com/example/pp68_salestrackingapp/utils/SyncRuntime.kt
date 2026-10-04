package com.example.pp68_salestrackingapp.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant

enum class SyncTrigger { SAVE, APP_FOREGROUND, LOGIN, MANUAL, PERIODIC }

enum class SyncFailureType {
    NETWORK,
    TIMEOUT,
    RATE_LIMITED,
    SERVER,
    AUTHENTICATION,
    PERMANENT,
    DEPENDENCY,
    LOCAL_FATAL,
    UNKNOWN
}

fun classifySyncFailure(error: Throwable): SyncFailureType = when (error) {
    is kotlinx.coroutines.CancellationException -> throw error
    is java.net.SocketTimeoutException -> SyncFailureType.TIMEOUT
    is java.io.IOException -> SyncFailureType.NETWORK
    else -> {
        val code = Regex("(?:HTTP|API error:)\\s*(\\d{3})", RegexOption.IGNORE_CASE)
            .find(error.message.orEmpty())?.groupValues?.getOrNull(1)?.toIntOrNull()
        classifyHttpCode(code)
    }
}

fun classifyHttpCode(code: Int?): SyncFailureType = when (code) {
    null, 0 -> SyncFailureType.UNKNOWN
    401 -> SyncFailureType.AUTHENTICATION
    408 -> SyncFailureType.TIMEOUT
    429 -> SyncFailureType.RATE_LIMITED
    in 500..599 -> SyncFailureType.SERVER
    in setOf(400, 403, 404, 409, 422) -> SyncFailureType.PERMANENT
    else -> if (code in 400..499) SyncFailureType.PERMANENT else SyncFailureType.UNKNOWN
}

sealed interface SyncStatus {
    data object Idle : SyncStatus
    data class Queued(val trigger: SyncTrigger) : SyncStatus
    data class Running(val runId: String) : SyncStatus
    data class Success(val at: String, val uploaded: Int) : SyncStatus
    data class WaitingForNetwork(val pendingCount: Int) : SyncStatus
    data class NeedsAttention(val pendingCount: Int, val rejectedCount: Int) : SyncStatus
    data class Failed(val at: String, val errorType: String) : SyncStatus
}

data class SyncRunResult(
    val runId: String,
    val attempted: Int,
    val succeeded: Int,
    val temporaryFailures: Int,
    val permanentFailures: Int,
    val rejectedPending: Int,
    val skipped: Int,
    val stillPending: Int,
    val failureTypes: Map<SyncFailureType, Int> = emptyMap(),
    val startedAt: String,
    val finishedAt: String
) {
    val shouldRetry: Boolean get() = temporaryFailures > 0 || (stillPending > 0 && skipped > 0)
    val hasLocalFatalFailure: Boolean get() = failureTypes[SyncFailureType.LOCAL_FATAL].orZero() > 0
    val hasAuthenticationFailure: Boolean get() = failureTypes[SyncFailureType.AUTHENTICATION].orZero() > 0
}

private fun Int?.orZero(): Int = this ?: 0

/** สถานะกลางระดับ process; ข้อมูลจริงยังคงอยู่ใน Room/WorkManager เสมอ */
object SyncRuntime {
    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    fun queued(trigger: SyncTrigger) { _status.value = SyncStatus.Queued(trigger) }
    fun running(runId: String) { _status.value = SyncStatus.Running(runId) }
    fun completed(result: SyncRunResult) {
        _status.value = when {
            result.rejectedPending > 0 || result.hasAuthenticationFailure ->
                SyncStatus.NeedsAttention(result.stillPending, result.rejectedPending)
            result.shouldRetry -> SyncStatus.WaitingForNetwork(result.stillPending)
            else -> SyncStatus.Success(Instant.now().toString(), result.succeeded)
        }
    }
    fun failed(error: Throwable) {
        _status.value = SyncStatus.Failed(Instant.now().toString(), error::class.java.simpleName)
    }
}
