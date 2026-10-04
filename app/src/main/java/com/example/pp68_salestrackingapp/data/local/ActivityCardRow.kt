package com.example.pp68_salestrackingapp.data.local

/** Projection used by the monthly home list; it avoids loading whole lookup tables into memory. */
data class ActivityCardRow(
    val activityId: String,
    val activityType: String,
    val projectName: String?,
    val companyName: String?,
    val contactName: String?,
    val objective: String?,
    val planStatus: String,
    val plannedDate: String?,
    val plannedTime: String?,
    val plannedEndTime: String?,
    val weeklyNote: String?,
    val customerId: String?,
    val hasResult: Boolean,
    val checkInTime: String?,
    val isLocationVerified: Boolean,
    val plannedLat: Double?,
    val plannedLong: Double?,
    val locationName: String?
)
