package com.pp68.backend.domain.entity

data class ContactPerson(
    val contactId: String,
    val custId: String?,
    val userId: String?,
    val fullName: String,
    val role: String?,
    val phone: String?,
    val email: String?,
    val lineId: String?,
    val createdAt: String?
)