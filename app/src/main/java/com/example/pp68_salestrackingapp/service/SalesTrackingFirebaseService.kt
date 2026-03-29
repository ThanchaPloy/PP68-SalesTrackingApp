package com.example.pp68_salestrackingapp.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.pp68_salestrackingapp.MainActivity
import com.example.pp68_salestrackingapp.R
import com.example.pp68_salestrackingapp.di.TokenManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.example.pp68_salestrackingapp.data.remote.ApiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


@AndroidEntryPoint
class SalesTrackingFirebaseService : FirebaseMessagingService() {

    @Inject lateinit var tokenManager: TokenManager
    @Inject lateinit var apiService:   ApiService

    // ✅ สร้าง scope สำหรับ coroutine
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        tokenManager.saveFcmToken(token)

        val userId = tokenManager.getUserData()?.userId ?: return

        // ✅ ใช้ serviceScope แทน GlobalScope
        serviceScope.launch {
            try {
                apiService.updateFcmToken(
                    userId  = "eq.$userId",
                    updates = mapOf("fcm_token" to token)
                )
            } catch (e: Exception) {
                android.util.Log.w("FCM", "ส่ง token ไม่สำเร็จ: ${e.message}")
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        if (!tokenManager.isPushEnabled()) return

        val title      = message.notification?.title ?: message.data["title"] ?: "Sales Tracking"
        val body       = message.notification?.body  ?: message.data["body"]  ?: ""
        val activityId = message.data["activity_id"]
        showNotification(title, body, activityId)
    }

    private fun showNotification(title: String, body: String, activityId: String?) {
        val channelId = "sales_tracking_channel"
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            activityId?.let { putExtra("activity_id", it) }
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Sales Tracking Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "แจ้งเตือนการนัดหมายและรายงาน"
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}