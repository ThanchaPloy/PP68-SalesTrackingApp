package com.example.pp68_salestrackingapp.data.model

import com.google.gson.annotations.SerializedName

data class CompleteInitialSetupRequest(
    @SerializedName("current_password") val currentPassword: String,
    @SerializedName("new_password") val newPassword: String,
    @SerializedName("phone_number") val phoneNumber: String? = null,
    @SerializedName("phone_notice_version") val phoneNoticeVersion: String? = null
)

data class InitialSetupInfo(
    val required: Boolean,
    val phoneRequired: Boolean,
    val phoneNoticeVersion: String?
)
