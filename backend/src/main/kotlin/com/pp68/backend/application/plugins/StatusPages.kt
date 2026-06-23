package com.pp68.backend.application.plugins

import com.pp68.backend.domain.exception.ConflictException
import com.pp68.backend.domain.exception.ForbiddenException
import com.pp68.backend.domain.exception.NotFoundException
import com.pp68.backend.domain.exception.UnauthorizedException
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(val error: String, val message: String)

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<NotFoundException> { call, cause ->
            call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", cause.message ?: "Resource not found"))
        }
        exception<UnauthorizedException> { call, cause ->
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse("UNAUTHORIZED", cause.message ?: "Authentication failed"))
        }
        exception<ForbiddenException> { call, cause ->
            call.respond(HttpStatusCode.Forbidden, ErrorResponse("FORBIDDEN", cause.message ?: "Access denied"))
        }
        exception<ConflictException> { call, cause ->
            call.respond(HttpStatusCode.Conflict, ErrorResponse("CONFLICT", cause.message ?: "Resource conflict"))
        }
        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("BAD_REQUEST", cause.message ?: "Invalid request"))
        }
        exception<Throwable> { call, cause ->
            call.application.log.error("Unhandled exception", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred"))
        }
        status(HttpStatusCode.Unauthorized) { call, _ ->
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse("UNAUTHORIZED", "Authentication required"))
        }
    }
}