package com.pp68.backend.data.database.tables

import org.jetbrains.exposed.sql.Table

object ProjectMemberTable : Table("project_sales_member") {
    val projectId = varchar("project_id", 64)
    val userId    = varchar("user_id", 64)
    val salesRole = varchar("sales_role", 100).nullable()

    override val primaryKey = PrimaryKey(projectId, userId)
}