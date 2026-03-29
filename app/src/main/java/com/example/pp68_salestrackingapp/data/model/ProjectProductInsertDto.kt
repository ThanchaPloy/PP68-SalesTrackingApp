package com.example.pp68_salestrackingapp.data.model

import com.google.gson.annotations.SerializedName

data class ProjectProductInsertDto(
    @SerializedName("project_id")  val projectId:  String,
    @SerializedName("product_id")  val productId:  String,
    @SerializedName("quantity")    val quantity:   Double,
    @SerializedName("wanted_date") val wantedDate: String?
)
