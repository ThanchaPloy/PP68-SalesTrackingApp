package com.pp68.backend.data.database.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object UserTable : Table("user") {
    val userId      = varchar("user_id", 64)
    val fullName    = varchar("full_name", 255)
    val branchId    = varchar("branch_id", 64).nullable()
    val role        = varchar("role", 50)
    val email       = varchar("email", 255).uniqueIndex()
    val phoneNumber = varchar("phone_number", 50).nullable()
    val passwordHash = varchar("password_hash", 255)
    val isActive    = bool("is_active").default(true)
    val fcmToken    = text("fcm_token").nullable()
    val createdAt   = timestamp("created_at").nullable()

    override val primaryKey = PrimaryKey(userId)
}