package com.pp68.backend.data.database.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.time
import org.jetbrains.exposed.sql.javatime.timestamp

object AppointmentTable : Table("appointment") {
    val appointmentId      = varchar("appointment_id", 64)
    val userId             = varchar("emp_code", 64)
    val customerId         = varchar("cust_code", 64)
    val projectId          = varchar("project_code", 64).nullable()
    val activityType       = varchar("type", 100)
    val isAppointment      = bool("is_appointment").default(false)
    val topic              = text("topic").nullable()
    val plannedDate        = date("planned_date").nullable()
    val plannedTime        = time("planned_time").nullable()
    val plannedEndTime     = time("planned_end_time").nullable()
    val plannedLat         = double("planned_lat").nullable()
    val plannedLong        = double("planned_long").nullable()
    val checkInTime        = varchar("check_in_time", 64).nullable()
    val checkInLat         = double("check_in_lat").nullable()
    val checkInLong        = double("check_in_long").nullable()
    val distanceDeviation  = double("distance_deviation").nullable()
    val isLocationVerified = bool("is_location_verified").default(false)
    val status             = varchar("plan_status", 50).nullable()
    val note               = text("note").nullable()
    val createdAt          = timestamp("created_at").nullable()

    override val primaryKey = PrimaryKey(appointmentId)
}
