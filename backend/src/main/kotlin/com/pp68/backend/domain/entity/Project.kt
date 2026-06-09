package com.pp68.backend.domain.entity

data class Project(
    val projectId: String,
    val custId: String,
    val branchId: String?,
    val billingBranchId: String?,
    val projectName: String,
    val expectedValue: Double?,
    val projectStatus: String?,
    val startDate: String?,
    val closingDate: String?,
    val desiredCompletionDate: String?,
    val projectLat: Double?,
    val projectLong: Double?,
    val opportunityScore: String?,
    val progressPct: Int?,
    val lossReason: String?,
    val userId: String?,
    val createdAt: String?,
    val updatedAt: String?
)