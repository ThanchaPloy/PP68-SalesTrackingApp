package com.example.pp68_salestrackingapp.data.model

import com.google.gson.annotations.SerializedName

data class LossReasonMasterDto(
    @SerializedName("code")     val code: String,
    @SerializedName("label")    val label: String,
    @SerializedName("sequence") val sequence: Int
)
