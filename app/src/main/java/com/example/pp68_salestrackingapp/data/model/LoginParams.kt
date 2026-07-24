package com.example.pp68_salestrackingapp.data.model

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    @SerializedName("emp_code") val empCode: String,
    @SerializedName("password") val password: String
)

data class LoginResponse(
    @SerializedName("token")     val token:    String,
    @SerializedName("user_id")   val userId:   String? = null,
    @SerializedName("full_name") val fullName: String? = null,
    @SerializedName("role")      val role:     String? = null,
    @SerializedName("branch_id") val branchId: String? = null,
    @SerializedName("emp_type")  val empType:  String? = null,
    @SerializedName("employee")  val employee: EmployeeResponseDto? = null
)

data class EmployeeResponseDto(
    @SerializedName("emp_code") val empCode: String,
    @SerializedName("emp_name") val empName: String?,
    @SerializedName("emp_post_code") val empPostCode: String?,
    @SerializedName("emp_post") val empPost: String?,
    @SerializedName("emp_brch_code") val empBrchCode: String?,
    @SerializedName("emp_brch_name") val empBrchName: String?,
    @SerializedName("branch_name") val branchName: String?,
    @SerializedName("region") val region: String?
)

// สร้างกล่องมารับข้อมูลที่อยู่ข้างใน "user"
data class UserInfo(
    @SerializedName("userId") val userId: String,
    @SerializedName("fullName") val fullName: String,
    @SerializedName("role") val role: String,
    @SerializedName("email") val email: String
)