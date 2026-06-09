package com.pp68.backend.data.database.tables

import org.jetbrains.exposed.sql.Table

object ActivityResultTable : Table("activity_result") {
    val resultId            = varchar("result_id", 64)
    val activityId          = varchar("appointment_id", 64).nullable()
    val projectId           = varchar("project_id", 64).nullable()
    val createdBy           = varchar("created_by", 64).nullable()
    val reportDate          = varchar("report_date", 32).nullable()
    val newStatus           = varchar("new_status", 100).nullable()
    val opportunityScore    = varchar("opportunity_score", 50).nullable()
    val dmInvolved          = bool("dm_involved").default(false)
    val isProposalSent      = bool("is_proposal_sent").default(false)
    val proposalDate        = varchar("proposal_date", 32).nullable()
    val competitorCount     = integer("competitor_count").default(0)
    val responseSpeed       = varchar("response_speed", 100).nullable()
    val dealPosition        = varchar("deal_position", 100).nullable()
    val previousSolution    = text("current_solution").nullable()
    val counterpartyType    = varchar("counterparty_type", 100).nullable()
    val summary             = text("note_summary").nullable()
    val photoUrl            = text("photo_url").nullable()
    val photoTakenAt        = varchar("photo_taken_at", 64).nullable()
    val photoLat            = double("photo_lat").nullable()
    val photoLng            = double("photo_lng").nullable()
    val photoDeviceModel    = varchar("photo_device_model", 255).nullable()
    val lossReason          = text("loss_reason").nullable()

    override val primaryKey = PrimaryKey(resultId)
}