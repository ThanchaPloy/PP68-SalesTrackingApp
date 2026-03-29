package com.example.pp68_salestrackingapp.data.repository

import com.example.pp68_salestrackingapp.data.model.*
import com.example.pp68_salestrackingapp.data.remote.ApiService
import com.example.pp68_salestrackingapp.data.remote.AuthService
import com.example.pp68_salestrackingapp.di.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class AuthRepository @Inject constructor(
    private val apiService: ApiService,
    private val authService: AuthService,
    private val tokenManager: TokenManager
) {
    suspend fun login(email: String, password: String): kotlin.Result<LoginResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val response = authService.login(LoginRequest(email, password))
                if (response.isSuccessful && response.body() != null) {
                    val loginResp = response.body()!!
                    tokenManager.saveToken(loginResp.token)

                    // ✅ ดึงข้อมูล user เพิ่มเติมจาก PostgREST
                    val userDetail = fetchUserDetail(loginResp.userId)

                    val authUser = AuthUser(
                        userId     = loginResp.userId,
                        email      = email,
                        role       = loginResp.role,
                        teamId     = userDetail?.branchId,
                        fullName   = userDetail?.fullName,
                        branchName = userDetail?.branchName
                    )
                    tokenManager.saveUserData(authUser)

                    val fcmToken = tokenManager.getFcmToken()
                    if (!fcmToken.isNullOrBlank()) {
                        try {
                            apiService.updateFcmToken(
                                userId  = "eq.${loginResp.userId}",
                                updates = mapOf("fcm_token" to fcmToken)
                            )
                        } catch (e: Exception) {
                            // ถ้าไม่สำเร็จก็ไม่เป็นไร
                        }
                    }

                    kotlin.Result.success(loginResp)
                } else {
                    kotlin.Result.failure(Exception("อีเมลหรือรหัสผ่านไม่ถูกต้อง"))
                }
            } catch (e: Exception) {
                kotlin.Result.failure(e)
            }
        }
    }

    suspend fun register(
        email:    String,
        password: String,
        fullName: String,
        branchId: String
    ): kotlin.Result<LoginResponse> {  // ✅ เปลี่ยนจาก RegisterResponse
        return withContext(Dispatchers.IO) {
            try {
                val request  = RegisterApiRequest(
                    email    = email.trim().lowercase(),
                    password = password,
                    fullName = fullName.trim(),
                    branchId = branchId
                )
                val response = authService.register(request)
                if (response.isSuccessful && response.body() != null) {
                    val loginResp = response.body()!!
                    tokenManager.saveToken(loginResp.token)
                    val userDetail = fetchUserDetail(loginResp.userId)
                    val authUser = AuthUser(
                        userId     = loginResp.userId,
                        email      = email,
                        role       = loginResp.role,
                        teamId     = userDetail?.branchId ?: branchId,
                        fullName   = fullName,
                        branchName = userDetail?.branchName
                    )
                    tokenManager.saveUserData(authUser)
                    kotlin.Result.success(loginResp)
                } else {
                    val errBody = response.errorBody()?.string() ?: ""
                    val errMsg  = try {
                        org.json.JSONObject(errBody).getString("error")
                    } catch (e: Exception) { "ลงทะเบียนไม่สำเร็จ" }
                    kotlin.Result.failure(Exception(errMsg))
                }
            } catch (e: Exception) {
                kotlin.Result.failure(e)
            }
        }
    }

    private suspend fun fetchUserDetail(userId: String): UserDetailResult? {
        return try {
            val userResp = apiService.getUserById("eq.$userId")
            val user = userResp.body()?.firstOrNull() ?: return null

            // ✅ เรียก getBranchById แทน getBranches
            val branchResp = user.branchId?.let {
                apiService.getBranchById("eq.$it").body()?.firstOrNull()
            }

            UserDetailResult(
                fullName   = user.fullName,
                branchId   = user.branchId,
                branchName = branchResp?.branchName
            )
        } catch (e: Exception) { null }
    }

    private data class UserDetailResult(
        val fullName:   String?,
        val branchId:   String?,
        val branchName: String?
    )

    fun logout() {
        tokenManager.clearToken()
    }

    fun isUserLoggedIn(): Boolean {
        return !tokenManager.getToken().isNullOrEmpty()
    }

    fun currentUser(): AuthUser? {
        return tokenManager.getUserData()
    }

    fun updateLocalUser(user: AuthUser) {
        tokenManager.saveUserData(user)
    }

    suspend fun changePassword(
        oldPassword: String,
        newPassword: String
    ): kotlin.Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val userData = currentUser() ?: return@withContext kotlin.Result.failure(Exception("กรุณา login ใหม่"))
                val userId = userData.userId

                val response = authService.changePassword(
                    ChangePasswordRequest(userId, oldPassword, newPassword)
                )

                if (response.isSuccessful && response.body() != null) {
                    kotlin.Result.success(response.body()!!.message)
                } else {
                    val errBody = response.errorBody()?.string() ?: ""
                    val errMsg  = try {
                        org.json.JSONObject(errBody).getString("error")
                    } catch (e: Exception) { "เปลี่ยนรหัสผ่านไม่สำเร็จ" }
                    kotlin.Result.failure(Exception(errMsg))
                }
            } catch (e: Exception) {
                kotlin.Result.failure(e)
            }
        }
    }


}
