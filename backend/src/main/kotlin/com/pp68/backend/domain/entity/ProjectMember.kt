package com.pp68.backend.domain.entity

data class ProjectMember(
    val projectId: String,
    val userId: String,
    val salesRole: String?
)