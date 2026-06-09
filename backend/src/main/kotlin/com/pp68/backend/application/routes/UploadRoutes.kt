package com.pp68.backend.application.routes

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File
import java.util.UUID

fun Route.uploadRoutes() {
    authenticate("jwt-auth") {
        post("/upload-visit-photo") {
            val uploadDir = application.environment.config.property("upload.dir").getString()
            File(uploadDir).mkdirs()

            var photoUrl: String? = null
            val multipart = call.receiveMultipart()

            multipart.forEachPart { part ->
                if (part is PartData.FileItem) {
                    val ext  = part.originalFileName?.substringAfterLast('.', "jpg") ?: "jpg"
                    val name = "${UUID.randomUUID()}.$ext"
                    val file = File("$uploadDir/$name")
                    part.streamProvider().use { input -> file.outputStream().use { input.copyTo(it) } }
                    photoUrl = "/uploads/$name"
                }
                part.dispose()
            }

            if (photoUrl == null) return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "No file received"))
            call.respond(mapOf("photo_url" to photoUrl))
        }
    }
}