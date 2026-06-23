package com.pp68.backend.data.database.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object ProjectTable : Table("project") {
    val projectCode  = varchar("project_code", 64)
    val projectName  = varchar("project_name", 255).nullable()
    val customerCode = varchar("customer_code", 64).nullable()
    val customerName = varchar("customer_name", 255).nullable()
    val branchCode   = varchar("branch_code", 50).nullable()
    val requestBy    = varchar("request_by", 50).nullable()
    val remark       = text("remark").nullable()
    val updatedAt    = timestamp("updated_at").nullable()

    override val primaryKey = PrimaryKey(projectCode)
}
