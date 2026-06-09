package com.pp68.backend.application.routes

import com.pp68.backend.domain.entity.Appointment
import com.pp68.backend.domain.entity.ActivityResult
import com.pp68.backend.domain.entity.ActivityMaster
import com.pp68.backend.domain.entity.AppointmentChecklist
import com.pp68.backend.domain.repository.ActivityMasterRepository
import com.pp68.backend.domain.repository.ActivityResultRepository
import com.pp68.backend.domain.repository.AppointmentRepository
import com.pp68.backend.domain.repository.ChecklistRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.appointmentRoutes() {
    val appointmentRepo: AppointmentRepository by inject()
    val resultRepo: ActivityResultRepository by inject()
    val masterRepo: ActivityMasterRepository by inject()
    val checklistRepo: ChecklistRepository by inject()

    authenticate("jwt-auth") {

        route("/appointment") {
            get {
                val userId        = call.request.queryParameters["user_id"]
                val appointmentId = call.request.queryParameters["appointment_id"]
                val limit         = call.request.queryParameters["limit"]?.toIntOrNull() ?: 1000

                when {
                    appointmentId != null -> {
                        val a = appointmentRepo.findById(appointmentId)
                            ?: return@get call.respond(HttpStatusCode.NotFound)
                        call.respond(listOf(a))
                    }
                    userId != null -> call.respond(appointmentRepo.findByUserId(userId, limit))
                    else -> call.respond(HttpStatusCode.BadRequest, mapOf("error" to "user_id or appointment_id required"))
                }
            }

            post {
                val appointment = call.receive<Appointment>()
                call.respond(HttpStatusCode.Created, listOf(appointmentRepo.create(appointment)))
            }

            patch {
                val appointmentId = call.request.queryParameters["appointment_id"]
                    ?: return@patch call.respond(HttpStatusCode.BadRequest)
                val updates = call.receive<Map<String, String?>>()
                val updated = appointmentRepo.update(appointmentId, updates)
                    ?: return@patch call.respond(HttpStatusCode.NotFound)
                call.respond(listOf(updated))
            }

            delete {
                val appointmentId = call.request.queryParameters["appointment_id"]
                val custId        = call.request.queryParameters["cust_id"]
                when {
                    appointmentId != null -> if (appointmentRepo.delete(appointmentId)) call.respond(HttpStatusCode.NoContent)
                                             else call.respond(HttpStatusCode.NotFound)
                    custId != null -> { appointmentRepo.deleteByCustId(custId); call.respond(HttpStatusCode.NoContent) }
                    else -> call.respond(HttpStatusCode.BadRequest)
                }
            }
        }

        route("/activity_master") {
            get {
                val isActive = call.request.queryParameters["is_active"]
                call.respond(if (isActive == "eq.true") masterRepo.findActive() else masterRepo.findAll())
            }
        }

        route("/activity_result") {
            get {
                val appointmentId = call.request.queryParameters["appointment_id"]
                val createdBy     = call.request.queryParameters["created_by"]
                val resultId      = call.request.queryParameters["result_id"]
                val limit         = call.request.queryParameters["limit"]?.toIntOrNull() ?: 1000

                when {
                    resultId      != null -> call.respond(listOfNotNull(resultRepo.findById(resultId)))
                    appointmentId != null -> call.respond(listOfNotNull(resultRepo.findByAppointmentId(appointmentId)))
                    createdBy     != null -> call.respond(resultRepo.findByUserId(createdBy, limit))
                    else -> call.respond(HttpStatusCode.BadRequest)
                }
            }

            post {
                val prefer = call.request.headers["Prefer"] ?: ""
                val result = call.receive<ActivityResult>()
                val saved = if (prefer.contains("merge-duplicates")) resultRepo.upsert(result)
                            else resultRepo.create(result)
                call.respond(HttpStatusCode.Created, listOf(saved))
            }
        }

        route("/appointment_checklist") {
            get {
                val appointmentId = call.request.queryParameters["appointment_id"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest)
                call.respond(checklistRepo.findByAppointmentId(appointmentId))
            }
            post {
                val items = call.receive<List<AppointmentChecklist>>()
                call.respond(HttpStatusCode.Created, checklistRepo.create(items))
            }
            patch {
                val appointmentId = call.request.queryParameters["appointment_id"]
                    ?: return@patch call.respond(HttpStatusCode.BadRequest)
                val masterId = call.request.queryParameters["master_id"]
                    ?: return@patch call.respond(HttpStatusCode.BadRequest)
                val body = call.receive<Map<String, Boolean>>()
                val updated = checklistRepo.update(appointmentId, masterId, body["is_checked"] ?: false)
                call.respond(listOfNotNull(updated))
            }
        }
    }
}