package com.pp68.backend.data.database.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object ActivityResultTable : Table("activity_result") {
    val resultId         = varchar("result_id", 64)
    val appointmentId    = varchar("appointment_id", 64).nullable()
    val projectId        = text("project_id").nullable()
    val createdBy        = varchar("created_by", 64).nullable()
    val reportDate       = varchar("report_date", 32).nullable()
    val newStatus        = varchar("new_status", 100).nullable()
    val opportunityScore = varchar("opportunity_score", 50).nullable()
    val dmInvolved       = bool("dm_involved").default(false)
    val dmContactId      = varchar("dm_contact_id", 64).nullable()
    val isProposalSent   = bool("is_proposal_sent").default(false)
    val proposalDate     = varchar("proposal_date", 32).nullable()
    val competitorCount  = integer("competitor_count").default(0)
    val responseSpeed    = varchar("response_speed", 100).nullable()
    val dealPosition     = varchar("deal_position", 100).nullable()
    val currentSolution  = text("current_solution").nullable()
    val counterpartyType = varchar("counterparty_type", 100).nullable()
    val summary          = text("note_summary").nullable()
    val createdAt        = timestamp("created_at").nullable()
    val photoUrl         = text("photo_url").nullable()
    val photoTakenAt     = text("photo_taken_at").nullable()
    val photoLat         = double("photo_lat").nullable()
    val photoLng         = double("photo_lng").nullable()
    val photoDeviceModel = text("photo_device_model").nullable()

    override val primaryKey = PrimaryKey(resultId)
}
