package com.pp68.backend.data.database

import com.pp68.backend.data.database.tables.*
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseFactory {

    fun init(config: ApplicationConfig) {
        val dbUrl      = config.property("database.url").getString()
        val dbUser     = config.property("database.user").getString()
        val dbPassword = config.property("database.password").getString()
        val poolSize   = config.property("database.poolSize").getString().toInt()

        val hikariConfig = HikariConfig().apply {
            jdbcUrl         = dbUrl
            username        = dbUser
            password        = dbPassword
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = poolSize
            isAutoCommit    = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        }

        Database.connect(HikariDataSource(hikariConfig))

        transaction {
            SchemaUtils.createMissingTablesAndColumns(
                UserTable,
                BranchTable,
                CustomerTable,
                ContactPersonTable,
                ProjectTable,
                AppointmentTable,
                ActivityResultTable,
                ActivityMasterTable,
                ChecklistTable,
                ProjectContactTable,
                AppointmentContactTable,
                ProductTable,
                ProjectProductTable,
                ProjectMemberTable,
                CallLogTable
            )
        }
    }
}