package com.example.pp68_salestrackingapp.data.remote

import com.example.pp68_salestrackingapp.data.model.ActivityResult
import com.example.pp68_salestrackingapp.data.model.Customer
import com.example.pp68_salestrackingapp.data.model.Project
import com.example.pp68_salestrackingapp.data.model.SalesActivity

/**
 * Source of truth for POST payloads that can be retried from the local outbox.
 *
 * Idempotency compares the effective request payload on the server. The immediate-send path and
 * WorkManager path must therefore send the same fields and values for a given local operation.
 */
object CreateRequestPayloads {
    fun customer(customer: Customer): Map<String, Any?> = linkedMapOf(
        "customer_name" to customer.companyName,
        "gen_bus_posting_group" to customer.bizPostingGroup,
        "cust_type" to customer.custType,
        "address" to customer.companyAddr,
        "latitude" to customer.companyLat,
        "longitude" to customer.companyLong,
        "customer_status" to customer.companyStatus,
        "create_date" to customer.createdAt,
        "created_at" to customer.createdAt,
        "create_by" to customer.createdBy,
        "salesperson_code" to customer.createdBy,
        "grade" to customer.grade,
        "vat_registration_no" to customer.vatRegistrationNo
    ).filterValues { it != null }

    fun project(project: Project): Map<String, Any?> = linkedMapOf(
        "customer_code" to project.custId,
        "customer_name" to project.customerName,
        "project_name" to project.projectName,
        "branch_code" to project.branchId,
        "billing_branch_id" to project.billingBranchId,
        "request_by" to project.requestBy,
        "create_by" to project.createBy,
        "remark" to project.remark,
        "project_status" to project.projectStatus,
        "expected_value" to project.expectedValue,
        "loss_reason" to project.lossReason,
        "loss_reason_note" to project.lossReasonNote,
        "start_date" to project.startDate,
        "closing_date" to project.closingDate,
        "desired_completion_date" to project.desiredCompletionDate,
        "project_lat" to project.projectLat,
        "project_long" to project.projectLong,
        "opportunity_score" to project.opportunityScore,
        "deal_position" to project.dealPosition,
        "current_solution" to project.previousSolution,
        "counterparty_type" to project.counterpartyType,
        "response_speed" to project.responseSpeed,
        "is_proposal_sent" to project.isProposalSent,
        "proposal_date" to project.proposalDate,
        "competitor_count" to project.competitorCount,
        "progress_pct" to project.progressPct,
        "created_at" to project.createdAt
    ).filterValues { it != null }

    fun activity(activity: SalesActivity): Map<String, Any?> = linkedMapOf(
        "emp_code" to activity.userId,
        "cust_code" to activity.customerId?.takeUnless { it == "CST-UNKNOWN" },
        "project_code" to activity.projectId,
        "type" to activity.activityType,
        "is_appointment" to activity.isAppointment,
        "topic" to activity.detail,
        "planned_date" to activity.activityDate,
        "planned_time" to activity.plannedTime,
        "planned_end_time" to activity.plannedEndTime,
        "planned_lat" to activity.plannedLat,
        "planned_long" to activity.plannedLong,
        "plan_status" to activity.status,
        "created_at" to activity.createdAt
    ).filterValues { it != null }

    fun result(result: ActivityResult, includeResultId: Boolean): Map<String, Any?> = linkedMapOf(
        "result_id" to result.resultId.takeIf { includeResultId },
        "appointment_id" to result.activityId,
        "project_code" to result.projectId,
        "created_by" to result.createdBy,
        "report_date" to result.reportDate,
        "new_status" to result.newStatus,
        "opportunity_score" to result.opportunityScore,
        "dm_involved" to result.dmInvolved,
        "is_proposal_sent" to result.isProposalSent,
        "proposal_date" to result.proposalDate,
        "competitor_count" to result.competitorCount,
        "response_speed" to result.responseSpeed,
        "deal_position" to result.dealPosition,
        "current_solution" to result.previousSolution,
        "counterparty_type" to result.counterpartyMultiplier,
        "note_summary" to result.summary,
        "photo_url" to result.photoUrl,
        "photo_taken_at" to result.photoTakenAt,
        "photo_lat" to result.photoLat,
        "photo_lng" to result.photoLng,
        "photo_device_model" to result.photoDeviceModel,
        "version" to result.version,
        "is_latest" to result.isLatest,
        "result_group_id" to result.resultGroupId,
        "loss_reason" to result.lossReason,
        "loss_reason_note" to result.lossReasonNote
    ).filterValues { it != null }
}
