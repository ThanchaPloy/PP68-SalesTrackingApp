package com.pp68.backend.domain.repository

import com.pp68.backend.domain.entity.ActivityResult

interface ActivityResultRepository {
    suspend fun findByAppointmentId(appointmentId: String): ActivityResult?
    suspend fun findByUserId(userId: String, limit: Int = 1000): List<ActivityResult>
    suspend fun findById(resultId: String): ActivityResult?
    suspend fun create(result: ActivityResult): ActivityResult
    suspend fun upsert(result: ActivityResult): ActivityResult
    suspend fun update(resultId: String, updates: Map<String, Any?>): ActivityResult?
}