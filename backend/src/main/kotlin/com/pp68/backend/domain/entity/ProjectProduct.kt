package com.pp68.backend.domain.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProjectProduct(
    @SerialName("project_id")         val projectId:        String,
    @SerialName("product_id")         val productId:        String,
    @SerialName("quantity")           val quantity:         Double? = null,
    @SerialName("desired_date")       val desiredDate:      String? = null,
    @SerialName("shipping_branch_id") val shippingBranchId: String? = null
)