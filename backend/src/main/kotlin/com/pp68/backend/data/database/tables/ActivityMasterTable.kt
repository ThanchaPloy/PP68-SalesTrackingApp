package com.pp68.backend.data.database.tables

import org.jetbrains.exposed.sql.Table

object ActivityMasterTable : Table("activity_master") {
    val masterId     = varchar("master_id", 64)
    val activityName = varchar("activity_name", 255)
    val isActive     = bool("is_active").default(true)

    override val primaryKey = PrimaryKey(masterId)
}