package com.example.pp68_salestrackingapp.data.model

import com.google.gson.annotations.SerializedName


data class UserDto(
    @SerializedName("user_id")      val userId:      String,
    @SerializedName("full_name")    val fullName:    String? = null,
    @SerializedName("branch_id")    val branchId:    String? = null,
    @SerializedName("role")         val role:        String? = null,
    @SerializedName("email")        val email:       String? = null,
    @SerializedName("phone_number") val phoneNumber: String? = null
)
