package com.pp68.backend.data.repository

import com.pp68.backend.data.database.tables.CallLogTable
import com.pp68.backend.domain.entity.CallLog
import com.pp68.backend.domain.repository.CallLogRepository
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class CallLogRepositoryImpl : CallLogRepository {

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }

    override suspend fun create(callLog: CallLog): CallLog = dbQuery {
        CallLogTable.insert {
            it[callLogId]    = callLog.callLogId
            it[userId]       = callLog.userId
            it[custId]       = callLog.custId
            it[calledNumber] = callLog.calledNumber
            it[callDate]     = callLog.callDate
            it[duration]     = callLog.duration
        }
        callLog
    }

    override suspend fun findByUserId(userId: String): List<CallLog> = dbQuery {
        CallLogTable.select { CallLogTable.userId eq userId }.map {
            CallLog(
                callLogId    = it[CallLogTable.callLogId],
                userId       = it[CallLogTable.userId],
                custId       = it[CallLogTable.custId],
                calledNumber = it[CallLogTable.calledNumber],
                callDate     = it[CallLogTable.callDate],
                duration     = it[CallLogTable.duration]
            )
        }
    }
}