package com.pp68.backend.data.repository

import com.pp68.backend.data.database.tables.ActivityResultTable
import com.pp68.backend.domain.entity.ActivityResult
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.statements.UpdateStatement

class ActivityResultRepositoryImpl {

    private fun ResultRow.toResult() = ActivityResult(
        resultId         = this[ActivityResultTable.resultId],
        appointmentId    = this[ActivityResultTable.appointmentId],
        projectId        = this[ActivityResultTable.projectId],
        createdBy        = this[ActivityResultTable.createdBy],
        reportDate       = this[ActivityResultTable.reportDate],
        newStatus        = this[ActivityResultTable.newStatus],
        opportunityScore = this[ActivityResultTable.opportunityScore],
        dmInvolved       = this[ActivityResultTable.dmInvolved],
        dmContactId      = this[ActivityResultTable.dmContactId],
        isProposalSent   = this[ActivityResultTable.isProposalSent],
        proposalDate     = this[ActivityResultTable.proposalDate],
        competitorCount  = this[ActivityResultTable.competitorCount],
        responseSpeed    = this[ActivityResultTable.responseSpeed],
        dealPosition     = this[ActivityResultTable.dealPosition],
        currentSolution  = this[ActivityResultTable.currentSolution],
        counterpartyType = this[ActivityResultTable.counterpartyType],
        summary          = this[ActivityResultTable.summary],
        createdAt        = this[ActivityResultTable.createdAt]?.toString(),
        photoUrl         = this[ActivityResultTable.photoUrl],
        photoTakenAt     = this[ActivityResultTable.photoTakenAt],
        photoLat         = this[ActivityResultTable.photoLat],
        photoLng         = this[ActivityResultTable.photoLng],
        photoDeviceModel = this[ActivityResultTable.photoDeviceModel]
    )

    suspend fun findByAppointmentId(appointmentId: String): ActivityResult? = dbQuery {
        ActivityResultTable.select { ActivityResultTable.appointmentId eq appointmentId }
            .singleOrNull()?.toResult()
    }

    suspend fun findByUserId(userId: String, limit: Int): List<ActivityResult> = dbQuery {
        ActivityResultTable.select { ActivityResultTable.createdBy eq userId }.limit(limit).map { it.toResult() }
    }

    suspend fun findById(resultId: String): ActivityResult? = dbQuery {
        ActivityResultTable.select { ActivityResultTable.resultId eq resultId }.singleOrNull()?.toResult()
    }

    suspend fun create(result: ActivityResult): ActivityResult = dbQuery {
        ActivityResultTable.insert { stmt -> stmt.fromEntity(result) }
        ActivityResultTable.select { ActivityResultTable.resultId eq result.resultId }.single().toResult()
    }

    suspend fun upsert(result: ActivityResult): ActivityResult = dbQuery {
        val existing = ActivityResultTable.select {
            ActivityResultTable.appointmentId eq (result.appointmentId ?: "")
        }.singleOrNull()

        if (existing == null) {
            ActivityResultTable.insert { stmt -> stmt.fromEntity(result) }
        } else {
            ActivityResultTable.update({ ActivityResultTable.resultId eq existing[ActivityResultTable.resultId] }) { stmt ->
                stmt.fromEntityUpdate(result)
            }
        }
        ActivityResultTable.select { ActivityResultTable.resultId eq result.resultId }.single().toResult()
    }

    suspend fun update(resultId: String, updates: Map<String, Any?>): ActivityResult? = dbQuery {
        ActivityResultTable.update({ ActivityResultTable.resultId eq resultId }) { stmt ->
            updates["new_status"]?.let        { v -> stmt[ActivityResultTable.newStatus]       = v as String }
            updates["opportunity_score"]?.let { v -> stmt[ActivityResultTable.opportunityScore] = v as String }
            updates["photo_url"]?.let         { v -> stmt[ActivityResultTable.photoUrl]        = v as String }
            updates["note_summary"]?.let      { v -> stmt[ActivityResultTable.summary]         = v as String }
            updates["dm_contact_id"]?.let     { v -> stmt[ActivityResultTable.dmContactId]     = v as String }
        }
        ActivityResultTable.select { ActivityResultTable.resultId eq resultId }.singleOrNull()?.toResult()
    }

    private fun UpdateBuilder<*>.applyMutableFields(r: ActivityResult) {
        this[ActivityResultTable.newStatus]        = r.newStatus
        this[ActivityResultTable.opportunityScore] = r.opportunityScore
        this[ActivityResultTable.dmInvolved]       = r.dmInvolved
        this[ActivityResultTable.dmContactId]      = r.dmContactId
        this[ActivityResultTable.isProposalSent]   = r.isProposalSent
        this[ActivityResultTable.proposalDate]     = r.proposalDate
        this[ActivityResultTable.competitorCount]  = r.competitorCount
        this[ActivityResultTable.responseSpeed]    = r.responseSpeed
        this[ActivityResultTable.dealPosition]     = r.dealPosition
        this[ActivityResultTable.currentSolution]  = r.currentSolution
        this[ActivityResultTable.counterpartyType] = r.counterpartyType
        this[ActivityResultTable.summary]          = r.summary
        this[ActivityResultTable.photoUrl]         = r.photoUrl
    }

    private fun UpdateStatement.fromEntityUpdate(r: ActivityResult) = applyMutableFields(r)

    private fun InsertStatement<*>.fromEntity(r: ActivityResult) {
        this[ActivityResultTable.resultId]         = r.resultId
        this[ActivityResultTable.appointmentId]    = r.appointmentId
        this[ActivityResultTable.projectId]        = r.projectId
        this[ActivityResultTable.createdBy]        = r.createdBy
        this[ActivityResultTable.reportDate]       = r.reportDate
        applyMutableFields(r)
        this[ActivityResultTable.photoTakenAt]     = r.photoTakenAt
        this[ActivityResultTable.photoLat]         = r.photoLat
        this[ActivityResultTable.photoLng]         = r.photoLng
        this[ActivityResultTable.photoDeviceModel] = r.photoDeviceModel
    }
}
