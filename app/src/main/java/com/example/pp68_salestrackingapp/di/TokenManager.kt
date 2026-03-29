package com.example.pp68_salestrackingapp.di

import android.content.Context
import android.content.SharedPreferences
import com.example.pp68_salestrackingapp.data.model.AuthUser
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("sales_prefs", Context.MODE_PRIVATE)

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
            branchName = prefs.getString("user_branch", null)
        )

    }

    fun clearToken() {
        prefs.edit().clear().apply()
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
}