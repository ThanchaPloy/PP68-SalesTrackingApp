package com.example.pp68_salestrackingapp.data.remote

import com.example.pp68_salestrackingapp.data.model.*
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PATCH
import retrofit2.http.Query

interface AuthService {
    @POST("login-api")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("register-api")
    suspend fun register(@Body request: RegisterApiRequest): Response<LoginResponse>

    @POST("change-password-api")
    suspend fun changePassword(@Body request: ChangePasswordRequest): Response<ChangePasswordResponse>

    @PATCH("user/fcm-token")
    suspend fun updateFcmToken(@Body updates: Map<String, String>): Response<Map<String, String>>

    @GET("user")
    suspend fun getProjectSalesEmployees(
        @Query("is_project_sales") isProjectSales: String = "true"
    ): Response<List<UserDto>>
}