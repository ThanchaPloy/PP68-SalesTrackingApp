package com.example.pp68_salestrackingapp.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationChannels {
    const val SALES_TRACKING_CHANNEL_ID = "sales_tracking_channel"
    const val LOCATION_ALERT_CHANNEL_ID = "location_alert_channel"
    const val APPOINTMENT_TIME_CHANNEL_ID = "appointment_time_channel"

    fun ensureCreated(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val salesChannel = NotificationChannel(
            SALES_TRACKING_CHANNEL_ID,
            "Sales Tracking Notifications",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "แจ้งเตือนการนัดหมายและรายงาน"
            enableVibration(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }

        val locationChannel = NotificationChannel(
            LOCATION_ALERT_CHANNEL_ID,
            "แจ้งเตือนพิกัดนัดหมาย (Location Alert)",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "แจ้งเตือนเมื่อเดินทางเข้าใกล้สถานที่ปักหมุดนัดหมาย"
            enableVibration(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }

        val appointmentChannel = NotificationChannel(
            APPOINTMENT_TIME_CHANNEL_ID,
            "แจ้งเตือนเวลานัดหมาย (Appointment Time Alert)",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "แจ้งเตือนเมื่อใกล้ถึงเวลานัดหมาย"
            enableVibration(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }

        manager.createNotificationChannel(salesChannel)
        manager.createNotificationChannel(locationChannel)
        manager.createNotificationChannel(appointmentChannel)
    }
}
