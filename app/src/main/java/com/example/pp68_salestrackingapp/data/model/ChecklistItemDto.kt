package com.example.pp68_salestrackingapp.data.model

import com.google.gson.annotations.SerializedName

data class ChecklistItemDto(
    @SerializedName("master_id") val masterId: Int,
    @SerializedName("is_done")   val isDone:   Boolean = false
)