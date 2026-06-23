package com.pp68.backend.data.repository

import com.pp68.backend.data.database.tables.ActivityMasterTable
import com.pp68.backend.domain.entity.ActivityMaster
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll

class ActivityMasterRepositoryImpl {

    private fun ResultRow.toMaster() = ActivityMaster(
        masterId  = this[ActivityMasterTable.masterId],
        category  = this[ActivityMasterTable.category],
        objective = this[ActivityMasterTable.objective],
        actName   = this[ActivityMasterTable.actName],
        isActive  = this[ActivityMasterTable.isActive]
    )

    suspend fun findActive(): List<ActivityMaster> = dbQuery {
        ActivityMasterTable.select { ActivityMasterTable.isActive eq true }.map { it.toMaster() }
    }

    suspend fun findAll(): List<ActivityMaster> = dbQuery {
        ActivityMasterTable.selectAll().map { it.toMaster() }
    }
}
