package com.example.pp68_salestrackingapp.di

import android.content.Context
import android.content.SharedPreferences
import com.example.pp68_salestrackingapp.data.model.AuthUser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("sales_prefs", Context.MODE_PRIVATE)

    init {
        checkAppVersionAndForceRelogin()
    }

    private fun checkAppVersionAndForceRelogin() {
        try {
            val lastVersion = prefs.getInt("last_installed_version_code", -1)
            val currentVersion = com.example.pp68_salestrackingapp.BuildConfig.VERSION_CODE

            if (lastVersion != currentVersion) {
                // ✅ APK มีการอัปเดตเวอร์ชันใหม่ -> เคลียร์ Session ให้บังคับผู้ใช้ Login ใหม่
                clearToken()
                // ⚠️ ใช้ .commit() แทน .apply() เพื่อบังคับเซฟลง Disk ทันที
                // ป้องกันปัญหา "ปัดแอปออกแล้วเซฟไม่ทัน" ทำให้มันเคลียร์ Token ซ้ำในรอบหน้า
                prefs.edit().putInt("last_installed_version_code", currentVersion).commit()
            }
        } catch (_: Exception) {}
    }

    // ✅ ยิง event เมื่อ token หมดอายุ/ไม่ถูกต้อง (401 จาก endpoint ที่ต้อง auth) — ให้ NavGraph
    // เด้งกลับไปหน้า Login ทันที แทนที่จะปล่อยให้แอปเงียบๆ ใช้งานไม่ได้โดยไม่มีคำอธิบาย
    private val _sessionExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val sessionExpired: SharedFlow<Unit> = _sessionExpired.asSharedFlow()

    fun notifySessionExpired() {
        clearToken()
        _sessionExpired.tryEmit(Unit)
    }

    fun saveToken(token: String) {
        prefs.edit().putString("jwt_token", token).apply()
    }

    fun getToken(): String? {
        return prefs.getString("jwt_token", null)
    }

    fun saveUserData(user: AuthUser) {
        prefs.edit().apply {
            putString("user_id", user.userId)
            putString("user_email", user.email)
            putString("user_role", user.role)
            putString("user_team", user.teamId)
            putString("user_name",   user.fullName)
            putString("user_branch", user.branchName)
            putString("emp_type",    user.empType)
        }.apply()
    }

    fun getUserData(): AuthUser? {
        val userId = prefs.getString("user_id", null) ?: return null
        return AuthUser(
            userId     = userId,
            email      = prefs.getString("user_email",  "") ?: "",
            role       = prefs.getString("user_role",   "sale") ?: "sale",
            teamId     = prefs.getString("user_team",   null),
            fullName   = prefs.getString("user_name",   null),
            branchName = prefs.getString("user_branch", null),
            empType    = prefs.getString("emp_type",    null)
        )
    }

    fun getEmpType(): String? = prefs.getString("emp_type", null)

    fun clearToken() {
        prefs.edit().apply {
            remove("jwt_token")
            remove("user_id")
            remove("user_email")
            remove("user_role")
            remove("user_team")
            remove("user_name")
            remove("user_branch")
            remove("emp_type")
        }.apply()
    }

    // ✅ เพิ่ม FCM token functions
    fun saveFcmToken(token: String) {
        prefs.edit().putString("fcm_token", token).apply()
    }

    fun getFcmToken(): String? {
        return prefs.getString("fcm_token", null)
    }

    fun savePushEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("push_enabled", enabled).apply()
    }

    fun isPushEnabled(): Boolean {
        return prefs.getBoolean("push_enabled", true) // default = เปิด
    }

    fun saveVisitReminderEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("visit_reminder_enabled", enabled).apply()
    }

    fun isVisitReminderEnabled(): Boolean {
        return prefs.getBoolean("visit_reminder_enabled", true) // default = เปิด
    }

    // ✅ กันส่ง call log ซ้ำข้ามการเปิดแอปใหม่ — ต้อง persist ไว้ข้าม process ไม่ใช่แค่ในหน่วยความจำ
    fun saveLastCallLogSyncTime(timeMs: Long) {
        prefs.edit().putLong("last_call_log_sync_time", timeMs).apply()
    }

    fun getLastCallLogSyncTime(): Long {
        return prefs.getLong("last_call_log_sync_time", 0L)
    }
}