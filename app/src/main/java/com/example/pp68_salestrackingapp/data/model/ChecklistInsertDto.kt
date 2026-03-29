package com.example.pp68_salestrackingapp.data.model


import com.google.gson.annotations.SerializedName

data class ChecklistInsertDto(
    @SerializedName("appointment_id") val appointmentId: String,
    @SerializedName("master_id")      val masterId:      Int,
    @SerializedName("is_done")        val isDone:        Boolean = false
)