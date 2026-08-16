package com.example.pp68_salestrackingapp.data.model

import com.google.gson.annotations.SerializedName

data class ProjectMemberInsertDto(
    @SerializedName("project_code") val projectId: String,
    @SerializedName("user_id")    val userId:    String,
    @SerializedName("role")       val saleRole:  String = "support"
)

