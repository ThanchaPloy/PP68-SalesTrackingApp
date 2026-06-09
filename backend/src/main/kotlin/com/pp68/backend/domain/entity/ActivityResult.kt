package com.pp68.backend.domain.entity

data class ActivityResult(
    val resultId: String,
    val activityId: String?,
    val projectId: String?,
    val createdBy: String?,
    val reportDate: String?,
    val newStatus: String?,
    val opportunityScore: String?,
    val dmInvolved: Boolean,
    val isProposalSent: Boolean,
    val proposalDate: String?,
    val competitorCount: Int,
    val responseSpeed: String?,
    val dealPosition: String?,
    val previousSolution: String?,
    val counterpartyType: String?,
    val summary: String?,
    val photoUrl: String?,
    val photoTakenAt: String?,
    val photoLat: Double?,
    val photoLng: Double?,
    val photoDeviceModel: String?,
    val lossReason: String?
)