package com.example.pp68_salestrackingapp.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = "activity_table")
data class SalesActivity(
    @PrimaryKey
    @ColumnInfo(name = "appointment_id")
    @SerializedName("appointment_id")
    val activityId: String,

    @ColumnInfo(name = "user_id")
    @SerializedName("user_id")
    val userId: String,

    @ColumnInfo(name = "cust_id")
    @SerializedName("cust_id")
    val customerId: String,

    @ColumnInfo(name = "project_id")
    @SerializedName("project_id")
    val projectId: String? = null,

    @ColumnInfo(name = "type")
    @SerializedName("type")
    val activityType: String,

    @ColumnInfo(name = "is_appointment")
    @SerializedName("is_appointment")
    val isAppointment: Boolean = false,

    @ColumnInfo(name = "topic")
    @SerializedName("topic")
    val detail: String? = null,

    @ColumnInfo(name = "planned_date")
    @SerializedName("planned_date")
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

    @ColumnInfo(name = "plan_status")
    @SerializedName("plan_status")
    val status: String,

    @ColumnInfo(name = "note")
    @SerializedName("note")
    val note: String? = null,

    // Local-only fields ไม่ส่งขึ้น API
    @ColumnInfo(name = "project_name")
    val projectName: String? = null,
    @ColumnInfo(name = "company_name")
    val companyName: String? = null,
    @ColumnInfo(name = "contact_name")
    val contactName: String? = null,
    @ColumnInfo(name = "weekly_note")
    val weeklyNote: String? = null
)
