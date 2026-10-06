package com.example.pp68_salestrackingapp.data.repository

import android.util.Log
import com.example.pp68_salestrackingapp.data.local.AppDatabase
import com.example.pp68_salestrackingapp.data.model.*
import com.example.pp68_salestrackingapp.data.remote.ApiService
import com.example.pp68_salestrackingapp.data.remote.AuthService
import com.example.pp68_salestrackingapp.di.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import javax.inject.Inject
import com.example.pp68_salestrackingapp.utils.SyncManager as OutboxSyncManager

class AuthRepository @Inject constructor(
    private val apiService: ApiService,
    private val authService: AuthService,
    private val tokenManager: TokenManager,
    private val database: AppDatabase,
    private val outboxSyncManager: OutboxSyncManager
) {
    suspend fun login(
        username: String,
        password: String,
        discardPreviousData: Boolean = false
    ): kotlin.Result<LoginResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val response = authService.login(LoginRequest(username, password))
                if (response.isSuccessful && response.body() != null) {
                    val loginResp = response.body()!!
                    val previousUserId = tokenManager.getUserData()?.userId
                        ?: tokenManager.getLocalDataOwner()
                    val finalUserId = loginResp.employee?.empCode ?: loginResp.userId ?: ""
                    val finalFullName = loginResp.employee?.empName ?: loginResp.fullName
                    val finalRole = loginResp.employee?.empPost ?: loginResp.role ?: ""
                    val finalBranchId = loginResp.employee?.empBrchCode ?: loginResp.branchId ?: ""
                    val finalEmpType = loginResp.employee?.empPost ?: loginResp.empType
                    val pending = outboxSyncManager.pendingSummary()
                    val rejected = outboxSyncManager.rejectedSummary()
                    val conflicts = database.syncConflictDao().countAll()
                    val hasProtectedLocalData = pending.isNotEmpty() || rejected.isNotEmpty() || conflicts > 0
                    val isSameUser = previousUserId == finalUserId ||
                        (previousUserId.isNullOrBlank() && !hasProtectedLocalData)

                    // สำคัญ: ตรวจและบล็อกก่อนเขียน token ใหม่ เพื่อไม่ให้งานของ A ถูกส่งด้วยสิทธิ์ของ B
                    if (!isSameUser && hasProtectedLocalData && !discardPreviousData) {
                        return@withContext kotlin.Result.failure(
                            AccountSwitchBlockedException(pending, rejected, conflicts)
                        )
                    }
                    if (!isSameUser) {
                        outboxSyncManager.discardPendingAttachmentFiles()
                        database.clearAllTables()
                    }

                    tokenManager.saveToken(loginResp.token)

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
                    tokenManager.saveLocalDataOwner(finalUserId)

                    // Login จบตรงนี้ ไม่รอจำนวนข้อมูล/ความเร็วเครือข่าย งานจริงทำใน WorkManager
                    outboxSyncManager.scheduleSync(com.example.pp68_salestrackingapp.utils.SyncTrigger.LOGIN)
                    outboxSyncManager.scheduleDownload()

                    kotlin.Result.success(loginResp)
                } else {
                    kotlin.Result.failure(Exception("รหัสพนักงานหรือรหัสผ่านไม่ถูกต้อง"))
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                kotlin.Result.failure(e)
            }
        }
    }

    class AccountSwitchBlockedException(
        val pending: List<Pair<String, Int>>,
        val rejected: List<com.example.pp68_salestrackingapp.data.model.SyncRejection>,
        val conflicts: Int = 0
    ) : Exception(
        buildString {
            append("มีข้อมูลของบัญชีก่อนหน้าค้างอยู่ในเครื่อง")
            if (pending.isNotEmpty()) append("\nรอส่ง: " + pending.joinToString { "${it.first} ${it.second}" })
            if (rejected.isNotEmpty()) {
                val groups = rejected.groupingBy { it.entityType to (it.reason ?: "ไม่ทราบสาเหตุ") }.eachCount()
                append("\nต้องตรวจสอบ:\n")
                append(groups.entries.joinToString("\n") { (key, count) -> "• ${key.first}: ${key.second} ($count รายการ)" })
            }
            if (conflicts > 0) append("\nข้อมูลออฟไลน์ชนกับเซิร์ฟเวอร์: $conflicts รายการ")
            append("\nหากลบ ข้อมูลเหล่านี้จะกู้คืนไม่ได้")
        }
    )

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
                    outboxSyncManager.discardPendingAttachmentFiles()
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

                    tokenManager.saveLocalDataOwner(finalUserId)
                    outboxSyncManager.scheduleDownload()

                    kotlin.Result.success(loginResp)
                } else {
                    // ✅ เหมือน changePassword() — backend ตอบ { "error": code, "message": ข้อความจริง }
                    // ต้องอ่าน "message" ไม่งั้นโชว์ code สั้นๆ เช่น "CONFLICT" แทนเหตุผลจริงให้ผู้ใช้เห็น
                    val errBody = response.errorBody()?.string() ?: ""
                    val errMsg  = try {
                        org.json.JSONObject(errBody).getString("message")
                    } catch (e: Exception) { "ลงทะเบียนไม่สำเร็จ" }
                    kotlin.Result.failure(Exception(errMsg))
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                kotlin.Result.failure(e)
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

    /** logout ยังไปต่อได้ แต่ต้องให้ผู้ใช้ยืนยันก่อน — ต่างจาก failure อื่นที่เป็นทางตัน */
    class PendingRejectionException(message: String, val count: Int) : Exception(message)

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
    suspend fun logout(force: Boolean = false): kotlin.Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                if (outboxSyncManager.hasPendingChanges()) {
                    outboxSyncManager.doSync()
                }
                // บอกให้ชัดว่าค้างอะไรอยู่ — ข้อความเดิมโทษอินเทอร์เน็ตอย่างเดียว ซึ่งชี้ทางผิดทุกครั้ง
                // ที่สาเหตุจริงคือแถวที่เซิร์ฟเวอร์ปฏิเสธ (เช่น 403 ไม่มีสิทธิ์ หรือ 404 ของหายไปแล้ว)
                // ซึ่งจะไม่หายไปเองแม้เน็ตดี และผู้ใช้ก็ไม่มีทางรู้ว่าต้องไปดูอะไร
                val pending = outboxSyncManager.pendingSummary()
                if (pending.isNotEmpty()) {
                    val detail = pending.joinToString(", ") { (name, count) -> "$name $count รายการ" }
                    Log.w("AuthRepository", "logout ถูกบล็อกเพราะยังซิงค์ไม่สำเร็จ: $detail")
                    return@withContext kotlin.Result.failure(
                        Exception(
                            "ยังซิงค์ข้อมูลขึ้นเซิร์ฟเวอร์ไม่สำเร็จ ($detail) " +
                                "ถ้าเชื่อมต่ออินเทอร์เน็ตอยู่แล้วแต่ยังขึ้นข้อความนี้ แจ้งผู้ดูแลระบบได้เลย"
                        )
                    )
                }

                // แถวที่เซิร์ฟเวอร์ปฏิเสธถาวรไม่บล็อก logout (บล็อกไปก็ไม่มีวันผ่าน) แต่ห้ามเงียบ —
                // clearAllTables ด้านล่างจะลบทิ้งจริง ๆ ผู้ใช้จึงต้องได้เห็นว่าจะเสียอะไรและยืนยันเอง
                val rejected = outboxSyncManager.rejectedSummary()
                if (rejected.isNotEmpty() && !force) {
                    val reasons = rejected.groupingBy { it.reason ?: "ไม่ทราบสาเหตุ" }.eachCount()
                        .entries.joinToString("\n") { (reason, count) -> "• $reason ($count รายการ)" }
                    Log.w("AuthRepository", "logout: มีแถวที่ถูกปฏิเสธถาวร ${rejected.size} รายการ")
                    return@withContext kotlin.Result.failure(
                        PendingRejectionException(
                            "มีข้อมูล ${rejected.size} รายการที่เซิร์ฟเวอร์ไม่รับ และจะหายไปถ้าออกจากระบบตอนนี้\n$reasons",
                            rejected.size
                        )
                    )
                }

                val conflicts = database.syncConflictDao().countAll()
                if (conflicts > 0 && !force) {
                    return@withContext kotlin.Result.failure(
                        PendingRejectionException(
                            "มีข้อมูลออฟไลน์ $conflicts รายการที่ชนกับข้อมูลบนเซิร์ฟเวอร์ และจะหายไปถ้าออกจากระบบตอนนี้",
                            conflicts
                        )
                    )
                }

                outboxSyncManager.discardPendingAttachmentFiles()
                database.clearAllTables()
                tokenManager.clearToken()
                // Room ว่างแล้ว จึงไม่มีเจ้าของข้อมูลในเครื่องให้จำอีก
                tokenManager.clearLocalDataOwner()
                com.example.pp68_salestrackingapp.utils.ProjectStages.clearServerData()
                com.example.pp68_salestrackingapp.utils.LossReasons.clearServerData()
                com.example.pp68_salestrackingapp.utils.DealFactors.clearServerData()
                kotlin.Result.success(Unit)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
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
                    // ✅ backend (StatusPages.kt) ตอบ { "error": "UNAUTHORIZED", "message": "Wrong current
                    // password" } — "error" เป็นแค่ code สั้นๆ ไม่ใช่ข้อความสำหรับผู้ใช้ ต้องอ่าน "message"
                    // ไม่งั้นรหัสผ่านเก่าผิดจะโชว์คำว่า "UNAUTHORIZED" ตรงๆ ให้ผู้ใช้เห็นแทนเหตุผลจริง
                    val errBody = response.errorBody()?.string() ?: ""
                    val errMsg  = try {
                        org.json.JSONObject(errBody).getString("message")
                    } catch (e: Exception) { "เปลี่ยนรหัสผ่านไม่สำเร็จ" }
                    kotlin.Result.failure(Exception(errMsg))
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                kotlin.Result.failure(e)
            }
        }
    }
}
