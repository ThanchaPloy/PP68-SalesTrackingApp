package com.pp68.backend.domain.entity

data class Appointment(
    val appointmentId: String,
    val userId: String,
    val customerId: String,
    val projectId: String?,
    val activityType: String,
    val isAppointment: Boolean,
    val topic: String?,
    val plannedDate: String,
    val plannedTime: String?,
    val plannedEndTime: String?,
    val plannedLat: Double?,
    val plannedLong: Double?,
    val checkInTime: String?,
    val checkInLat: Double?,
    val checkInLong: Double?,
    val distanceDeviation: Double?,
    val isLocationVerified: Boolean,
    val status: String,
    val note: String?
)