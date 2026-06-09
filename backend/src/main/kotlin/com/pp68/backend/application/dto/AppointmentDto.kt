package com.pp68.backend.application.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AppointmentDto(
    @SerialName("appointment_id")      val appointmentId:      String,
    @SerialName("user_id")             val userId:             String,
    @SerialName("cust_id")             val customerId:         String,
    @SerialName("project_id")          val projectId:          String?  = null,
    val type:                                                   String,
    @SerialName("is_appointment")      val isAppointment:      Boolean  = false,
    val topic:                                                  String?  = null,
    @SerialName("planned_date")        val plannedDate:        String,
    @SerialName("planned_time")        val plannedTime:        String?  = null,
    @SerialName("planned_end_time")    val plannedEndTime:     String?  = null,
    @SerialName("planned_lat")         val plannedLat:         Double?  = null,
    @SerialName("planned_long")        val plannedLong:        Double?  = null,
    @SerialName("check_in_time")       val checkInTime:        String?  = null,
    @SerialName("check_in_lat")        val checkInLat:         Double?  = null,
    @SerialName("check_in_long")       val checkInLong:        Double?  = null,
    @SerialName("distance_deviation")  val distanceDeviation:  Double?  = null,
    @SerialName("is_location_verified") val isLocationVerified: Boolean = false,
    @SerialName("plan_status")         val status:             String,
    val note:                                                   String?  = null
)

@Serializable
data class ActivityResultDto(
    @SerialName("result_id")          val resultId:          String,
    @SerialName("appointment_id")     val activityId:        String?  = null,
    @SerialName("project_id")         val projectId:         String?  = null,
    @SerialName("created_by")         val createdBy:         String?  = null,
    @SerialName("report_date")        val reportDate:        String?  = null,
    @SerialName("new_status")         val newStatus:         String?  = null,
    @SerialName("opportunity_score")  val opportunityScore:  String?  = null,
    @SerialName("dm_involved")        val dmInvolved:        Boolean  = false,
    @SerialName("is_proposal_sent")   val isProposalSent:    Boolean  = false,
    @SerialName("proposal_date")      val proposalDate:      String?  = null,
    @SerialName("competitor_count")   val competitorCount:   Int      = 0,
    @SerialName("response_speed")     val responseSpeed:     String?  = null,
    @SerialName("deal_position")      val dealPosition:      String?  = null,
    @SerialName("current_solution")   val previousSolution:  String?  = null,
    @SerialName("counterparty_type")  val counterpartyType:  String?  = null,
    @SerialName("note_summary")       val summary:           String?  = null,
    @SerialName("photo_url")          val photoUrl:          String?  = null,
    @SerialName("photo_taken_at")     val photoTakenAt:      String?  = null,
    @SerialName("photo_lat")          val photoLat:          Double?  = null,
    @SerialName("photo_lng")          val photoLng:          Double?  = null,
    @SerialName("photo_device_model") val photoDeviceModel:  String?  = null,
    @SerialName("loss_reason")        val lossReason:        String?  = null
)