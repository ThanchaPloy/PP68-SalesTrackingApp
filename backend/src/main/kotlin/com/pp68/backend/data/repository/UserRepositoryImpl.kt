package com.pp68.backend.data.repository

import com.pp68.backend.data.database.tables.UserTable
import com.pp68.backend.domain.entity.User
import com.pp68.backend.domain.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant

class UserRepositoryImpl : UserRepository {

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }

    private fun ResultRow.toUser() = User(
        userId       = this[UserTable.userId],
        fullName     = this[UserTable.fullName],
        branchId     = this[UserTable.branchId],
        role         = this[UserTable.role],
        email        = this[UserTable.email],
        phoneNumber  = this[UserTable.phoneNumber],
        passwordHash = this[UserTable.passwordHash],
        isActive     = this[UserTable.isActive],
        fcmToken     = this[UserTable.fcmToken],
        createdAt    = this[UserTable.createdAt]?.toString()
    )

    override suspend fun findById(userId: String): User? = dbQuery {
        UserTable.select { UserTable.userId eq userId }.singleOrNull()?.toUser()
    }

    override suspend fun findByEmail(email: String): User? = dbQuery {
        UserTable.select { UserTable.email eq email }.singleOrNull()?.toUser()
    }

    override suspend fun findByBranch(branchId: String): List<User> = dbQuery {
        UserTable.select {
            (UserTable.branchId eq branchId) and (UserTable.isActive eq true)
        }.map { it.toUser() }
    }

    override suspend fun findByIds(userIds: List<String>): List<User> = dbQuery {
        UserTable.select { UserTable.userId inList userIds }.map { it.toUser() }
    }

    override suspend fun create(user: User): User = dbQuery {
        UserTable.insert {
            it[userId]       = user.userId
            it[fullName]     = user.fullName
            it[branchId]     = user.branchId
            it[role]         = user.role
            it[email]        = user.email
            it[phoneNumber]  = user.phoneNumber
            it[passwordHash] = user.passwordHash
            it[isActive]     = user.isActive
            it[createdAt]    = Instant.now()
        }
        UserTable.select { UserTable.userId eq user.userId }.single().toUser()
    }

    override suspend fun updateFcmToken(userId: String, fcmToken: String): User? = dbQuery {
        UserTable.update({ UserTable.userId eq userId }) { it[UserTable.fcmToken] = fcmToken }
        UserTable.select { UserTable.userId eq userId }.singleOrNull()?.toUser()
    }

    override suspend fun updateProfile(userId: String, updates: Map<String, Any?>): User? = dbQuery {
        UserTable.update({ UserTable.userId eq userId }) { stmt ->
            updates["full_name"]?.let    { v -> stmt[fullName]    = v as String }
            updates["phone_number"]?.let { v -> stmt[phoneNumber] = v as String }
            updates["branch_id"]?.let    { v -> stmt[branchId]    = v as String }
        }
        UserTable.select { UserTable.userId eq userId }.singleOrNull()?.toUser()
    }
}