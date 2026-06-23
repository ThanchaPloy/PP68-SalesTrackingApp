package com.pp68.backend.application.routes

import com.pp68.backend.data.repository.CustomerRepositoryImpl
import com.pp68.backend.domain.entity.Customer
import com.pp68.backend.domain.exception.NotFoundException
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.customerRoutes() {
    val customerRepo: CustomerRepositoryImpl by inject()

    authenticate("jwt-auth") {
        route("/customer") {

            get {
                val custCodeRaw      = call.request.queryParameters["customer_code"]
                    ?: call.request.queryParameters["cust_id"]
                val salespersonCode  = call.request.queryParameters["salesperson_code"].stripEq()
                val limit            = call.request.queryParameters["limit"]?.toIntOrNull() ?: 1000

                when {
                    custCodeRaw != null && custCodeRaw.startsWith("in.") -> {
                        val codes = custCodeRaw.removePrefix("in.(").removeSuffix(")").split(",")
                        call.respond<List<Customer>>(customerRepo.findByIds(codes))
                    }
                    custCodeRaw     != null -> call.respond<List<Customer>>(listOf(customerRepo.findById(custCodeRaw.stripEq()!!) ?: throw NotFoundException("Customer not found: $custCodeRaw")))
                    salespersonCode != null -> call.respond<List<Customer>>(customerRepo.findBySalesperson(salespersonCode))
                    else                   -> call.respond<List<Customer>>(customerRepo.findAll(limit))
                }
            }

            post {
                val customer = customerRepo.create(call.receive<Customer>())
                call.respond<List<Customer>>(HttpStatusCode.Created, listOf(customer))
            }

            patch {
                val customerCode = call.request.queryParameters["customer_code"].stripEq()
                    ?: call.request.queryParameters["cust_id"].stripEq()
                    ?: return@patch call.respond(HttpStatusCode.BadRequest)
                val updates = call.receive<Map<String, String?>>()
                call.respond<List<Customer>>(listOf(customerRepo.update(customerCode, updates) ?: throw NotFoundException("Customer not found: $customerCode")))
            }

            delete {
                val customerCode = call.request.queryParameters["customer_code"].stripEq()
                    ?: call.request.queryParameters["cust_id"].stripEq()
                    ?: return@delete call.respond(HttpStatusCode.BadRequest)
                if (!customerRepo.delete(customerCode)) throw NotFoundException("Customer not found: $customerCode")
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}
