package com.pp68.backend.domain.usecase

import com.pp68.backend.domain.entity.ActivityMaster
import com.pp68.backend.domain.entity.ActivityResult
import com.pp68.backend.domain.entity.Appointment
import com.pp68.backend.domain.entity.AppointmentChecklist
import com.pp68.backend.domain.exception.NotFoundException
import com.pp68.backend.data.repository.ActivityMasterRepositoryImpl
import com.pp68.backend.data.repository.ActivityResultRepositoryImpl
import com.pp68.backend.data.repository.AppointmentRepositoryImpl
import com.pp68.backend.data.repository.ChecklistRepositoryImpl

class AppointmentUseCase(
    private val appointmentRepository: AppointmentRepositoryImpl,
    private val resultRepository: ActivityResultRepositoryImpl,
    private val masterRepository: ActivityMasterRepositoryImpl,
    private val checklistRepository: ChecklistRepositoryImpl
) {

    suspend fun findById(appointmentId: String): Appointment =
        appointmentRepository.findById(appointmentId)
            ?: throw NotFoundException("Appointment not found: $appointmentId")

    suspend fun findByUserId(userId: String, limit: Int = 1000): List<Appointment> =
        appointmentRepository.findByUserId(userId, limit)

    suspend fun create(appointment: Appointment): Appointment =
        appointmentRepository.create(appointment)

    suspend fun update(appointmentId: String, updates: Map<String, String?>): Appointment =
        appointmentRepository.update(appointmentId, updates)
            ?: throw NotFoundException("Appointment not found: $appointmentId")

    suspend fun delete(appointmentId: String) {
        if (!appointmentRepository.delete(appointmentId))
            throw NotFoundException("Appointment not found: $appointmentId")
    }

    suspend fun deleteByCustId(custId: String) =
        appointmentRepository.deleteByCustId(custId)

    suspend fun getActivityMasters(activeOnly: Boolean): List<ActivityMaster> =
        if (activeOnly) masterRepository.findActive() else masterRepository.findAll()

    suspend fun findResultById(resultId: String): ActivityResult? =
        resultRepository.findById(resultId)

    suspend fun findResultByAppointmentId(appointmentId: String): ActivityResult? =
        resultRepository.findByAppointmentId(appointmentId)

    suspend fun findResultsByUserId(userId: String, limit: Int = 1000): List<ActivityResult> =
        resultRepository.findByUserId(userId, limit)

    suspend fun saveResult(result: ActivityResult, upsert: Boolean): ActivityResult =
        if (upsert) resultRepository.upsert(result) else resultRepository.create(result)

    suspend fun findChecklist(appointmentId: String): List<AppointmentChecklist> =
        checklistRepository.findByAppointmentId(appointmentId)

    suspend fun createChecklist(items: List<AppointmentChecklist>): List<AppointmentChecklist> =
        checklistRepository.create(items)

    suspend fun updateChecklist(appointmentId: String, masterId: String, isChecked: Boolean): AppointmentChecklist =
        checklistRepository.update(appointmentId, masterId, isChecked)
            ?: throw NotFoundException("Checklist item not found")
}