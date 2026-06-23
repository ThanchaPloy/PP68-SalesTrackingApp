package com.example.pp68_salestrackingapp.data.model

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    @SerializedName("emp_code") val empCode: String,
    @SerializedName("password") val password: String
)

data class LoginResponse(
    @SerializedName("token")     val token:    String,
    @SerializedName("user_id")   val userId:   String,
    @SerializedName("full_name") val fullName: String?,
    @SerializedName("role")      val role:     String,
    @SerializedName("branch_id") val branchId: String?,
    @SerializedName("emp_type")  val empType:  String?  = null
)

// สร้างกล่องมารับข้อมูลที่อยู่ข้างใน "user"
data class UserInfo(
    @SerializedName("userId") val userId: String,
    @SerializedName("fullName") val fullName: String,
    @SerializedName("role") val role: String,
    @SerializedName("email") val email: String
)