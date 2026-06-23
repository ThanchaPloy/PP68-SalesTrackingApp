package com.pp68.backend

import com.pp68.backend.application.di.appModule
import com.pp68.backend.application.plugins.*
import com.pp68.backend.data.database.DatabaseFactory
import io.ktor.server.application.*
import io.ktor.server.netty.EngineMain
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import java.io.File

fun main(args: Array<String>) {
    loadDotenv()
    EngineMain.main(args)
}

private fun loadDotenv() {
    val f = File(".env"); if (!f.exists()) return
    val props = java.util.Properties().apply { load(f.bufferedReader()) }
    props.forEach { k, v -> if (System.getenv(k as String) == null) System.setProperty(k, v as String) }
}

fun Application.module() {
    // Database
    DatabaseFactory.init(environment.config)
    File(environment.config.property("upload.dir").getString()).mkdirs()

    // DI
    install(Koin) {
        slf4jLogger()
        modules(appModule)
    }

    // Plugins
    configureSerialization()
    configureHTTP()
    val jwt = configureSecurity()
    configureStatusPages()
    configureRouting(jwt)

    log.info("PP68 Backend started on port ${environment.config.property("ktor.deployment.port").getString()}")
}