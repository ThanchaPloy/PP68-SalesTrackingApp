package com.pp68.backend.domain.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AppointmentChecklist(
    @SerialName("appointment_id") val appointmentId: String,
    @SerialName("master_id")      val masterId:      String,
    @SerialName("is_checked")     val isChecked:     Boolean
)