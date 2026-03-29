package com.example.pp68_salestrackingapp.data.model

import com.google.gson.annotations.SerializedName

data class ActivityMasterDto(
    @SerializedName("master_id") val masterId: Int,
    @SerializedName("category")  val category: String,
    @SerializedName("act_name")  val actName:  String
)
