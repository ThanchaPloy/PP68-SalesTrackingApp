package com.pp68.backend

import com.pp68.backend.application.di.appModule
import com.pp68.backend.application.plugins.*
import com.pp68.backend.data.database.DatabaseFactory
import io.ktor.server.application.*
import io.ktor.server.netty.EngineMain
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun main(args: Array<String>) = EngineMain.main(args)

fun Application.module() {
    // Database
    DatabaseFactory.init(environment.config)

    // DI
    install(Koin) {
        slf4jLogger()
        modules(appModule)
    }

    // Plugins
    configureSerialization()
    configureHTTP()
    configureSecurity()
    configureStatusPages()
    configureRouting()

    log.info("PP68 Backend started on port ${environment.config.property("ktor.deployment.port").getString()}")
}