package com.example.pp68_salestrackingapp.data.model

import com.google.gson.annotations.SerializedName


// Backend's /user route returns the raw "employee" row (emp_code/emp_name/...), not
// user_id/full_name aliases — map to those actual keys. phone_number has no backing column
// yet, so it always comes back null.
data class UserDto(
    @SerializedName("emp_code")      val userId:      String,
    @SerializedName("emp_name")      val fullName:    String? = null,
    @SerializedName("emp_brch_code") val branchId:    String? = null,
    @SerializedName("emp_post")      val role:        String? = null,
    @SerializedName("phone_number")  val phoneNumber: String? = null
)
