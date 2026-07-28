package com.example.pp68_salestrackingapp.data.repository

import com.example.pp68_salestrackingapp.data.local.AppDatabase
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
    private val tokenManager: TokenManager,
    private val database: AppDatabase,
    private val syncManager: SyncManager
) {
    suspend fun login(username: String, password: String): kotlin.Result<LoginResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val response = authService.login(LoginRequest(username, password))
                if (response.isSuccessful && response.body() != null) {
                    val loginResp = response.body()!!

                    // 1. บันทึก Token และล้างข้อมูลเก่าในเครื่อง
                    tokenManager.saveToken(loginResp.token)
                    database.clearAllTables()

                    // 2. ดึงข้อมูลผู้ใช้ (รองรับทั้ง Ktor/PostgREST backend และ Node.js backend)
                    val finalUserId = loginResp.employee?.empCode ?: loginResp.userId ?: ""
                    val finalFullName = loginResp.employee?.empName ?: loginResp.fullName
                    val finalRole = loginResp.employee?.empPost ?: loginResp.role ?: ""
                    val finalBranchId = loginResp.employee?.empBrchCode ?: loginResp.branchId ?: ""
                    val finalEmpType = loginResp.employee?.empPost ?: loginResp.empType

                    val authUser = AuthUser(
                        userId     = finalUserId,
                        email      = username,
                        role       = finalRole,
                        teamId     = finalBranchId,
                        fullName   = finalFullName,
                        branchName = null,
                        empType    = finalEmpType
                    )
                    tokenManager.saveUserData(authUser)

                    // 3. Sync ข้อมูลทั้งหมดตามสาขาของผู้ใช้
                    syncManager.syncAll(
                        userId   = finalUserId,
                        branchId = finalBranchId
                    )

                    // 4. อัปเดต FCM Token สำหรับการแจ้งเตือน
                    updateFcmTokenOnServer(finalUserId)

                    kotlin.Result.success(loginResp)
                } else {
                    kotlin.Result.failure(Exception("รหัสพนักงานหรือรหัสผ่านไม่ถูกต้อง"))
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
    ): kotlin.Result<LoginResponse> {
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
                    database.clearAllTables()

                    val finalUserId = loginResp.employee?.empCode ?: loginResp.userId ?: ""
                    val finalRole = loginResp.employee?.empPost ?: loginResp.role ?: ""

                    val userDetail = fetchUserDetail(finalUserId)
                    val authUser = AuthUser(
                        userId     = finalUserId,
                        email      = email,
                        role       = finalRole,
                        teamId     = userDetail?.branchId ?: branchId,
                        fullName   = fullName,
                        branchName = userDetail?.branchName
                    )
                    tokenManager.saveUserData(authUser)

                    // Sync ข้อมูลเริ่มต้นหลังสมัครสมาชิก
                    syncManager.syncAll(
                        userId   = finalUserId,
                        branchId = userDetail?.branchId ?: branchId
                    )

                    // อัปเดต FCM Token สำหรับผู้ใช้ใหม่
                    updateFcmTokenOnServer(finalUserId)

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

    private suspend fun updateFcmTokenOnServer(userId: String) {
        val fcmToken = tokenManager.getFcmToken()
        if (!fcmToken.isNullOrBlank()) {
            try {
                apiService.updateFcmToken(
                    userId  = "eq.$userId",
                    updates = mapOf("fcm_token" to fcmToken)
                )
            } catch (e: Exception) {
                // หากอัปเดตไม่สำเร็จก็ให้ทำงานส่วนหลักต่อไปได้
            }
        }
    }

    private suspend fun fetchUserDetail(userId: String): UserDetailResult? {
        return try {
            val userResp = apiService.getUserById("eq.$userId")
            val user = userResp.body()?.firstOrNull() ?: return null

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

    suspend fun logout() {
        withContext(Dispatchers.IO) {
            database.clearAllTables()
            tokenManager.clearToken()
        }
    }

    fun isUserLoggedIn(): Boolean = !tokenManager.getToken().isNullOrEmpty()

    val sessionExpired = tokenManager.sessionExpired

    fun currentUser(): AuthUser? = tokenManager.getUserData()

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
                    ChangePasswordRequest(userId, oldPassword, newPassword)  // userId == empCode
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
