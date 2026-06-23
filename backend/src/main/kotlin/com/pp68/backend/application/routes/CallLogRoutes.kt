package com.pp68.backend.application.routes

import com.pp68.backend.data.repository.CallLogRepositoryImpl
import com.pp68.backend.domain.entity.CallLog
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import java.util.UUID

fun Route.callLogRoutes() {
    val callLogRepo: CallLogRepositoryImpl by inject()

    authenticate("jwt-auth") {
        route("/call_log") {
            post {
                val raw = call.receive<CallLog>()
                val log = callLogRepo.create(if (raw.logId.isBlank()) raw.copy(logId = UUID.randomUUID().toString()) else raw)
                call.respond(HttpStatusCode.Created, log)
            }
        }
    }
}
