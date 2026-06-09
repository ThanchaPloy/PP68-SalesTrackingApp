package com.pp68.backend.domain.repository

import com.pp68.backend.domain.entity.Appointment

interface AppointmentRepository {
    suspend fun findByUserId(userId: String, limit: Int = 1000): List<Appointment>
    suspend fun findById(appointmentId: String): Appointment?
    suspend fun create(appointment: Appointment): Appointment
    suspend fun update(appointmentId: String, updates: Map<String, Any?>): Appointment?
    suspend fun delete(appointmentId: String): Boolean
    suspend fun deleteByCustId(custId: String): Boolean
}