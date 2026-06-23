package com.pp68.backend.domain.usecase

import com.pp68.backend.domain.entity.Project
import com.pp68.backend.domain.entity.ProjectContact
import com.pp68.backend.domain.entity.ProjectMember
import com.pp68.backend.domain.exception.NotFoundException
import com.pp68.backend.data.repository.ProjectContactRepositoryImpl
import com.pp68.backend.data.repository.ProjectMemberRepositoryImpl
import com.pp68.backend.data.repository.ProjectRepositoryImpl

class ProjectUseCase(
    private val projectRepository: ProjectRepositoryImpl,
    private val memberRepository: ProjectMemberRepositoryImpl,
    private val contactRepository: ProjectContactRepositoryImpl
) {

    suspend fun findById(projectId: String): Project =
        projectRepository.findById(projectId) ?: throw NotFoundException("Project not found: $projectId")

    suspend fun findByIds(projectIds: List<String>): List<Project> =
        projectRepository.findByIds(projectIds)

    suspend fun create(project: Project): Project =
        projectRepository.create(project)

    suspend fun update(projectId: String, updates: Map<String, String?>): Project =
        projectRepository.update(projectId, updates) ?: throw NotFoundException("Project not found: $projectId")

    suspend fun delete(projectId: String) {
        if (!projectRepository.delete(projectId)) throw NotFoundException("Project not found: $projectId")
    }

    suspend fun deleteByCustId(custId: String) =
        projectRepository.deleteByCustId(custId)

    suspend fun getMembersByProjectId(projectId: String): List<ProjectMember> =
        memberRepository.findByProjectId(projectId)

    suspend fun getMembersByUserId(userId: String): List<ProjectMember> =
        memberRepository.findByUserId(userId)

    suspend fun addMembers(members: List<ProjectMember>): List<ProjectMember> =
        memberRepository.addMembers(members)

    suspend fun deleteMembersByProjectId(projectId: String) =
        memberRepository.deleteByProjectId(projectId)

    suspend fun getContactsByProjectId(projectId: String): List<ProjectContact> =
        contactRepository.findByProjectId(projectId)

    suspend fun addContacts(contacts: List<ProjectContact>) =
        contactRepository.addContacts(contacts)

    suspend fun deleteContactsByProjectId(projectId: String) =
        contactRepository.deleteByProjectId(projectId)
}