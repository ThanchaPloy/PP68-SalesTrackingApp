package com.pp68.backend.data.repository

import com.pp68.backend.data.database.tables.ActivityResultTable
import com.pp68.backend.domain.entity.ActivityResult
import com.pp68.backend.domain.repository.ActivityResultRepository
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class ActivityResultRepositoryImpl : ActivityResultRepository {

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }

    private fun ResultRow.toResult() = ActivityResult(
        resultId           = this[ActivityResultTable.resultId],
        activityId         = this[ActivityResultTable.activityId],
        projectId          = this[ActivityResultTable.projectId],
        createdBy          = this[ActivityResultTable.createdBy],
        reportDate         = this[ActivityResultTable.reportDate],
        newStatus          = this[ActivityResultTable.newStatus],
        opportunityScore   = this[ActivityResultTable.opportunityScore],
        dmInvolved         = this[ActivityResultTable.dmInvolved],
        isProposalSent     = this[ActivityResultTable.isProposalSent],
        proposalDate       = this[ActivityResultTable.proposalDate],
        competitorCount    = this[ActivityResultTable.competitorCount],
        responseSpeed      = this[ActivityResultTable.responseSpeed],
        dealPosition       = this[ActivityResultTable.dealPosition],
        previousSolution   = this[ActivityResultTable.previousSolution],
        counterpartyType   = this[ActivityResultTable.counterpartyType],
        summary            = this[ActivityResultTable.summary],
        photoUrl           = this[ActivityResultTable.photoUrl],
        photoTakenAt       = this[ActivityResultTable.photoTakenAt],
        photoLat           = this[ActivityResultTable.photoLat],
        photoLng           = this[ActivityResultTable.photoLng],
        photoDeviceModel   = this[ActivityResultTable.photoDeviceModel],
        lossReason         = this[ActivityResultTable.lossReason]
    )

    override suspend fun findByAppointmentId(appointmentId: String): ActivityResult? = dbQuery {
        ActivityResultTable.select { ActivityResultTable.activityId eq appointmentId }
            .singleOrNull()?.toResult()
    }

    override suspend fun findByUserId(userId: String, limit: Int): List<ActivityResult> = dbQuery {
        ActivityResultTable.select { ActivityResultTable.createdBy eq userId }.limit(limit).map { it.toResult() }
    }

    override suspend fun findById(resultId: String): ActivityResult? = dbQuery {
        ActivityResultTable.select { ActivityResultTable.resultId eq resultId }.singleOrNull()?.toResult()
    }

    override suspend fun create(result: ActivityResult): ActivityResult = dbQuery {
        ActivityResultTable.insert { stmt -> stmt.fromEntity(result) }
        ActivityResultTable.select { ActivityResultTable.resultId eq result.resultId }.single().toResult()
    }

    override suspend fun upsert(result: ActivityResult): ActivityResult = dbQuery {
        val existing = ActivityResultTable.select {
            ActivityResultTable.activityId eq (result.activityId ?: "")
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

    override suspend fun update(resultId: String, updates: Map<String, Any?>): ActivityResult? = dbQuery {
        ActivityResultTable.update({ ActivityResultTable.resultId eq resultId }) { stmt ->
            updates["new_status"]?.let         { v -> stmt[ActivityResultTable.newStatus]       = v as String }
            updates["opportunity_score"]?.let  { v -> stmt[ActivityResultTable.opportunityScore] = v as String }
            updates["photo_url"]?.let          { v -> stmt[ActivityResultTable.photoUrl]        = v as String }
            updates["loss_reason"]?.let        { v -> stmt[ActivityResultTable.lossReason]      = v as String }
            updates["note_summary"]?.let       { v -> stmt[ActivityResultTable.summary]         = v as String }
        }
        ActivityResultTable.select { ActivityResultTable.resultId eq resultId }.singleOrNull()?.toResult()
    }

    private fun UpdateStatement.fromEntityUpdate(r: ActivityResult) {
        this[ActivityResultTable.newStatus]       = r.newStatus
        this[ActivityResultTable.opportunityScore] = r.opportunityScore
        this[ActivityResultTable.dmInvolved]      = r.dmInvolved
        this[ActivityResultTable.isProposalSent]  = r.isProposalSent
        this[ActivityResultTable.proposalDate]    = r.proposalDate
        this[ActivityResultTable.competitorCount] = r.competitorCount
        this[ActivityResultTable.responseSpeed]   = r.responseSpeed
        this[ActivityResultTable.dealPosition]    = r.dealPosition
        this[ActivityResultTable.previousSolution] = r.previousSolution
        this[ActivityResultTable.counterpartyType] = r.counterpartyType
        this[ActivityResultTable.summary]          = r.summary
        this[ActivityResultTable.photoUrl]         = r.photoUrl
        this[ActivityResultTable.lossReason]       = r.lossReason
    }

    private fun InsertStatement<*>.fromEntity(r: ActivityResult) {
        this[ActivityResultTable.resultId]          = r.resultId
        this[ActivityResultTable.activityId]        = r.activityId
        this[ActivityResultTable.projectId]         = r.projectId
        this[ActivityResultTable.createdBy]         = r.createdBy
        this[ActivityResultTable.reportDate]        = r.reportDate
        this[ActivityResultTable.newStatus]         = r.newStatus
        this[ActivityResultTable.opportunityScore]  = r.opportunityScore
        this[ActivityResultTable.dmInvolved]        = r.dmInvolved
        this[ActivityResultTable.isProposalSent]    = r.isProposalSent
        this[ActivityResultTable.proposalDate]      = r.proposalDate
        this[ActivityResultTable.competitorCount]   = r.competitorCount
        this[ActivityResultTable.responseSpeed]     = r.responseSpeed
        this[ActivityResultTable.dealPosition]      = r.dealPosition
        this[ActivityResultTable.previousSolution]  = r.previousSolution
        this[ActivityResultTable.counterpartyType]  = r.counterpartyType
        this[ActivityResultTable.summary]           = r.summary
        this[ActivityResultTable.photoUrl]          = r.photoUrl
        this[ActivityResultTable.photoTakenAt]      = r.photoTakenAt
        this[ActivityResultTable.photoLat]          = r.photoLat
        this[ActivityResultTable.photoLng]          = r.photoLng
        this[ActivityResultTable.photoDeviceModel]  = r.photoDeviceModel
        this[ActivityResultTable.lossReason]        = r.lossReason
    }
}