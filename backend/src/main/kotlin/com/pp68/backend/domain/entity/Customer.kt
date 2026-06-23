package com.pp68.backend.domain.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Customer(
    @SerialName("customer_code")          val customerCode:       String,
    @SerialName("customer_name")          val customerName:       String,
    @SerialName("create_by")              val createBy:           String? = null,
    @SerialName("address")                val address:            String? = null,
    @SerialName("salesperson_code")       val salespersonCode:    String? = null,
    @SerialName("create_date")            val createDate:         String? = null,
    @SerialName("gen_bus_posting_group")  val genBusPostingGroup: String? = null,
    @SerialName("customer_status")        val customerStatus:     Int?    = null,
    @SerialName("grade")                  val grade:              Int?    = null,
    @SerialName("updated_at")             val updatedAt:          String? = null
)
