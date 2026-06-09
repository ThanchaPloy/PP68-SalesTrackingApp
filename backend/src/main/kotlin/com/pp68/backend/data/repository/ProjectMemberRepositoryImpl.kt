package com.pp68.backend.data.repository

import com.pp68.backend.data.database.tables.ProjectMemberTable
import com.pp68.backend.domain.entity.ProjectMember
import com.pp68.backend.domain.repository.ProjectMemberRepository
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class ProjectMemberRepositoryImpl : ProjectMemberRepository {

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }

    override suspend fun findByProjectId(projectId: String): List<ProjectMember> = dbQuery {
        ProjectMemberTable.select { ProjectMemberTable.projectId eq projectId }.map {
            ProjectMember(it[ProjectMemberTable.projectId], it[ProjectMemberTable.userId], it[ProjectMemberTable.salesRole])
        }
    }

    override suspend fun findByUserId(userId: String): List<ProjectMember> = dbQuery {
        ProjectMemberTable.select { ProjectMemberTable.userId eq userId }.map {
            ProjectMember(it[ProjectMemberTable.projectId], it[ProjectMemberTable.userId], it[ProjectMemberTable.salesRole])
        }
    }

    override suspend fun addMembers(members: List<ProjectMember>): List<ProjectMember> = dbQuery {
        ProjectMemberTable.batchInsert(members) { m ->
            this[ProjectMemberTable.projectId] = m.projectId
            this[ProjectMemberTable.userId]    = m.userId
            this[ProjectMemberTable.salesRole] = m.salesRole
        }
        members
    }

    override suspend fun deleteByProjectId(projectId: String): Boolean = dbQuery {
        ProjectMemberTable.deleteWhere { ProjectMemberTable.projectId eq projectId } >= 0
    }
}