package com.pp68.backend.data.database.tables

import org.jetbrains.exposed.sql.Table

object AppointmentTable : Table("appointment") {
    val appointmentId      = varchar("appointment_id", 64)
    val userId             = varchar("user_id", 64)
    val customerId         = varchar("cust_id", 64)
    val projectId          = varchar("project_id", 64).nullable()
    val activityType       = varchar("type", 100)
    val isAppointment      = bool("is_appointment").default(false)
    val topic              = text("topic").nullable()
    val plannedDate        = varchar("planned_date", 32)
    val plannedTime        = varchar("planned_time", 16).nullable()
    val plannedEndTime     = varchar("planned_end_time", 16).nullable()
    val plannedLat         = double("planned_lat").nullable()
    val plannedLong        = double("planned_long").nullable()
    val checkInTime        = varchar("check_in_time", 64).nullable()
    val checkInLat         = double("check_in_lat").nullable()
    val checkInLong        = double("check_in_long").nullable()
    val distanceDeviation  = double("distance_deviation").nullable()
    val isLocationVerified = bool("is_location_verified").default(false)
    val status             = varchar("plan_status", 50)
    val note               = text("note").nullable()

    override val primaryKey = PrimaryKey(appointmentId)
}