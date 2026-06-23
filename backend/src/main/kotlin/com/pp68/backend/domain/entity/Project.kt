package com.pp68.backend.domain.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Project(
    @SerialName("project_code")   val projectCode:   String,
    @SerialName("project_name")   val projectName:   String?  = null,
    @SerialName("customer_code")  val customerCode:  String?  = null,
    @SerialName("customer_name")  val customerName:  String?  = null,
    @SerialName("branch_code")    val branchCode:    String?  = null,
    @SerialName("request_by")     val requestBy:     String?  = null,
    @SerialName("remark")         val remark:        String?  = null,
    @SerialName("updated_at")     val updatedAt:     String?  = null
)
