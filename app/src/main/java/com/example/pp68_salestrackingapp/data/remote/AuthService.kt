package com.example.pp68_salestrackingapp.data.remote


import com.example.pp68_salestrackingapp.data.model.*
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthService {
    @POST("login-api")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("register-api")
    suspend fun register(@Body request: RegisterApiRequest): Response<LoginResponse>

    @POST("change-password-api")
    suspend fun changePassword(@Body request: ChangePasswordRequest): Response<ChangePasswordResponse>
}