package com.pp68.backend.data.repository

import com.pp68.backend.data.database.tables.CallLogTable
import com.pp68.backend.domain.entity.CallLog
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select

class CallLogRepositoryImpl {

    suspend fun create(callLog: CallLog): CallLog = dbQuery {
        CallLogTable.insert {
            it[logId]       = callLog.logId
            it[userId]      = callLog.userId
            it[custId]      = callLog.custId
            it[phoneNumber] = callLog.phoneNumber
            it[startTime]   = callLog.startTime
            it[endTime]     = callLog.endTime
            it[duration]    = callLog.duration
            it[isSync]      = callLog.isSync
        }
        callLog
    }

    suspend fun findByUserId(userId: String): List<CallLog> = dbQuery {
        CallLogTable.select { CallLogTable.userId eq userId }.map {
            CallLog(
                logId       = it[CallLogTable.logId],
                userId      = it[CallLogTable.userId]!!,
                custId      = it[CallLogTable.custId],
                phoneNumber = it[CallLogTable.phoneNumber],
                startTime   = it[CallLogTable.startTime],
                endTime     = it[CallLogTable.endTime],
                duration    = it[CallLogTable.duration],
                isSync      = it[CallLogTable.isSync]
            )
        }
    }
}
