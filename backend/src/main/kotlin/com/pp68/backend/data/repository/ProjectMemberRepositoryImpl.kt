package com.pp68.backend.data.repository

import com.pp68.backend.data.database.tables.ProjectMemberTable
import com.pp68.backend.domain.entity.ProjectMember
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

class ProjectMemberRepositoryImpl {

    suspend fun findByProjectId(projectId: String): List<ProjectMember> = dbQuery {
        ProjectMemberTable.select { ProjectMemberTable.projectId eq projectId }.map {
            ProjectMember(it[ProjectMemberTable.projectId], it[ProjectMemberTable.userId], it[ProjectMemberTable.salesRole])
        }
    }

    suspend fun findByUserId(userId: String): List<ProjectMember> = dbQuery {
        ProjectMemberTable.select { ProjectMemberTable.userId eq userId }.map {
            ProjectMember(it[ProjectMemberTable.projectId], it[ProjectMemberTable.userId], it[ProjectMemberTable.salesRole])
        }
    }

    suspend fun addMembers(members: List<ProjectMember>): List<ProjectMember> = dbQuery {
        ProjectMemberTable.batchInsert(members) { m ->
            this[ProjectMemberTable.projectId] = m.projectId
            this[ProjectMemberTable.userId]    = m.userId
            this[ProjectMemberTable.salesRole] = m.salesRole
        }
        members
    }

    suspend fun deleteByProjectId(projectId: String): Boolean = dbQuery {
        ProjectMemberTable.deleteWhere { ProjectMemberTable.projectId eq projectId } >= 0
    }
}