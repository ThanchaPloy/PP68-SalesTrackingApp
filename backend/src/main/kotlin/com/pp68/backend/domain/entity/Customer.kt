package com.pp68.backend.domain.entity

data class Customer(
    val custId: String,
    val companyName: String,
    val branchId: String?,
    val branch: String?,
    val custType: String?,
    val companyAddr: String?,
    val companyLat: Double?,
    val companyLong: Double?,
    val companyStatus: String?,
    val createdAt: String?,
    val userId: String?
)