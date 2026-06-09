package com.pp68.backend.data.repository

import com.pp68.backend.data.database.tables.ProjectTable
import com.pp68.backend.domain.entity.Project
import com.pp68.backend.domain.repository.ProjectRepository
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant

class ProjectRepositoryImpl : ProjectRepository {

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }

    private fun ResultRow.toProject() = Project(
        projectId             = this[ProjectTable.projectId],
        custId                = this[ProjectTable.custId],
        branchId              = this[ProjectTable.branchId],
        billingBranchId       = this[ProjectTable.billingBranchId],
        projectName           = this[ProjectTable.projectName],
        expectedValue         = this[ProjectTable.expectedValue],
        projectStatus         = this[ProjectTable.projectStatus],
        startDate             = this[ProjectTable.startDate],
        closingDate           = this[ProjectTable.closingDate],
        desiredCompletionDate = this[ProjectTable.desiredCompletionDate],
        projectLat            = this[ProjectTable.projectLat],
        projectLong           = this[ProjectTable.projectLong],
        opportunityScore      = this[ProjectTable.opportunityScore],
        progressPct           = this[ProjectTable.progressPct],
        lossReason            = this[ProjectTable.lossReason],
        userId                = this[ProjectTable.userId],
        createdAt             = this[ProjectTable.createdAt]?.toString(),
        updatedAt             = this[ProjectTable.updatedAt]?.toString()
    )

    override suspend fun findByIds(projectIds: List<String>): List<Project> = dbQuery {
        ProjectTable.select { ProjectTable.projectId inList projectIds }.map { it.toProject() }
    }

    override suspend fun findById(projectId: String): Project? = dbQuery {
        ProjectTable.select { ProjectTable.projectId eq projectId }.singleOrNull()?.toProject()
    }

    override suspend fun create(project: Project): Project = dbQuery {
        ProjectTable.insert {
            it[projectId]             = project.projectId
            it[custId]                = project.custId
            it[branchId]              = project.branchId
            it[billingBranchId]       = project.billingBranchId
            it[projectName]           = project.projectName
            it[expectedValue]         = project.expectedValue
            it[projectStatus]         = project.projectStatus
            it[startDate]             = project.startDate
            it[closingDate]           = project.closingDate
            it[desiredCompletionDate] = project.desiredCompletionDate
            it[projectLat]            = project.projectLat
            it[projectLong]           = project.projectLong
            it[opportunityScore]      = project.opportunityScore
            it[progressPct]           = project.progressPct
            it[lossReason]            = project.lossReason
            it[userId]                = project.userId
            it[createdAt]             = Instant.now()
            it[updatedAt]             = Instant.now()
        }
        ProjectTable.select { ProjectTable.projectId eq project.projectId }.single().toProject()
    }

    override suspend fun update(projectId: String, updates: Map<String, Any?>): Project? = dbQuery {
        ProjectTable.update({ ProjectTable.projectId eq projectId }) { stmt ->
            updates["project_name"]?.let            { v -> stmt[projectName]           = v as String }
            updates["project_status"]?.let          { v -> stmt[projectStatus]         = v as String }
            updates["opportunity_score"]?.let       { v -> stmt[opportunityScore]      = v as String }
            updates["progress_pct"]?.let            { v -> stmt[progressPct]           = (v as Number).toInt() }
            updates["expected_value"]?.let          { v -> stmt[expectedValue]         = (v as Number).toDouble() }
            updates["closing_date"]?.let            { v -> stmt[closingDate]           = v as String }
            updates["loss_reason"]?.let             { v -> stmt[lossReason]            = v as String }
            updates["desired_completion_date"]?.let { v -> stmt[desiredCompletionDate] = v as String }
            stmt[updatedAt] = Instant.now()
        }
        ProjectTable.select { ProjectTable.projectId eq projectId }.singleOrNull()?.toProject()
    }

    override suspend fun delete(projectId: String): Boolean = dbQuery {
        ProjectTable.deleteWhere { ProjectTable.projectId eq projectId } > 0
    }

    override suspend fun deleteByCustId(custId: String): Boolean = dbQuery {
        ProjectTable.deleteWhere { ProjectTable.custId eq custId } > 0
    }
}