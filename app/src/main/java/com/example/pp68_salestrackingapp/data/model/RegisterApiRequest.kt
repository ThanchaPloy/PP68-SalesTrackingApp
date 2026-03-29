package com.example.pp68_salestrackingapp.data.model


import com.google.gson.annotations.SerializedName

data class RegisterApiRequest(
    @SerializedName("email")     val email:    String,
    @SerializedName("password")  val password: String,
    @SerializedName("full_name") val fullName: String,
    @SerializedName("branch_id") val branchId: String
)