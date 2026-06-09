package com.pp68.backend.data.repository

import com.pp68.backend.data.database.tables.ActivityMasterTable
import com.pp68.backend.domain.entity.ActivityMaster
import com.pp68.backend.domain.repository.ActivityMasterRepository
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class ActivityMasterRepositoryImpl : ActivityMasterRepository {

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }

    private fun ResultRow.toMaster() = ActivityMaster(
        masterId     = this[ActivityMasterTable.masterId],
        activityName = this[ActivityMasterTable.activityName],
        isActive     = this[ActivityMasterTable.isActive]
    )

    override suspend fun findActive(): List<ActivityMaster> = dbQuery {
        ActivityMasterTable.select { ActivityMasterTable.isActive eq true }.map { it.toMaster() }
    }

    override suspend fun findAll(): List<ActivityMaster> = dbQuery {
        ActivityMasterTable.selectAll().map { it.toMaster() }
    }
}