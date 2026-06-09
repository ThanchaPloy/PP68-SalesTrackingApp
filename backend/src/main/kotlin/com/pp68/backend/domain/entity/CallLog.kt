package com.pp68.backend.domain.entity

data class CallLog(
    val callLogId: String,
    val userId: String,
    val custId: String?,
    val calledNumber: String,
    val callDate: String,
    val duration: Int?
)