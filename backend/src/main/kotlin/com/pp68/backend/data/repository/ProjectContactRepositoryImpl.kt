package com.pp68.backend.data.repository

import com.pp68.backend.data.database.tables.ProjectContactTable
import com.pp68.backend.domain.entity.ProjectContact
import com.pp68.backend.domain.repository.ProjectContactRepository
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class ProjectContactRepositoryImpl : ProjectContactRepository {

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }

    override suspend fun findByProjectId(projectId: String): List<ProjectContact> = dbQuery {
        ProjectContactTable.select { ProjectContactTable.projectId eq projectId }.map {
            ProjectContact(it[ProjectContactTable.projectId], it[ProjectContactTable.contactId])
        }
    }

    override suspend fun addContacts(contacts: List<ProjectContact>): Boolean = dbQuery {
        ProjectContactTable.batchInsert(contacts) { c ->
            this[ProjectContactTable.projectId] = c.projectId
            this[ProjectContactTable.contactId] = c.contactId
        }
        true
    }

    override suspend fun deleteByProjectId(projectId: String): Boolean = dbQuery {
        ProjectContactTable.deleteWhere { ProjectContactTable.projectId eq projectId } >= 0
    }
}