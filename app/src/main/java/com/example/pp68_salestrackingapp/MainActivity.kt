package com.example.pp68_salestrackingapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.pp68_salestrackingapp.ui.navigation.SalesTrackingApp
import com.example.pp68_salestrackingapp.ui.theme.SalesTrackingTheme
import dagger.hilt.android.AndroidEntryPoint

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.pp68_salestrackingapp.data.remote.ApiService
import com.example.pp68_salestrackingapp.di.TokenManager
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var apiService: ApiService
    @Inject lateinit var tokenManager: TokenManager

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            getFcmToken()
        }
    }

    private val callLogPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Log.d("CallLog", "READ_CALL_LOG permission granted")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        askNotificationPermission()
        askCallLogPermission()

        setContent {
            SalesTrackingTheme {
                SalesTrackingApp()
            }
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    getFcmToken()
                }
                else -> {
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            getFcmToken()
        }
    }

    private fun askCallLogPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
                callLogPermissionLauncher.launch(Manifest.permission.READ_CALL_LOG)
            }
        }
    }

    private fun getFcmToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                Log.d("FCM", "Current Token: $token")
                tokenManager.saveFcmToken(token)
                
                // ✅ ส่ง Token ไปที่ Server จริงๆ
                val userId = tokenManager.getUserData()?.userId
                if (userId != null) {
                    lifecycleScope.launch {
                        try {
                            apiService.updateFcmToken("eq.$userId", mapOf("fcm_token" to token))
                            Log.d("FCM", "ส่ง Token ไป Server สำเร็จ")
                        } catch (e: Exception) {
                            Log.e("FCM", "ส่ง Token ล้มเหลว: ${e.message}")
                        }
                    }
                }
            }
        }
    }
}
