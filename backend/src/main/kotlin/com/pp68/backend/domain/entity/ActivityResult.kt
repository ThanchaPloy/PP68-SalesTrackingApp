package com.pp68.backend.domain.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ActivityResult(
    @SerialName("result_id")          val resultId:         String,
    @SerialName("appointment_id")     val appointmentId:    String?  = null,
    @SerialName("project_id")         val projectId:        String?  = null,
    @SerialName("created_by")         val createdBy:        String?  = null,
    @SerialName("report_date")        val reportDate:       String?  = null,
    @SerialName("new_status")         val newStatus:        String?  = null,
    @SerialName("opportunity_score")  val opportunityScore: String?  = null,
    @SerialName("dm_involved")        val dmInvolved:       Boolean  = false,
    @SerialName("dm_contact_id")      val dmContactId:      String?  = null,
    @SerialName("is_proposal_sent")   val isProposalSent:   Boolean  = false,
    @SerialName("proposal_date")      val proposalDate:     String?  = null,
    @SerialName("competitor_count")   val competitorCount:  Int      = 0,
    @SerialName("response_speed")     val responseSpeed:    String?  = null,
    @SerialName("deal_position")      val dealPosition:     String?  = null,
    @SerialName("current_solution")   val currentSolution:  String?  = null,
    @SerialName("counterparty_type")  val counterpartyType: String?  = null,
    @SerialName("note_summary")       val summary:          String?  = null,
    @SerialName("created_at")         val createdAt:        String?  = null,
    @SerialName("photo_url")          val photoUrl:         String?  = null,
    @SerialName("photo_taken_at")     val photoTakenAt:     String?  = null,
    @SerialName("photo_lat")          val photoLat:         Double?  = null,
    @SerialName("photo_lng")          val photoLng:         Double?  = null,
    @SerialName("photo_device_model") val photoDeviceModel: String?  = null
)
