package com.pp68.backend.application.routes

import com.pp68.backend.domain.entity.CallLog
import com.pp68.backend.domain.repository.CallLogRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.callLogRoutes() {
    val repo: CallLogRepository by inject()

    authenticate("jwt-auth") {
        route("/call_log") {
            post {
                val body = call.receive<Map<String, String>>()
                val log = CallLog(
                    callLogId    = body["call_log_id"] ?: java.util.UUID.randomUUID().toString(),
                    userId       = body["user_id"] ?: return@post call.respond(HttpStatusCode.BadRequest),
                    custId       = body["cust_id"],
                    calledNumber = body["called_number"] ?: return@post call.respond(HttpStatusCode.BadRequest),
                    callDate     = body["call_date"] ?: return@post call.respond(HttpStatusCode.BadRequest),
                    duration     = body["duration"]?.toIntOrNull()
                )
                call.respond(HttpStatusCode.Created, repo.create(log))
            }
        }
    }
}