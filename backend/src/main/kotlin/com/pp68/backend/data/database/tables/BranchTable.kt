package com.pp68.backend.data.database.tables

import org.jetbrains.exposed.sql.Table

object BranchTable : Table("branch") {
    val branchCode = varchar("branch_code", 64)
    val name       = varchar("name", 255)
    val region     = varchar("region", 100).nullable()

    override val primaryKey = PrimaryKey(branchCode)
}
