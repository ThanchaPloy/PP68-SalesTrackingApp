package com.pp68.backend.data.repository

import com.pp68.backend.data.database.tables.ProjectContactTable
import com.pp68.backend.domain.entity.ProjectContact
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

class ProjectContactRepositoryImpl {

    suspend fun findByProjectId(projectId: String): List<ProjectContact> = dbQuery {
        ProjectContactTable.select { ProjectContactTable.projectId eq projectId }.map {
            ProjectContact(it[ProjectContactTable.projectId], it[ProjectContactTable.contactId])
        }
    }

    suspend fun addContacts(contacts: List<ProjectContact>): Boolean = dbQuery {
        ProjectContactTable.batchInsert(contacts) { c ->
            this[ProjectContactTable.projectId] = c.projectId
            this[ProjectContactTable.contactId] = c.contactId
        }
        true
    }

    suspend fun deleteByProjectId(projectId: String): Boolean = dbQuery {
        ProjectContactTable.deleteWhere { ProjectContactTable.projectId eq projectId } >= 0
    }
}