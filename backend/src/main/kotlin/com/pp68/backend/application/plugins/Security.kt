package com.pp68.backend.application.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*

data class JwtConfig(val secret: String, val issuer: String, val audience: String, val realm: String, val expireHours: Long)

fun Application.configureSecurity(): JwtConfig {
    val cfg = JwtConfig(
        secret      = environment.config.property("jwt.secret").getString(),
        issuer      = environment.config.property("jwt.issuer").getString(),
        audience    = environment.config.property("jwt.audience").getString(),
        realm       = environment.config.property("jwt.realm").getString(),
        expireHours = environment.config.property("jwt.expireHours").getString().toLong()
    )
    install(Authentication) {
        jwt("jwt-auth") {
            realm = cfg.realm
            verifier(
                JWT.require(Algorithm.HMAC256(cfg.secret))
                    .withAudience(cfg.audience)
                    .withIssuer(cfg.issuer)
                    .build()
            )
            validate { credential ->
                if (credential.payload.getClaim("user_id").asString() != null)
                    JWTPrincipal(credential.payload)
                else null
            }
        }
    }
    return cfg
}

fun generateToken(userId: String, jwt: JwtConfig): String =
    JWT.create()
        .withAudience(jwt.audience)
        .withIssuer(jwt.issuer)
        .withSubject(userId)
        .withClaim("user_id", userId)
        .withExpiresAt(java.util.Date(System.currentTimeMillis() + jwt.expireHours * 3600 * 1000))
        .sign(Algorithm.HMAC256(jwt.secret))