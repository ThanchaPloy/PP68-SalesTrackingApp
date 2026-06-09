package com.pp68.backend.domain.entity

data class AppointmentChecklist(
    val appointmentId: String,
    val masterId: String,
    val isChecked: Boolean
)