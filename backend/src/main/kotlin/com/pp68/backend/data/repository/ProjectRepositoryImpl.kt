package com.pp68.backend.data.repository

import com.pp68.backend.data.database.tables.ProjectTable
import com.pp68.backend.domain.entity.Project
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

class ProjectRepositoryImpl {

    private fun ResultRow.toProject() = Project(
        projectCode  = this[ProjectTable.projectCode],
        projectName  = this[ProjectTable.projectName],
        customerCode = this[ProjectTable.customerCode],
        customerName = this[ProjectTable.customerName],
        branchCode   = this[ProjectTable.branchCode],
        requestBy    = this[ProjectTable.requestBy],
        remark       = this[ProjectTable.remark],
        updatedAt    = this[ProjectTable.updatedAt]?.toString()
    )

    suspend fun findByIds(projectIds: List<String>): List<Project> = dbQuery {
        ProjectTable.select { ProjectTable.projectCode inList projectIds }.map { it.toProject() }
    }

    suspend fun findById(projectId: String): Project? = dbQuery {
        ProjectTable.select { ProjectTable.projectCode eq projectId }.singleOrNull()?.toProject()
    }

    suspend fun create(project: Project): Project = dbQuery {
        ProjectTable.insert {
            it[projectCode]  = project.projectCode
            it[projectName]  = project.projectName
            it[customerCode] = project.customerCode
            it[customerName] = project.customerName
            it[branchCode]   = project.branchCode
            it[requestBy]    = project.requestBy
            it[remark]       = project.remark
        }
        ProjectTable.select { ProjectTable.projectCode eq project.projectCode }.single().toProject()
    }

    suspend fun update(projectId: String, updates: Map<String, Any?>): Project? = dbQuery {
        ProjectTable.update({ ProjectTable.projectCode eq projectId }) { stmt ->
            updates["project_name"]?.let  { v -> stmt[projectName]  = v as String }
            updates["customer_code"]?.let { v -> stmt[customerCode] = v as String }
            updates["customer_name"]?.let { v -> stmt[customerName] = v as String }
            updates["branch_code"]?.let   { v -> stmt[branchCode]   = v as String }
            updates["request_by"]?.let    { v -> stmt[requestBy]    = v as String }
            updates["remark"]?.let        { v -> stmt[remark]       = v as String }
        }
        ProjectTable.select { ProjectTable.projectCode eq projectId }.singleOrNull()?.toProject()
    }

    suspend fun delete(projectId: String): Boolean = dbQuery {
        ProjectTable.deleteWhere { ProjectTable.projectCode eq projectId } > 0
    }

    suspend fun deleteByCustId(custId: String): Boolean = dbQuery {
        ProjectTable.deleteWhere { ProjectTable.customerCode eq custId } > 0
    }
}
