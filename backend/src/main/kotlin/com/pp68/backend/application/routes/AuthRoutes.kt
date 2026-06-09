package com.pp68.backend.application.routes

import at.favre.lib.crypto.bcrypt.BCrypt
import com.pp68.backend.application.dto.ChangePasswordRequest
import com.pp68.backend.application.dto.LoginRequest
import com.pp68.backend.application.dto.LoginResponse
import com.pp68.backend.application.dto.RegisterRequest
import com.pp68.backend.application.plugins.generateToken
import com.pp68.backend.domain.entity.User
import com.pp68.backend.domain.repository.UserRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import java.util.UUID

fun Route.authRoutes() {
    val userRepo: UserRepository by inject()
    val jwtSecret   = application.environment.config.property("jwt.secret").getString()
    val jwtIssuer   = application.environment.config.property("jwt.issuer").getString()
    val jwtAudience = application.environment.config.property("jwt.audience").getString()
    val expireHours = application.environment.config.property("jwt.expireHours").getString().toLong()

    post("/login-api") {
        val req = call.receive<LoginRequest>()
        val user = userRepo.findByEmail(req.email)
            ?: return@post call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid credentials"))

        val verified = BCrypt.verifyer().verify(req.password.toCharArray(), user.passwordHash).verified
        if (!verified) return@post call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid credentials"))

        if (!user.isActive) return@post call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Account is inactive"))

        req.fcmToken?.let { userRepo.updateFcmToken(user.userId, it) }

        val token = generateToken(user.userId, user.email, user.role, jwtSecret, jwtIssuer, jwtAudience, expireHours)
        call.respond(LoginResponse(token, user.userId, user.fullName, user.role, user.branchId))
    }

    post("/register-api") {
        val req = call.receive<RegisterRequest>()

        if (userRepo.findByEmail(req.email) != null)
            return@post call.respond(HttpStatusCode.Conflict, mapOf("error" to "Email already exists"))

        val hash = BCrypt.withDefaults().hashToString(12, req.password.toCharArray())
        val user = userRepo.create(
            User(
                userId       = UUID.randomUUID().toString(),
                fullName     = req.fullName,
                branchId     = req.branchId,
                role         = req.role,
                email        = req.email,
                phoneNumber  = req.phoneNumber,
                passwordHash = hash,
                isActive     = true,
                fcmToken     = null,
                createdAt    = null
            )
        )
        call.respond(HttpStatusCode.Created, mapOf("user_id" to user.userId, "email" to user.email))
    }

    post("/change-password-api") {
        val req = call.receive<ChangePasswordRequest>()
        val user = userRepo.findById(req.userId)
            ?: return@post call.respond(HttpStatusCode.NotFound, mapOf("error" to "User not found"))

        val verified = BCrypt.verifyer().verify(req.oldPassword.toCharArray(), user.passwordHash).verified
        if (!verified) return@post call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Wrong current password"))

        val newHash = BCrypt.withDefaults().hashToString(12, req.newPassword.toCharArray())
        userRepo.updateProfile(req.userId, mapOf("password_hash" to newHash))
        call.respond(mapOf("success" to true))
    }
}