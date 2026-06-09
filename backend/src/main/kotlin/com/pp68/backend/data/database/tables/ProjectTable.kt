package com.pp68.backend.data.database.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object ProjectTable : Table("project") {
    val projectId              = varchar("project_id", 64)
    val custId                 = varchar("cust_id", 64)
    val branchId               = varchar("branch_id", 64).nullable()
    val billingBranchId        = varchar("billing_branch_id", 64).nullable()
    val projectName            = varchar("project_name", 255)
    val expectedValue          = double("expected_value").nullable()
    val projectStatus          = varchar("project_status", 100).nullable()
    val startDate              = varchar("start_date", 32).nullable()
    val closingDate            = varchar("closing_date", 32).nullable()
    val desiredCompletionDate  = varchar("desired_completion_date", 32).nullable()
    val projectLat             = double("project_lat").nullable()
    val projectLong            = double("project_long").nullable()
    val opportunityScore       = varchar("opportunity_score", 50).nullable()
    val progressPct            = integer("progress_pct").nullable()
    val lossReason             = text("loss_reason").nullable()
    val userId                 = varchar("user_id", 64).nullable()
    val createdAt              = timestamp("created_at").nullable()
    val updatedAt              = timestamp("updated_at").nullable()

    override val primaryKey = PrimaryKey(projectId)
}