package com.pp68.backend.application.routes

import com.pp68.backend.domain.repository.UserRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.userRoutes() {
    val userRepo: UserRepository by inject()

    authenticate("jwt-auth") {
        route("/user") {

            get {
                val userId   = call.request.queryParameters["user_id"]
                val branchId = call.request.queryParameters["branch_id"]
                val userIds  = call.request.queryParameters["user_id"]
                    ?.removePrefix("in.(")?.removeSuffix(")")?.split(",")

                when {
                    userId != null && !userId.startsWith("in.") -> {
                        val user = userRepo.findById(userId)
                            ?: return@get call.respond(HttpStatusCode.NotFound)
                        call.respond(listOf(user))
                    }
                    userId?.startsWith("in.") == true -> {
                        val ids = userId.removePrefix("in.(").removeSuffix(")").split(",")
                        call.respond(userRepo.findByIds(ids))
                    }
                    branchId != null -> call.respond(userRepo.findByBranch(branchId))
                    else -> call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Query parameter required"))
                }
            }

            patch {
                val userId = call.request.queryParameters["user_id"]
                    ?: return@patch call.respond(HttpStatusCode.BadRequest)
                val updates = call.receive<Map<String, String>>()

                if (updates.containsKey("fcm_token")) {
                    val user = userRepo.updateFcmToken(userId, updates["fcm_token"]!!)
                    call.respond(listOf(user))
                } else {
                    val user = userRepo.updateProfile(userId, updates)
                    call.respond(listOf(user))
                }
            }
        }
    }
}