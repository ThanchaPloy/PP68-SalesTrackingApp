package com.example.pp68_salestrackingapp.data.model

import com.google.gson.annotations.SerializedName

data class ChangePasswordRequest(
    @SerializedName("user_id") val userId: String,
    @SerializedName("old_password") val oldPassword: String,
    @SerializedName("new_password") val newPassword: String
)

data class ChangePasswordResponse(
    @SerializedName("message") val message: String,
    @SerializedName("error")   val error:   String? = null
)