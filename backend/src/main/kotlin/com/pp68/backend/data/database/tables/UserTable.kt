package com.pp68.backend.data.database.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object EmployeeTable : Table("employee") {
    val empCode     = varchar("emp_code", 50)
    val empName     = varchar("emp_name", 255).nullable()
    val empPostCode = varchar("emp_post_code", 50).nullable()
    val empPost     = varchar("emp_post", 100).nullable()
    val empBrchCode = varchar("emp_brch_code", 50).nullable()
    val empBrchName = varchar("emp_brch_name", 255).nullable()
    val stat        = varchar("stat", 10).nullable()
    val createDate  = timestamp("create_date").nullable()
    val updatedAt   = timestamp("updated_at").nullable()
    val password    = varchar("password", 255).nullable()
    val empType     = varchar("emp_type", 50).nullable()

    override val primaryKey = PrimaryKey(empCode)
}
