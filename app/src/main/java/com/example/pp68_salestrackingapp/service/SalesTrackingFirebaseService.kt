package com.example.pp68_salestrackingapp.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
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
        Log.d("FCM", "New Token: $token")
        tokenManager.saveFcmToken(token)

        val userId = tokenManager.getUserData()?.userId ?: return

        // ✅ ส่ง token ไปยัง Server
        serviceScope.launch {
            try {
                apiService.updateFcmToken(
                    userId  = "eq.$userId",
                    updates = mapOf("fcm_token" to token)
                )
                Log.d("FCM", "อัปเดต FCM Token ไปยัง Server สำเร็จ")
            } catch (e: Exception) {
                Log.w("FCM", "ส่ง token ไม่สำเร็จ: ${e.message}")
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        
        // ตรวจสอบว่าได้รับข้อมูลหรือไม่
        Log.d("FCM", "ได้รับข้อความ Data: ${message.data}")
        message.notification?.let {
            Log.d("FCM", "ได้รับข้อความ Notification: Title=${it.title}, Body=${it.body}")
        }

        if (!tokenManager.isPushEnabled()) {
            Log.w("FCM", "Push ถูกปิดการใช้งานใน TokenManager")
            return
        }

        val title      = message.notification?.title ?: message.data["title"] ?: "Sales Tracking"
        val body       = message.notification?.body  ?: message.data["body"]  ?: ""
        val activityId = message.data["activity_id"]
        
        showNotification(title, body, activityId)
    }

    private fun showNotification(title: String, body: String, activityId: String?) {
        val channelId = "sales_tracking_channel"
        
        // ตรวจสอบว่ามีข้อมูลครบถ้วนสำหรับการสร้าง Notification หรือไม่
        if (title.isBlank() && body.isBlank()) {
            Log.w("FCM", "Title และ Body ว่างเปล่า ไม่สร้าง Notification")
            return
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            activityId?.let { putExtra("activity_id", it) }
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, System.currentTimeMillis().toInt(), intent,
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
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(channel)
        }
        
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher) 
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // ✅ แสดงบนหน้าจอล็อก
            .setContentIntent(pendingIntent)
            .build()
            
        manager.notify(System.currentTimeMillis().toInt(), notification)
        Log.d("FCM", "แสดง Notification สำเร็จ: $title")
    }
}
