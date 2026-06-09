package com.pp68.backend.domain.repository

import com.pp68.backend.domain.entity.User

interface UserRepository {
    suspend fun findById(userId: String): User?
    suspend fun findByEmail(email: String): User?
    suspend fun findByBranch(branchId: String): List<User>
    suspend fun findByIds(userIds: List<String>): List<User>
    suspend fun create(user: User): User
    suspend fun updateFcmToken(userId: String, fcmToken: String): User?
    suspend fun updateProfile(userId: String, updates: Map<String, Any?>): User?
}