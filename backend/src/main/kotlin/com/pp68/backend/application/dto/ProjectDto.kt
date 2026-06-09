package com.pp68.backend.application.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProjectDto(
    @SerialName("project_id")               val projectId:             String,
    @SerialName("cust_id")                  val custId:                String,
    @SerialName("branch_id")                val branchId:              String?  = null,
    @SerialName("billing_branch_id")        val billingBranchId:       String?  = null,
    @SerialName("project_name")             val projectName:           String,
    @SerialName("expected_value")           val expectedValue:         Double?  = null,
    @SerialName("project_status")           val projectStatus:         String?  = null,
    @SerialName("start_date")               val startDate:             String?  = null,
    @SerialName("closing_date")             val closingDate:           String?  = null,
    @SerialName("desired_completion_date")  val desiredCompletionDate: String?  = null,
    @SerialName("project_lat")              val projectLat:            Double?  = null,
    @SerialName("project_long")             val projectLong:           Double?  = null,
    @SerialName("opportunity_score")        val opportunityScore:      String?  = null,
    @SerialName("progress_pct")             val progressPct:           Int?     = null,
    @SerialName("loss_reason")              val lossReason:            String?  = null,
    @SerialName("user_id")                  val userId:                String?  = null,
    @SerialName("created_at")              val createdAt:              String?  = null,
    @SerialName("updated_at")              val updatedAt:              String?  = null
)

@Serializable
data class ProjectMemberDto(
    @SerialName("project_id") val projectId: String,
    @SerialName("user_id")    val userId:    String,
    @SerialName("sales_role") val salesRole: String? = null
)