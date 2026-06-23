package com.pp68.backend.application.routes

import com.pp68.backend.data.repository.ContactPersonRepositoryImpl
import com.pp68.backend.domain.entity.ContactPerson
import com.pp68.backend.domain.exception.NotFoundException
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.contactRoutes() {
    val contactRepo: ContactPersonRepositoryImpl by inject()

    authenticate("jwt-auth") {
        route("/contact_person") {
            fun custCode(params: io.ktor.http.Parameters) =
                (params["customer_code"] ?: params["cust_id"]).stripEq()

            get {
                val custCodeRaw = call.request.queryParameters["customer_code"]
                    ?: call.request.queryParameters["cust_id"]
                val contactId = call.request.queryParameters["contact_id"]?.toLongOrNull()

                when {
                    custCodeRaw != null && custCodeRaw.startsWith("in.") -> {
                        val codes = custCodeRaw.removePrefix("in.(").removeSuffix(")").split(",")
                        call.respond<List<ContactPerson>>(contactRepo.findByCustomerCodes(codes))
                    }
                    custCodeRaw != null -> call.respond<List<ContactPerson>>(contactRepo.findByCustomerCode(custCodeRaw.stripEq()!!))
                    contactId   != null -> call.respond<List<ContactPerson>>(listOf(contactRepo.findById(contactId) ?: throw NotFoundException("Contact not found: $contactId")))
                    else -> call.respond(HttpStatusCode.BadRequest)
                }
            }

            post {
                val contact = call.receive<ContactPerson>()
                call.respond<List<ContactPerson>>(HttpStatusCode.Created, listOf(contactRepo.create(contact)))
            }

            patch {
                val contactId = call.request.queryParameters["contact_id"]?.toLongOrNull()
                    ?: return@patch call.respond(HttpStatusCode.BadRequest)
                val updates = call.receive<Map<String, String?>>()
                call.respond<List<ContactPerson>>(listOf(contactRepo.update(contactId, updates) ?: throw NotFoundException("Contact not found: $contactId")))
            }

            delete {
                val contactId    = call.request.queryParameters["contact_id"]?.toLongOrNull()
                val customerCode = custCode(call.request.queryParameters)
                when {
                    contactId    != null -> { if (!contactRepo.delete(contactId)) throw NotFoundException("Contact not found: $contactId"); call.respond(HttpStatusCode.NoContent) }
                    customerCode != null -> { contactRepo.deleteByCustomerCode(customerCode); call.respond(HttpStatusCode.NoContent) }
                    else -> call.respond(HttpStatusCode.BadRequest)
                }
            }
        }
    }
}
