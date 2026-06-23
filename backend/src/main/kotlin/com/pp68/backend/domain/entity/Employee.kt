package com.pp68.backend.domain.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class Employee(
    @SerialName("emp_code")      val empCode:     String,
    @SerialName("emp_name")      val empName:     String?  = null,
    @SerialName("emp_post_code") val empPostCode: String?  = null,
    @SerialName("emp_post")      val empPost:     String?  = null,
    @SerialName("emp_brch_code") val empBrchCode: String?  = null,
    @SerialName("emp_brch_name") val empBrchName: String?  = null,
    @SerialName("stat")          val stat:        String?  = null,
    @SerialName("create_date")   val createDate:  String?  = null,
    @SerialName("updated_at")    val updatedAt:   String?  = null,
    @SerialName("emp_type")      val empType:     String?  = null,
    @Transient val password: String? = null
)
