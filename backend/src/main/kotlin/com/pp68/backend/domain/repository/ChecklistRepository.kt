package com.pp68.backend.domain.repository

import com.pp68.backend.domain.entity.AppointmentChecklist

interface ChecklistRepository {
    suspend fun findByAppointmentId(appointmentId: String): List<AppointmentChecklist>
    suspend fun create(items: List<AppointmentChecklist>): List<AppointmentChecklist>
    suspend fun update(appointmentId: String, masterId: String, isChecked: Boolean): AppointmentChecklist?
}