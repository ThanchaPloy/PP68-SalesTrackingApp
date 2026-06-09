package com.pp68.backend.data.database.tables

import org.jetbrains.exposed.sql.Table

object CallLogTable : Table("call_log") {
    val callLogId    = varchar("call_log_id", 64)
    val userId       = varchar("user_id", 64)
    val custId       = varchar("cust_id", 64).nullable()
    val calledNumber = varchar("called_number", 50)
    val callDate     = varchar("call_date", 32)
    val duration     = integer("duration").nullable()

    override val primaryKey = PrimaryKey(callLogId)
}