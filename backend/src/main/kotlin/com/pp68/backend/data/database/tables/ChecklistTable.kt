package com.pp68.backend.data.database.tables

import org.jetbrains.exposed.sql.Table

object ChecklistTable : Table("appointment_checklist") {
    val appointmentId = varchar("appointment_id", 64)
    val masterId      = varchar("master_id", 64)
    val isChecked     = bool("is_checked").default(false)

    override val primaryKey = PrimaryKey(appointmentId, masterId)
}