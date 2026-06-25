package com.pp68.backend.application.routes

import com.pp68.backend.data.repository.EmployeeRepositoryImpl
import com.pp68.backend.domain.entity.Employee
import com.pp68.backend.domain.exception.NotFoundException
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.userRoutes() {
    val employeeRepo: EmployeeRepositoryImpl by inject()

    authenticate("jwt-auth") {
        route("/user") {

            patch("fcm-token") {
                val empCode = call.principal<JWTPrincipal>()?.payload?.getClaim("user_id")?.asString()
                    ?: return@patch call.respond(HttpStatusCode.Unauthorized)
                val body = call.receive<Map<String, String>>()
                val token = body["fcm_token"]
                    ?: return@patch call.respond(HttpStatusCode.BadRequest, mapOf("error" to "fcm_token required"))
                val updated = employeeRepo.updateFcmToken(empCode, token)
                if (updated) call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
                else call.respond(HttpStatusCode.NotFound, mapOf("error" to "employee not found"))
            }

            get {
                val empCodeRaw = call.request.queryParameters["user_id"]
                val branchCode = call.request.queryParameters["branch_id"].stripEq()

                when {
                    empCodeRaw != null && empCodeRaw.startsWith("in.") -> {
                        val codes = empCodeRaw.removePrefix("in.(").removeSuffix(")").split(",")
                        call.respond<List<Employee>>(employeeRepo.findByCodes(codes))
                    }
                    empCodeRaw != null -> call.respond<List<Employee>>(listOf(employeeRepo.findByCode(empCodeRaw.stripEq()!!) ?: throw NotFoundException("Employee not found: $empCodeRaw")))
                    branchCode != null -> call.respond<List<Employee>>(employeeRepo.findByBranch(branchCode))
                    else -> call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Query parameter required"))
                }
            }
        }
    }
}
