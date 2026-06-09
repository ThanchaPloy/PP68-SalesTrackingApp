package com.pp68.backend.domain.entity

data class ProjectProduct(
    val projectId: String,
    val productId: String,
    val quantity: Int?,
    val status: String?,
    val deliveryDate: String?
)