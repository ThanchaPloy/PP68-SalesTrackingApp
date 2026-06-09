package com.pp68.backend.application.routes

import com.pp68.backend.application.dto.CreateCustomerRequest
import com.pp68.backend.domain.entity.Customer
import com.pp68.backend.domain.repository.CustomerRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.customerRoutes() {
    val repo: CustomerRepository by inject()

    authenticate("jwt-auth") {
        route("/customer") {

            get {
                val custId   = call.request.queryParameters["cust_id"]
                val branchId = call.request.queryParameters["branch_id"]
                val limit    = call.request.queryParameters["limit"]?.toIntOrNull() ?: 1000

                when {
                    custId != null && custId.startsWith("in.") -> {
                        val ids = custId.removePrefix("in.(").removeSuffix(")").split(",")
                        call.respond(repo.findByIds(ids))
                    }
                    custId != null -> {
                        val c = repo.findById(custId) ?: return@get call.respond(HttpStatusCode.NotFound)
                        call.respond(listOf(c))
                    }
                    branchId != null -> call.respond(repo.findByBranch(branchId))
                    else -> call.respond(repo.findAll(limit))
                }
            }

            post {
                val req = call.receive<CreateCustomerRequest>()
                val customer = repo.create(
                    Customer(req.custId, req.companyName, req.branchId, req.branch,
                        req.custType, req.companyAddr, req.companyLat, req.companyLong,
                        req.companyStatus, null, req.userId)
                )
                call.respond(HttpStatusCode.Created, listOf(customer))
            }

            patch {
                val custId = call.request.queryParameters["cust_id"]
                    ?: return@patch call.respond(HttpStatusCode.BadRequest)
                val updates = call.receive<Map<String, String?>>()
                val updated = repo.update(custId, updates) ?: return@patch call.respond(HttpStatusCode.NotFound)
                call.respond(listOf(updated))
            }

            delete {
                val custId = call.request.queryParameters["cust_id"]
                    ?: return@delete call.respond(HttpStatusCode.BadRequest)
                val deleted = repo.delete(custId)
                if (deleted) call.respond(HttpStatusCode.NoContent)
                else call.respond(HttpStatusCode.NotFound)
            }
        }
    }
}