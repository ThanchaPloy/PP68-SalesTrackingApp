package com.example.pp68_salestrackingapp.data.model

data class ContactWithCompany(
    val contactId: String,
    val companyId: String,
    val fullName: String?,
    val nickname: String?,
    val position: String?,
    val phoneNum: String?,
    val email: String?,
    val line: String?,
    val companyName: String,
    val branch: String?,
    val companyType: String?
)