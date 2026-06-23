package com.pp68.backend.domain.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Legacy alias kept for ProjectProduct usage; item catalog is now ItemSilver
@Serializable
data class Product(
    @SerialName("item_no")               val productId:         String,
    @SerialName("description")           val description:       String? = null,
    @SerialName("product_brand_no")      val productBrandNo:    String? = null,
    @SerialName("product_group_no")      val productGroupNo:    String? = null,
    @SerialName("base_unit_of_measure")  val unit:              String? = null,
    @SerialName("brand_name")            val brandName:         String? = null,
    @SerialName("group_name")            val groupName:         String? = null,
    @SerialName("color_name")            val colorName:         String? = null
)
