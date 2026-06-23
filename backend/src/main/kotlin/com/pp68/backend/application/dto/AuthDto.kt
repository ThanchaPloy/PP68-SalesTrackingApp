package com.pp68.backend.application.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    @SerialName("emp_code") val empCode:  String,
    val password:                         String
)

@Serializable
data class LoginResponse(
    val token:                              String,
    @SerialName("user_id")   val userId:   String,
    @SerialName("full_name") val fullName: String,
    val role:                              String,
    @SerialName("branch_id") val branchId: String?,
    @SerialName("emp_type")  val empType:  String?
)

@Serializable
data class ChangePasswordRequest(
    @SerialName("emp_code")     val empCode:     String,
    @SerialName("old_password") val oldPassword: String,
    @SerialName("new_password") val newPassword: String
)

@Serializable
data class MessageResponse(val message: String)
