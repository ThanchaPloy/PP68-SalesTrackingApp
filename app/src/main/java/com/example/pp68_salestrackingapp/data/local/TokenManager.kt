package com.example.pp68_salestrackingapp.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

    fun saveToken(token: String) = prefs.edit().putString("jwt_token", token).apply()
    fun getToken(): String?      = prefs.getString("jwt_token", null)
    fun clearToken()             = prefs.edit().remove("jwt_token").apply()
    fun isLoggedIn(): Boolean    = getToken() != null
}
