package com.pp68.backend.data.database.tables

import org.jetbrains.exposed.sql.Table

object ProjectContactTable : Table("project_contact") {
    val projectId = varchar("project_id", 64)
    val contactId = varchar("contact_id", 64)

    override val primaryKey = PrimaryKey(projectId, contactId)
}