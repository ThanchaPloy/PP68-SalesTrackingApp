package com.example.pp68_salestrackingapp.data.model

import com.google.gson.annotations.SerializedName

data class ProjectMemberDto(
    @SerializedName("project_code")    val projectId:  String,
    @SerializedName("emp_code")        val userId:     String? = null,
    @SerializedName("sales_role")      val saleRole:   String? = null
)
