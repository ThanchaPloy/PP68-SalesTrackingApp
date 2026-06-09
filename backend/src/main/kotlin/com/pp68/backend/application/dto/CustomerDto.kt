package com.pp68.backend.application.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CustomerDto(
    @SerialName("cust_id")        val custId:        String,
    @SerialName("company_name")   val companyName:   String,
    @SerialName("branch_id")      val branchId:      String?  = null,
    val branch:                                       String?  = null,
    @SerialName("cust_type")      val custType:      String?  = null,
    @SerialName("company_addr")   val companyAddr:   String?  = null,
    @SerialName("company_lat")    val companyLat:    Double?  = null,
    @SerialName("company_long")   val companyLong:   Double?  = null,
    @SerialName("company_status") val companyStatus: String?  = null,
    @SerialName("created_at")     val createdAt:     String?  = null,
    @SerialName("user_id")        val userId:        String?  = null
)

@Serializable
data class CreateCustomerRequest(
    @SerialName("cust_id")        val custId:        String,
    @SerialName("company_name")   val companyName:   String,
    @SerialName("branch_id")      val branchId:      String?  = null,
    val branch:                                       String?  = null,
    @SerialName("cust_type")      val custType:      String?  = null,
    @SerialName("company_addr")   val companyAddr:   String?  = null,
    @SerialName("company_lat")    val companyLat:    Double?  = null,
    @SerialName("company_long")   val companyLong:   Double?  = null,
    @SerialName("company_status") val companyStatus: String?  = null,
    @SerialName("user_id")        val userId:        String?  = null
)