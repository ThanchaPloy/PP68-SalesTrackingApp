package com.pp68.backend.application.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
    @SerialName("fcm_token") val fcmToken: String? = null
)

@Serializable
data class LoginResponse(
    val token: String,
    @SerialName("user_id")   val userId:   String,
    @SerialName("full_name") val fullName: String,
    val role:                              String,
    @SerialName("branch_id") val branchId: String?
)

@Serializable
data class RegisterRequest(
    val email:                             String,
    val password:                          String,
    @SerialName("full_name") val fullName: String,
    @SerialName("branch_id") val branchId: String? = null,
    val role:                              String   = "sales",
    @SerialName("phone_number") val phoneNumber: String? = null
)

@Serializable
data class ChangePasswordRequest(
    @SerialName("user_id")      val userId:      String,
    @SerialName("old_password") val oldPassword: String,
    @SerialName("new_password") val newPassword: String
)