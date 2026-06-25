package com.pp68.backend.data.repository

import com.pp68.backend.data.database.tables.AppointmentTable
import com.pp68.backend.data.database.tables.EmployeeTable
import com.pp68.backend.domain.entity.Appointment
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList

class AppointmentRepositoryImpl {

    private fun ResultRow.toAppointment() = Appointment(
        appointmentId      = this[AppointmentTable.appointmentId],
        userId             = this[AppointmentTable.userId],
        customerId         = this[AppointmentTable.customerId],
        projectId          = this[AppointmentTable.projectId],
        activityType       = this[AppointmentTable.activityType],
        isAppointment      = this[AppointmentTable.isAppointment],
        topic              = this[AppointmentTable.topic],
        plannedDate        = this[AppointmentTable.plannedDate]?.toString(),
        plannedTime        = this[AppointmentTable.plannedTime]?.toString(),
        plannedEndTime     = this[AppointmentTable.plannedEndTime]?.toString(),
        plannedLat         = this[AppointmentTable.plannedLat],
        plannedLong        = this[AppointmentTable.plannedLong],
        checkInTime        = this[AppointmentTable.checkInTime],
        checkInLat         = this[AppointmentTable.checkInLat],
        checkInLong        = this[AppointmentTable.checkInLong],
        distanceDeviation  = this[AppointmentTable.distanceDeviation],
        isLocationVerified = this[AppointmentTable.isLocationVerified],
        status             = this[AppointmentTable.status],
        note               = this[AppointmentTable.note],
        createdAt          = this[AppointmentTable.createdAt]?.toString()
    )

    suspend fun findByUserId(userId: String, limit: Int): List<Appointment> = dbQuery {
        AppointmentTable
            .select { AppointmentTable.userId eq userId }
            .orderBy(AppointmentTable.plannedDate, SortOrder.DESC)
            .limit(limit)
            .map { it.toAppointment() }
    }

    suspend fun findById(appointmentId: String): Appointment? = dbQuery {
        AppointmentTable.select { AppointmentTable.appointmentId eq appointmentId }
            .singleOrNull()?.toAppointment()
    }

    suspend fun create(appointment: Appointment): Appointment = dbQuery {
        AppointmentTable.insert {
            it[appointmentId]      = appointment.appointmentId
            it[userId]             = appointment.userId
            it[customerId]         = appointment.customerId
            it[projectId]          = appointment.projectId
            it[activityType]       = appointment.activityType
            it[isAppointment]      = appointment.isAppointment
            it[topic]              = appointment.topic
            it[plannedDate]        = appointment.plannedDate?.let { LocalDate.parse(it) }
            it[plannedTime]        = appointment.plannedTime?.let { LocalTime.parse(it) }
            it[plannedEndTime]     = appointment.plannedEndTime?.let { LocalTime.parse(it) }
            it[plannedLat]         = appointment.plannedLat
            it[plannedLong]        = appointment.plannedLong
            it[status]             = appointment.status
            it[note]               = appointment.note
        }
        AppointmentTable.select { AppointmentTable.appointmentId eq appointment.appointmentId }
            .single().toAppointment()
    }

    suspend fun update(appointmentId: String, updates: Map<String, Any?>): Appointment? = dbQuery {
        AppointmentTable.update({ AppointmentTable.appointmentId eq appointmentId }) { stmt ->
            updates["plan_status"]?.let          { v -> stmt[status]             = v as String }
            updates["check_in_time"]?.let        { v -> stmt[checkInTime]        = v as String }
            updates["check_in_lat"]?.let         { v -> stmt[checkInLat]         = (v as Number).toDouble() }
            updates["check_in_long"]?.let        { v -> stmt[checkInLong]        = (v as Number).toDouble() }
            updates["distance_deviation"]?.let   { v -> stmt[distanceDeviation]  = (v as Number).toDouble() }
            updates["is_location_verified"]?.let { v -> stmt[isLocationVerified] = v as Boolean }
            updates["note"]?.let                 { v -> stmt[note]               = v as String }
            updates["topic"]?.let                { v -> stmt[topic]              = v as String }
        }
        AppointmentTable.select { AppointmentTable.appointmentId eq appointmentId }
            .singleOrNull()?.toAppointment()
    }

    suspend fun delete(appointmentId: String): Boolean = dbQuery {
        AppointmentTable.deleteWhere { AppointmentTable.appointmentId eq appointmentId } > 0
    }

    suspend fun deleteByCustId(custId: String): Boolean = dbQuery {
        AppointmentTable.deleteWhere { AppointmentTable.customerId eq custId } > 0
    }

    suspend fun findUpcomingForReminders(withinMinutes: Long = 60): List<Pair<Appointment, String?>> = dbQuery {
        val thaiZone  = ZoneId.of("Asia/Bangkok")
        val nowThai   = LocalDateTime.now(thaiZone)
        val cutoff    = nowThai.plusMinutes(withinMinutes)
        val todayDate = nowThai.toLocalDate()

        // Step 1: get today's appointments in the time window (simple select — no JOIN to avoid column qualifier issues)
        val appointments = AppointmentTable
            .select { AppointmentTable.plannedDate eq todayDate }
            // NOTE: add status filter here once actual plan_status values are confirmed
            .mapNotNull { row ->
                val plannedTime = row[AppointmentTable.plannedTime] ?: return@mapNotNull null
                val plannedDt   = LocalDateTime.of(todayDate, plannedTime)
                if (plannedDt.isAfter(nowThai) && !plannedDt.isAfter(cutoff)) row.toAppointment() else null
            }

        if (appointments.isEmpty()) return@dbQuery emptyList()

        // Step 2: batch-fetch FCM tokens for the relevant users
        val userIds = appointments.map { it.userId }.distinct()
        val tokenMap = EmployeeTable
            .select { EmployeeTable.empCode inList userIds }
            .associate { it[EmployeeTable.empCode] to it[EmployeeTable.fcmToken] }

        // Step 3: merge — only include appointments where an FCM token exists
        appointments.mapNotNull { a ->
            tokenMap[a.userId]?.let { token -> Pair(a, token) }
        }
    }
}
