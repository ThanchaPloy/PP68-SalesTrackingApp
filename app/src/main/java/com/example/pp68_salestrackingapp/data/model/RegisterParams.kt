package com.example.pp68_salestrackingapp.data.model

import com.google.gson.annotations.SerializedName

data class RegisterRequest(
    @SerializedName("email") val email: String,
    @SerializedName("password_hash") val passwordHash: String,
    @SerializedName("full_name") val fullName: String,
    @SerializedName("branch_id") val branchId: String,
    @SerializedName("role") val role: String = "Sales"
)

data class RegisterResponse(
    @SerializedName("user_id") val userId: String,
    @SerializedName("message") val message: String? = null
)
