package com.pp68.backend.domain.repository

import com.pp68.backend.domain.entity.CallLog

interface CallLogRepository {
    suspend fun create(callLog: CallLog): CallLog
    suspend fun findByUserId(userId: String): List<CallLog>
}