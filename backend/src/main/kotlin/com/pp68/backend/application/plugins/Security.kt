package com.pp68.backend.application.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*

fun Application.configureSecurity() {
    val jwtSecret   = environment.config.property("jwt.secret").getString()
    val jwtIssuer   = environment.config.property("jwt.issuer").getString()
    val jwtAudience = environment.config.property("jwt.audience").getString()
    val jwtRealm    = environment.config.property("jwt.realm").getString()

    install(Authentication) {
        jwt("jwt-auth") {
            realm = jwtRealm
            verifier(
                JWT.require(Algorithm.HMAC256(jwtSecret))
                    .withAudience(jwtAudience)
                    .withIssuer(jwtIssuer)
                    .build()
            )
            validate { credential ->
                if (credential.payload.getClaim("userId").asString() != null)
                    JWTPrincipal(credential.payload)
                else null
            }
        }
    }
}

fun generateToken(
    userId: String,
    email: String,
    role: String,
    secret: String,
    issuer: String,
    audience: String,
    expireHours: Long = 168
): String {
    return JWT.create()
        .withAudience(audience)
        .withIssuer(issuer)
        .withClaim("userId", userId)
        .withClaim("email", email)
        .withClaim("role", role)
        .withExpiresAt(java.util.Date(System.currentTimeMillis() + expireHours * 3600 * 1000))
        .sign(Algorithm.HMAC256(secret))
}