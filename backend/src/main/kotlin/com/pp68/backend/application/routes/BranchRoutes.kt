package com.pp68.backend.application.routes

import com.pp68.backend.domain.repository.BranchRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.branchRoutes() {
    val repo: BranchRepository by inject()

    authenticate("jwt-auth") {
        route("/branch") {
            get {
                val branchId = call.request.queryParameters["branch_id"]
                if (branchId != null) {
                    val b = repo.findById(branchId) ?: return@get call.respond(HttpStatusCode.NotFound)
                    call.respond(listOf(b))
                } else {
                    call.respond(repo.findAll())
                }
            }
        }
    }
}