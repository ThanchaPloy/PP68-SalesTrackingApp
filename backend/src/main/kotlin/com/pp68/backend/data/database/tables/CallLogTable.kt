package com.pp68.backend.data.database.tables

import org.jetbrains.exposed.sql.Table

object CallLogTable : Table("call_logs") {
    val logId       = varchar("log_id", 64)
    val userId      = varchar("user_id", 64).nullable()
    val custId      = varchar("cust_id", 64).nullable()
    val phoneNumber = varchar("phone_number", 50).nullable()
    val startTime   = varchar("start_time", 64).nullable()
    val endTime     = varchar("end_time", 64).nullable()
    val duration    = integer("duration").nullable()
    val isSync      = bool("is_sync").default(false)

    override val primaryKey = PrimaryKey(logId)
}
