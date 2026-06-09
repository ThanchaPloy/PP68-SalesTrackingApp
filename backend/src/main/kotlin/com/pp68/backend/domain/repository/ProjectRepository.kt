package com.pp68.backend.domain.repository

import com.pp68.backend.domain.entity.Project

interface ProjectRepository {
    suspend fun findByIds(projectIds: List<String>): List<Project>
    suspend fun findById(projectId: String): Project?
    suspend fun create(project: Project): Project
    suspend fun update(projectId: String, updates: Map<String, Any?>): Project?
    suspend fun delete(projectId: String): Boolean
    suspend fun deleteByCustId(custId: String): Boolean
}