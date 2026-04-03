package com.example.pp68_salestrackingapp.data.model

import com.google.gson.annotations.SerializedName

data class ProjectMemberDto(
    @SerializedName("project_id")     val projectId:     String,
    @SerializedName("user_id")        val userId:        String? = null,
    @SerializedName("sale_role")      val saleRole:      String? = null,
    @SerializedName("project_number") val projectNumber: String? = null
)
