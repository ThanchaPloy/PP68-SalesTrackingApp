package com.pp68.backend.data.database.tables

import org.jetbrains.exposed.sql.Table

object BranchTable : Table("branch") {
    val branchId   = varchar("branch_id", 64)
    val branchName = varchar("branch_name", 255)

    override val primaryKey = PrimaryKey(branchId)
}