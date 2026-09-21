package com.example.pp68_salestrackingapp.data.repository

import com.example.pp68_salestrackingapp.data.local.AppDatabase
import com.example.pp68_salestrackingapp.data.model.*
import com.example.pp68_salestrackingapp.data.remote.ApiService
import com.example.pp68_salestrackingapp.data.remote.AuthService
import com.example.pp68_salestrackingapp.di.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import com.example.pp68_salestrackingapp.utils.SyncManager as OutboxSyncManager

class AuthRepository @Inject constructor(
    private val apiService: ApiService,
    private val authService: AuthService,
    private val tokenManager: TokenManager,
    private val database: AppDatabase,
    private val syncManager: SyncManager,
    private val outboxSyncManager: OutboxSyncManager
) {
    suspend fun login(username: String, password: String): kotlin.Result<LoginResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val response = authService.login(LoginRequest(username, password))
                if (response.isSuccessful && response.body() != null) {
                    val loginResp = response.body()!!

                    // 1. บันทึก Token แล้วพยายามดันงานที่ยังค้างขึ้นเซิร์ฟเวอร์ก่อนล้างเครื่อง
                    tokenManager.saveToken(loginResp.token)
                    // session หมดอายุจะลบแค่ token ไม่ลบ Room — เช็คอิน/บันทึกผลที่ทำตอนออฟไลน์
                    // จึงยังค้างอยู่ ถ้าล้างเลยโดยไม่ดันขึ้นก่อน งานนั้นหายถาวรเงียบ ๆ
                    // (logout กันเรื่องนี้ไว้แล้ว แต่ทางเข้า login ไม่เคยกัน)
                    // ตอนนี้มี token ใหม่ที่ใช้ได้แล้ว จึงมีโอกาสส่งสำเร็จ
                    try {
                        if (outboxSyncManager.hasPendingChanges()) outboxSyncManager.doSync()
                    } catch (_: Exception) {
                        // ยังออฟไลน์อยู่ก็ปล่อยผ่าน — ล้างต่อเพื่อไม่ให้ข้อมูลผู้ใช้เก่าค้างในเครื่อง
                    }
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
                authService.updateFcmToken(
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

    /**
     * รอ sync ข้อมูลที่ยังค้าง (outbox) ให้เสร็จก่อน แล้วค่อยล้าง DB+token — ป้องกันเช็คอิน/บันทึกผล
     * ที่ทำ offline ไว้หายถาวรตอน logout. ถ้า sync แล้วยังมีข้อมูลค้างอยู่ (เช่น ยังไม่มีเน็ต) จะไม่ยอม
     * logout เพื่อไม่ให้ข้อมูลหาย — คืน failure ให้ผู้เรียกแจ้งผู้ใช้แทน
     */
    suspend fun logout(): kotlin.Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                if (outboxSyncManager.hasPendingChanges()) {
                    outboxSyncManager.doSync()
                }
                if (outboxSyncManager.hasPendingChanges()) {
                    return@withContext kotlin.Result.failure(
                        Exception("มีข้อมูลที่ยังไม่ได้ซิงค์กับเซิร์ฟเวอร์ กรุณาเชื่อมต่ออินเทอร์เน็ตแล้วลองอีกครั้ง")
                    )
                }
                database.clearAllTables()
                tokenManager.clearToken()
                kotlin.Result.success(Unit)
            } catch (e: Exception) {
                kotlin.Result.failure(e)
            }
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
