package com.example.pp68_salestrackingapp.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = "activity_table")
data class SalesActivity(
    @PrimaryKey
    @SerializedName("appointment_id")
    val activityId: String,

    @SerializedName("user_id")
    val userId: String,

    @SerializedName("cust_id")
    val customerId: String,

    @SerializedName("project_id")
    val projectId: String? = null,

    @SerializedName("type")
    val activityType: String,

    @SerializedName("planned_date")
    val activityDate: String,

    @SerializedName("topic")
    val detail: String? = null,

    @SerializedName("plan_status")
    val status: String,

    @SerializedName("planned_time")
    val plannedTime: String? = null,

    @SerializedName("planned_end_time")
    val plannedEndTime: String? = null,

    @SerializedName("planned_lat")
    val plannedLat: Double? = null,

    @SerializedName("planned_long")
    val plannedLong: Double? = null,

    @SerializedName("check_in_time")
    val checkInTime: String? = null,

    @SerializedName("check_in_lat")
    val checkInLat: Double? = null,

    @SerializedName("check_in_long")
    val checkInLong: Double? = null,

    @SerializedName("is_location_verified")
    val isLocationVerified: Boolean = false,

    @SerializedName("note")
    val weeklyNote: String? = null,

    @SerializedName("is_appointment")
    val isAppointment: Boolean = false,

    // ── ไม่ได้มาจาก API — เก็บ local เท่านั้น ──
    val projectName:  String? = null,
    val companyName:  String? = null,
    val contactName:  String? = null   // ✅ เก็บชื่อ contact ที่เลือก
)