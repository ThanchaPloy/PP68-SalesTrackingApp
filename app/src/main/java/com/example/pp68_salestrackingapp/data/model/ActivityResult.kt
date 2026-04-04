package com.example.pp68_salestrackingapp.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = "activity_result")
data class ActivityResult(
    @PrimaryKey
    @ColumnInfo(name = "appointment_id")
    @SerializedName("appointment_id")
    val activityId: String,

    @ColumnInfo(name = "created_by")
    @SerializedName("created_by")
    val createdBy: String? = null,

    @ColumnInfo(name = "report_date")
    @SerializedName("report_date")
    val reportDate: String? = null,

    @ColumnInfo(name = "new_status")
    @SerializedName("new_status")
    val newStatus: String? = null,

    @ColumnInfo(name = "opportunity_score")
    @SerializedName("opportunity_score")
    val opportunityScore: String? = null,

    @ColumnInfo(name = "dm_involved")
    @SerializedName("dm_involved")
    val dmInvolved: Boolean = false,

    @ColumnInfo(name = "dm_contact_id")
    @SerializedName("dm_contact_id")
    val dmContactId: String? = null,

    @ColumnInfo(name = "is_proposal_sent")
    @SerializedName("is_proposal_sent")
    val isProposalSent: Boolean = false,

    @ColumnInfo(name = "proposal_date")
    @SerializedName("proposal_date")
    val proposalDate: String? = null,

    @ColumnInfo(name = "competitor_count")
    @SerializedName("competitor_count")
    val competitorCount: Int = 0,

    @ColumnInfo(name = "response_speed")
    @SerializedName("response_speed")
    val responseSpeed: String? = null,

    @ColumnInfo(name = "deal_position")
    @SerializedName("deal_position")
    val dealPosition: String? = null,

    @ColumnInfo(name = "current_solution")
    @SerializedName("current_solution")
    val previousSolution: String? = null,

    @ColumnInfo(name = "counterparty_type")
    @SerializedName("counterparty_type")
    val counterpartyMultiplier: String? = null,

    @ColumnInfo(name = "note_summary")
    @SerializedName("note_summary")
    val summary: String? = null,

    @ColumnInfo(name = "photo_url")
    @SerializedName("photo_url")
    val photoUrl: String? = null,

    @ColumnInfo(name = "photo_taken_at")
    @SerializedName("photo_taken_at")
    val photoTakenAt: String? = null,

    @ColumnInfo(name = "photo_lat")
    @SerializedName("photo_lat")
    val photoLat: Double? = null,

    @ColumnInfo(name = "photo_lng")
    @SerializedName("photo_lng")
    val photoLng: Double? = null,

    @ColumnInfo(name = "photo_device_model")
    @SerializedName("photo_device_model")
    val photoDeviceModel: String? = null
)
