package com.example.pp68_salestrackingapp.data.model

import com.google.gson.annotations.SerializedName


data class UserDto(
    @SerializedName("user_id")      val userId:      String,
    @SerializedName("full_name")    val fullName:    String?,
    @SerializedName("branch_id")    val branchId:    String?,
    @SerializedName("role")         val role:        String?,
    @SerializedName("email")        val email:       String?,
    @SerializedName("phone_number") val phoneNumber: String?  // ✅ เพิ่ม
)