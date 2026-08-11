package com.example.pp68_salestrackingapp.data.model

import com.google.gson.annotations.SerializedName

data class UserDto(
    @SerializedName("emp_code")         val userId:         String,
    @SerializedName("emp_name")         val fullName:       String? = null,
    @SerializedName("emp_brch_code")    val branchId:       String? = null,
    @SerializedName("emp_post")         val role:           String? = null,
    @SerializedName("emp_type")         val empType:        String? = null,
    @SerializedName("phone_number")     val phoneNumber:    String? = null,
    @SerializedName("email")            val email:          String? = null,
    @SerializedName("is_project_sales") val isProjectSales: Boolean = false
)
