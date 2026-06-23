package com.pp68.backend.domain.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProjectMember(
    @SerialName("project_id")  val projectId:  String,
    @SerialName("user_id")     val userId:     String,
    @SerialName("sales_role")  val salesRole:  String?
)