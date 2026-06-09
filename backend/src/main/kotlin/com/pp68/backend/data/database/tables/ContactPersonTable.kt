package com.pp68.backend.data.database.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object ContactPersonTable : Table("contact_person") {
    val contactId = varchar("contact_id", 64)
    val custId    = varchar("cust_id", 64).nullable()
    val userId    = varchar("user_id", 64).nullable()
    val fullName  = varchar("full_name", 255)
    val role      = varchar("role", 100).nullable()
    val phone     = varchar("phone", 50).nullable()
    val email     = varchar("email", 255).nullable()
    val lineId    = varchar("line_id", 100).nullable()
    val createdAt = timestamp("created_at").nullable()

    override val primaryKey = PrimaryKey(contactId)
}