package com.pp68.backend.application.plugins

import com.pp68.backend.application.routes.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        get("/health") { call.respond(mapOf("status" to "ok", "service" to "pp68-backend")) }

        // Auth — no prefix (matches Cloud Functions URL pattern)
        authRoutes()

        // All protected API routes
        branchRoutes()
        userRoutes()
        customerRoutes()
        contactRoutes()
        projectRoutes()
        appointmentRoutes()
        productRoutes()
        uploadRoutes()
        callLogRoutes()

        // Serve uploaded files statically
        staticFiles("/uploads", java.io.File("uploads"))
    }
}