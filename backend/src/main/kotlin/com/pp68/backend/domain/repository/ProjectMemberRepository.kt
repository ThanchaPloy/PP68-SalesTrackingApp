package com.pp68.backend.domain.repository

import com.pp68.backend.domain.entity.ProjectMember

interface ProjectMemberRepository {
    suspend fun findByProjectId(projectId: String): List<ProjectMember>
    suspend fun findByUserId(userId: String): List<ProjectMember>
    suspend fun addMembers(members: List<ProjectMember>): List<ProjectMember>
    suspend fun deleteByProjectId(projectId: String): Boolean
}