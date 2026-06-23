package com.pp68.backend.application.routes

import com.pp68.backend.application.dto.ChangePasswordRequest
import com.pp68.backend.application.dto.LoginRequest
import com.pp68.backend.application.dto.LoginResponse
import com.pp68.backend.application.dto.MessageResponse
import com.pp68.backend.application.plugins.JwtConfig
import com.pp68.backend.application.plugins.generateToken
import com.pp68.backend.domain.usecase.AuthUseCase
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.authRoutes(jwt: JwtConfig) {
    val authUseCase: AuthUseCase by inject()

    post("/login-api") {
        val req = call.receive<LoginRequest>()
        val employee = authUseCase.login(req.empCode, req.password)
        val token = generateToken(employee.empCode, jwt)
        call.respond(LoginResponse(
            token     = token,
            userId    = employee.empCode,
            fullName  = employee.empName ?: "",
            role      = employee.empPost ?: "sales",
            branchId  = employee.empBrchCode,
            empType   = employee.empType
        ))
    }

    authenticate("jwt-auth") {
        post("/change-password-api") {
            val req = call.receive<ChangePasswordRequest>()
            authUseCase.changePassword(req.empCode, req.oldPassword, req.newPassword)
            call.respond(MessageResponse("Password changed successfully"))
        }
    }
}
