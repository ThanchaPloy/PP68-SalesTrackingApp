package com.pp68.backend.application.routes

import com.pp68.backend.data.repository.EmployeeRepositoryImpl
import com.pp68.backend.domain.entity.Employee
import com.pp68.backend.domain.exception.NotFoundException
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.userRoutes() {
    val employeeRepo: EmployeeRepositoryImpl by inject()

    authenticate("jwt-auth") {
        route("/user") {

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
