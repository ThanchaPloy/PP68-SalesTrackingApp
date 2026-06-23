package com.pp68.backend.domain.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AppointmentContact(
    @SerialName("appointment_id") val appointmentId: String,
    @SerialName("contact_id")     val contactId:     String
)