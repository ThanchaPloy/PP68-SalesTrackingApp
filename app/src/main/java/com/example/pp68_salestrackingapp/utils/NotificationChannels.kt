package com.example.pp68_salestrackingapp.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationChannels {
    const val SALES_TRACKING_CHANNEL_ID = "sales_tracking_channel"

    // ✅ ต้องสร้าง channel นี้ตั้งแต่แอปเปิดครั้งแรก ไม่ใช่รอตอน onMessageReceived
    // เพราะถ้าแอปอยู่ background/killed ตอน FCM ส่ง payload ที่มีทั้ง notification + data block มา
    // ระบบ Android จะแสดง notification เองโดยไม่เรียก onMessageReceived เลย ถ้า channel ยังไม่มีอยู่จริง
    // notification จะถูก drop เงียบๆ ไม่แสดงอะไรเลย
    fun ensureCreated(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            SALES_TRACKING_CHANNEL_ID,
            "Sales Tracking Notifications",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "แจ้งเตือนการนัดหมายและรายงาน"
            enableVibration(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }
}
