package com.pp68.backend.domain.entity

data class User(
    val userId: String,
    val fullName: String,
    val branchId: String?,
    val role: String,
    val email: String,
    val phoneNumber: String?,
    val passwordHash: String,
    val isActive: Boolean,
    val fcmToken: String?,
    val createdAt: String?
)