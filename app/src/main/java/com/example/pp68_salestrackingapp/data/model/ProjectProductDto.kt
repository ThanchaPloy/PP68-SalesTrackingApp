package com.example.pp68_salestrackingapp.data.model

import com.google.gson.annotations.SerializedName

data class ProjectProductDto(
    @SerializedName("project_id")  val projectId:  String,
    @SerializedName("product_id")  val productId:  String,
    @SerializedName("quantity")    val quantity:   Double?,
    @SerializedName("desired_date") val desiredDate: String?,
    @SerializedName("shipping_branch_id") val shippingBranchId: String?
)