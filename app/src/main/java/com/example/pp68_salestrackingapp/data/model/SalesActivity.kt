package com.example.pp68_salestrackingapp.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = "activity_table")
data class SalesActivity(
    // ── Primary Key ──────────────────────────────────────────
    @PrimaryKey
    @ColumnInfo(name = "appointment_id")
    @SerializedName("appointment_id")
    val activityId: String,

    // ── FK ───────────────────────────────────────────────────
    @ColumnInfo(name = "user_id")
    @SerializedName("user_id")
    val userId: String,

    @ColumnInfo(name = "cust_id")
    @SerializedName("cust_id")           // ✅ DB ใช้ cust_id ไม่ใช่ customer_id
    val customerId: String,

    @ColumnInfo(name = "project_id")
    @SerializedName("project_id")
    val projectId: String? = null,

    // ── Appointment fields ───────────────────────────────────
    @ColumnInfo(name = "type")
    @SerializedName("type")              // ✅ DB ใช้ type ไม่ใช่ activity_type
    val activityType: String,

    @ColumnInfo(name = "is_appointment")
    @SerializedName("is_appointment")
    val isAppointment: Boolean = false,

    @ColumnInfo(name = "topic")
    @SerializedName("topic")             // ✅ DB ใช้ topic ไม่ใช่ detail
    val detail: String? = null,

    @ColumnInfo(name = "planned_date")
    @SerializedName("planned_date")      // ✅ DB ใช้ planned_date ไม่ใช่ activity_date
    val activityDate: String,

    @ColumnInfo(name = "planned_time")
    @SerializedName("planned_time")
    val plannedTime: String? = null,

    @ColumnInfo(name = "planned_end_time")
    @SerializedName("planned_end_time")
    val plannedEndTime: String? = null,

    @ColumnInfo(name = "planned_lat")
    @SerializedName("planned_lat")
    val plannedLat: Double? = null,

    @ColumnInfo(name = "planned_long")
    @SerializedName("planned_long")
    val plannedLong: Double? = null,

    // ── Check-in fields ──────────────────────────────────────
    @ColumnInfo(name = "check_in_time")
    @SerializedName("check_in_time")
    val checkInTime: String? = null,

    @ColumnInfo(name = "check_in_lat")
    @SerializedName("check_in_lat")
    val checkInLat: Double? = null,

    @ColumnInfo(name = "check_in_long")
    @SerializedName("check_in_long")
    val checkInLong: Double? = null,

    @ColumnInfo(name = "distance_deviation")
    @SerializedName("distance_deviation")
    val distanceDeviation: Double? = null,

    @ColumnInfo(name = "is_location_verified")
    @SerializedName("is_location_verified")
    val isLocationVerified: Boolean = false,

    // ── Status & Note ────────────────────────────────────────
    @ColumnInfo(name = "plan_status")
    @SerializedName("plan_status")       // ✅ DB ใช้ plan_status ไม่ใช่ status
    val status: String,

    @ColumnInfo(name = "note")
    @SerializedName("note")
    val note: String? = null,

    // ── Local-only fields (ไม่ส่งขึ้น API) ──────────────────
    @ColumnInfo(name = "projectName")
    val projectName: String? = null,
    @ColumnInfo(name = "companyName")
    val companyName: String? = null,
    @ColumnInfo(name = "contactName")
    val contactName: String? = null,
    @ColumnInfo(name = "weeklyNote")
    val weeklyNote: String? = null
)
