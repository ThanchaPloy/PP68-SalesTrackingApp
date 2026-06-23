package com.pp68.backend.domain.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProjectContact(
    @SerialName("project_id") val projectId: String,
    @SerialName("contact_id") val contactId: String
)