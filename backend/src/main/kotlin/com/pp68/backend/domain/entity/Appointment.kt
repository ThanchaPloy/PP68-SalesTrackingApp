package com.pp68.backend.domain.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Appointment(
    @SerialName("appointment_id")       val appointmentId:      String,
    @SerialName("user_id")              val userId:             String,
    @SerialName("cust_id")              val customerId:         String,
    @SerialName("project_id")           val projectId:          String?  = null,
    @SerialName("type")                 val activityType:       String,
    @SerialName("is_appointment")       val isAppointment:      Boolean  = false,
    @SerialName("topic")                val topic:              String?  = null,
    @SerialName("planned_date")         val plannedDate:        String?  = null,
    @SerialName("planned_time")         val plannedTime:        String?  = null,
    @SerialName("planned_end_time")     val plannedEndTime:     String?  = null,
    @SerialName("planned_lat")          val plannedLat:         Double?  = null,
    @SerialName("planned_long")         val plannedLong:        Double?  = null,
    @SerialName("check_in_time")        val checkInTime:        String?  = null,
    @SerialName("check_in_lat")         val checkInLat:         Double?  = null,
    @SerialName("check_in_long")        val checkInLong:        Double?  = null,
    @SerialName("distance_deviation")   val distanceDeviation:  Double?  = null,
    @SerialName("is_location_verified") val isLocationVerified: Boolean  = false,
    @SerialName("plan_status")          val status:             String?  = null,
    @SerialName("note")                 val note:               String?  = null,
    @SerialName("created_at")           val createdAt:          String?  = null
)
