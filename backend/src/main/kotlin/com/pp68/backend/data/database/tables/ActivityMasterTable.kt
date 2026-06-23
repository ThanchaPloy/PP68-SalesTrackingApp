package com.pp68.backend.data.database.tables

import org.jetbrains.exposed.sql.Table

object ActivityMasterTable : Table("activity_master") {
    val masterId  = integer("master_id").autoIncrement()
    val category  = varchar("category", 100)
    val objective = varchar("objective", 255)
    val actName   = varchar("act_name", 255)
    val isActive  = bool("is_active").default(true)

    override val primaryKey = PrimaryKey(masterId)
}
