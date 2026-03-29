package com.example.pp68_salestrackingapp.data.model

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    @SerializedName("email") val email: String,
    @SerializedName("password_hash") val passwordHash: String
)

// แก้ให้ตรงกับโครงสร้าง JSON จริงๆ
data class LoginResponse(
    @SerializedName("token")
    val token: String,

    @SerializedName("user_id")
    val userId: String,

    @SerializedName("role")
    val role: String
)

// สร้างกล่องมารับข้อมูลที่อยู่ข้างใน "user"
data class UserInfo(
    @SerializedName("userId") val userId: String,
    @SerializedName("fullName") val fullName: String,
    @SerializedName("role") val role: String,
    @SerializedName("email") val email: String
)