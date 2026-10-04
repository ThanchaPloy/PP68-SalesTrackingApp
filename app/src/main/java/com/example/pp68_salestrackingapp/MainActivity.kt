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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.pp68_salestrackingapp.data.remote.ApiService
import com.example.pp68_salestrackingapp.di.TokenManager
import com.example.pp68_salestrackingapp.utils.SyncManager
import com.example.pp68_salestrackingapp.utils.SyncStatusNavigation
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var apiService: ApiService
    @Inject lateinit var tokenManager: TokenManager
    @Inject lateinit var syncManager: SyncManager

    // ผลของการขอสิทธิ์ไม่ต้องทำอะไรต่อ — การแจ้งเตือนนัดหมายตั้งไว้ล่วงหน้าด้วย AlarmManager
    // ตั้งแต่ตอนสร้างนัด ระบบจะยิงเองเมื่อถึงเวลาถ้าผู้ใช้อนุญาต
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }



    private var lastSyncMs = 0L

    override fun onResume() {
        super.onResume()
        val now = System.currentTimeMillis()
        if (tokenManager.getToken() != null && now - lastSyncMs > 60_000) {
            lastSyncMs = now
            syncManager.scheduleSync(com.example.pp68_salestrackingapp.utils.SyncTrigger.APP_FOREGROUND)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        com.example.pp68_salestrackingapp.utils.NotificationChannels.ensureCreated(this)
        askNotificationPermission()


        setContent {
            SalesTrackingTheme {
                SalesTrackingApp(
                    initialSettingsScreen = SyncStatusNavigation.destination(
                        intent.getBooleanExtra(SyncStatusNavigation.EXTRA_OPEN_SYNC_STATUS, false)
                    )
                )
            }
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
