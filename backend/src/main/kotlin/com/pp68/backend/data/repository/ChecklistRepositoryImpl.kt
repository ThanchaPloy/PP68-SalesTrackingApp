package com.pp68.backend.data.repository

import com.pp68.backend.data.database.tables.ChecklistTable
import com.pp68.backend.domain.entity.AppointmentChecklist
import com.pp68.backend.domain.repository.ChecklistRepository
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class ChecklistRepositoryImpl : ChecklistRepository {

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }

    override suspend fun findByAppointmentId(appointmentId: String): List<AppointmentChecklist> = dbQuery {
        ChecklistTable.select { ChecklistTable.appointmentId eq appointmentId }.map {
            AppointmentChecklist(it[ChecklistTable.appointmentId], it[ChecklistTable.masterId], it[ChecklistTable.isChecked])
        }
    }

    override suspend fun create(items: List<AppointmentChecklist>): List<AppointmentChecklist> = dbQuery {
        ChecklistTable.batchInsert(items) { item ->
            this[ChecklistTable.appointmentId] = item.appointmentId
            this[ChecklistTable.masterId]      = item.masterId
            this[ChecklistTable.isChecked]     = item.isChecked
        }
        items
    }

    override suspend fun update(appointmentId: String, masterId: String, isChecked: Boolean): AppointmentChecklist? = dbQuery {
        ChecklistTable.update({
            (ChecklistTable.appointmentId eq appointmentId) and (ChecklistTable.masterId eq masterId)
        }) { it[ChecklistTable.isChecked] = isChecked }
        ChecklistTable.select {
            (ChecklistTable.appointmentId eq appointmentId) and (ChecklistTable.masterId eq masterId)
        }.singleOrNull()?.let {
            AppointmentChecklist(it[ChecklistTable.appointmentId], it[ChecklistTable.masterId], it[ChecklistTable.isChecked])
        }
    }
}