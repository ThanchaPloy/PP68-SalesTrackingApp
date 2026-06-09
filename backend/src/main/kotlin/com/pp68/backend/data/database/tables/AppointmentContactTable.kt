package com.pp68.backend.data.database.tables

import org.jetbrains.exposed.sql.Table

object AppointmentContactTable : Table("appointment_contact") {
    val appointmentId = varchar("appointment_id", 64)
    val contactId     = varchar("contact_id", 64)

    override val primaryKey = PrimaryKey(appointmentId, contactId)
}