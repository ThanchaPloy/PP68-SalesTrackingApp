package com.example.pp68_salestrackingapp.data.model

import com.google.gson.annotations.SerializedName

data class ProjectProductInsertDto(
    @SerializedName("project_code")       val projectId:        String,
    @SerializedName("product_id")         val productId:        String,
    @SerializedName("quantity")           val quantity:         Double,
    @SerializedName("desired_date")       val desiredDate:      String?,
    @SerializedName("shipping_branch_id") val shippingBranchId: String?,
    @SerializedName("brand_name")         val brandName:        String?,
    @SerializedName("category_name")      val categoryName:     String?,
    @SerializedName("subcategory_name")   val subcategoryName:  String?,
    @SerializedName("product_name")       val productName:      String?,
    @SerializedName("color")              val color:            String?,
    @SerializedName("thickness")          val thickness:        String?,
    @SerializedName("width")              val width:            String?,
    @SerializedName("length")             val length:           String?,
    @SerializedName("dimension_unit")     val dimensionUnit:    String?,
    @SerializedName("uom")                val uom:              String?
)