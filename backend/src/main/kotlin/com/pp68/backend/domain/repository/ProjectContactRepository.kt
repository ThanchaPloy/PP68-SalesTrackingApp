package com.pp68.backend.domain.repository

import com.pp68.backend.domain.entity.ProjectContact

interface ProjectContactRepository {
    suspend fun findByProjectId(projectId: String): List<ProjectContact>
    suspend fun addContacts(contacts: List<ProjectContact>): Boolean
    suspend fun deleteByProjectId(projectId: String): Boolean
}