package com.pp68.backend.application.routes

import com.pp68.backend.domain.entity.ContactPerson
import com.pp68.backend.domain.repository.ContactPersonRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.contactRoutes() {
    val repo: ContactPersonRepository by inject()

    authenticate("jwt-auth") {
        route("/contact_person") {

            get {
                val custId    = call.request.queryParameters["cust_id"]
                val userId    = call.request.queryParameters["user_id"]
                val contactId = call.request.queryParameters["contact_id"]
                val limit     = call.request.queryParameters["limit"]?.toIntOrNull() ?: 1000

                when {
                    custId != null && custId.startsWith("in.") -> {
                        val ids = custId.removePrefix("in.(").removeSuffix(")").split(",")
                        call.respond(repo.findByCustIds(ids))
                    }
                    custId    != null -> call.respond(repo.findByCustId(custId))
                    userId    != null -> call.respond(repo.findByUserId(userId))
                    contactId != null -> call.respond(listOfNotNull(repo.findById(contactId)))
                    else -> call.respond(HttpStatusCode.BadRequest)
                }
            }

            post {
                val contact = call.receive<ContactPerson>()
                call.respond(HttpStatusCode.Created, listOf(repo.create(contact)))
            }

            patch {
                val contactId = call.request.queryParameters["contact_id"]
                    ?: return@patch call.respond(HttpStatusCode.BadRequest)
                val updates = call.receive<Map<String, String?>>()
                val updated = repo.update(contactId, updates) ?: return@patch call.respond(HttpStatusCode.NotFound)
                call.respond(listOf(updated))
            }

            delete {
                val contactId = call.request.queryParameters["contact_id"]
                val custId    = call.request.queryParameters["cust_id"]
                when {
                    contactId != null -> if (repo.delete(contactId)) call.respond(HttpStatusCode.NoContent)
                                         else call.respond(HttpStatusCode.NotFound)
                    custId    != null -> { repo.deleteByCustId(custId); call.respond(HttpStatusCode.NoContent) }
                    else -> call.respond(HttpStatusCode.BadRequest)
                }
            }
        }
    }
}