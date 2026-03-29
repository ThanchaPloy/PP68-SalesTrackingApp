package com.example.pp68_salestrackingapp.data.model

data class AuthUser(
    val userId: String,
    val email: String,
    val role: String,
    val teamId: String? = null,
    val fullName:   String? = null,
    val branchName: String? = null
)
