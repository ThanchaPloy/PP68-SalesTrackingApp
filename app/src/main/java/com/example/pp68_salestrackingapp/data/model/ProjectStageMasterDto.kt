package com.example.pp68_salestrackingapp.data.model

import com.google.gson.annotations.SerializedName

data class ProjectStageMasterDto(
    @SerializedName("code")            val code: String,
    @SerializedName("label")           val label: String,
    @SerializedName("sequence")        val sequence: Int,
    @SerializedName("probability_pct") val probabilityPct: Int,
    @SerializedName("is_closed")       val isClosed: Boolean = false,
    @SerializedName("is_won")          val isWon: Boolean = false
)
