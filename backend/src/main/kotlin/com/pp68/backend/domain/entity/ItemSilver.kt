package com.pp68.backend.domain.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ItemSilver(
    @SerialName("item_no")               val itemNo:            String,
    @SerialName("variant_code")          val variantCode:       String? = null,
    @SerialName("description")           val description:       String? = null,
    @SerialName("product_brand_no")      val productBrandNo:    String? = null,
    @SerialName("product_group_no")      val productGroupNo:    String? = null,
    @SerialName("product_subgroup_no")   val productSubgroupNo: String? = null,
    @SerialName("product_color_no")      val productColorNo:    String? = null,
    @SerialName("base_unit_of_measure")  val baseUnitOfMeasure: String? = null,
    @SerialName("product_weight")        val productWeight:     Double? = null,
    @SerialName("brand_name")            val brandName:         String? = null,
    @SerialName("group_name")            val groupName:         String? = null,
    @SerialName("subgroup_name")         val subgroupName:      String? = null,
    @SerialName("color_name")            val colorName:         String? = null
)
