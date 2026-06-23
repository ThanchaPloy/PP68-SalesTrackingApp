package com.pp68.backend.application.routes

import com.pp68.backend.domain.entity.ActivityMaster
import com.pp68.backend.domain.entity.ActivityResult
import com.pp68.backend.domain.entity.Appointment
import com.pp68.backend.domain.entity.AppointmentChecklist
import com.pp68.backend.domain.usecase.AppointmentUseCase
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.appointmentRoutes() {
    val appointmentUseCase: AppointmentUseCase by inject()

    authenticate("jwt-auth") {

        route("/appointment") {
            get {
                val userId        = call.request.queryParameters["user_id"].stripEq()
                val appointmentId = call.request.queryParameters["appointment_id"].stripEq()
                val limit         = call.request.queryParameters["limit"]?.toIntOrNull() ?: 1000

                when {
                    appointmentId != null -> call.respond<List<Appointment>>(listOf(appointmentUseCase.findById(appointmentId)))
                    userId        != null -> call.respond<List<Appointment>>(appointmentUseCase.findByUserId(userId, limit))
                    else -> call.respond(HttpStatusCode.BadRequest, mapOf("error" to "user_id or appointment_id required"))
                }
            }

            post {
                val appointment = call.receive<Appointment>()
                call.respond<List<Appointment>>(HttpStatusCode.Created, listOf(appointmentUseCase.create(appointment)))
            }

            patch {
                val appointmentId = call.request.queryParameters["appointment_id"].stripEq()
                    ?: return@patch call.respond(HttpStatusCode.BadRequest)
                val updates = call.receive<Map<String, String?>>()
                call.respond<List<Appointment>>(listOf(appointmentUseCase.update(appointmentId, updates)))
            }

            delete {
                val appointmentId = call.request.queryParameters["appointment_id"].stripEq()
                val custId        = call.request.queryParameters["cust_id"].stripEq()
                when {
                    appointmentId != null -> { appointmentUseCase.delete(appointmentId); call.respond(HttpStatusCode.NoContent) }
                    custId        != null -> { appointmentUseCase.deleteByCustId(custId); call.respond(HttpStatusCode.NoContent) }
                    else -> call.respond(HttpStatusCode.BadRequest)
                }
            }
        }

        route("/activity_master") {
            get {
                val isActive = call.request.queryParameters["is_active"]
                call.respond<List<ActivityMaster>>(appointmentUseCase.getActivityMasters(activeOnly = isActive == "eq.true"))
            }
        }

        route("/activity_result") {
            get {
                val appointmentId = call.request.queryParameters["appointment_id"].stripEq()
                val createdBy     = call.request.queryParameters["created_by"].stripEq()
                val resultId      = call.request.queryParameters["result_id"].stripEq()
                val limit         = call.request.queryParameters["limit"]?.toIntOrNull() ?: 1000

                when {
                    resultId      != null -> call.respond<List<ActivityResult>>(listOfNotNull(appointmentUseCase.findResultById(resultId)))
                    appointmentId != null -> call.respond<List<ActivityResult>>(listOfNotNull(appointmentUseCase.findResultByAppointmentId(appointmentId)))
                    createdBy     != null -> call.respond<List<ActivityResult>>(appointmentUseCase.findResultsByUserId(createdBy, limit))
                    else -> call.respond(HttpStatusCode.BadRequest)
                }
            }

            post {
                val prefer = call.request.headers["Prefer"] ?: ""
                val result = call.receive<ActivityResult>()
                val saved = appointmentUseCase.saveResult(result, upsert = prefer.contains("merge-duplicates"))
                call.respond<List<ActivityResult>>(HttpStatusCode.Created, listOf(saved))
            }
        }

        route("/appointment_checklist") {
            get {
                val appointmentId = call.request.queryParameters["appointment_id"].stripEq()
                    ?: return@get call.respond(HttpStatusCode.BadRequest)
                call.respond<List<AppointmentChecklist>>(appointmentUseCase.findChecklist(appointmentId))
            }
            post {
                val items = call.receive<List<AppointmentChecklist>>()
                call.respond<List<AppointmentChecklist>>(HttpStatusCode.Created, appointmentUseCase.createChecklist(items))
            }
            patch {
                val appointmentId = call.request.queryParameters["appointment_id"].stripEq()
                    ?: return@patch call.respond(HttpStatusCode.BadRequest)
                val masterId = call.request.queryParameters["master_id"].stripEq()
                    ?: return@patch call.respond(HttpStatusCode.BadRequest)
                val body = call.receive<Map<String, Boolean>>()
                call.respond<List<AppointmentChecklist>>(listOf(appointmentUseCase.updateChecklist(appointmentId, masterId, body["is_checked"] ?: false)))
            }
        }
    }
}