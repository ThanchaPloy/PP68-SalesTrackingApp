package com.example.pp68_salestrackingapp.receiver

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.pp68_salestrackingapp.MainActivity
import com.example.pp68_salestrackingapp.R
import com.example.pp68_salestrackingapp.utils.NotificationChannels

class AppointmentAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val activityId = intent.getStringExtra("activity_id") ?: return
        val companyName = intent.getStringExtra("company_name") ?: "สถานที่นัดหมาย"
        val topic = intent.getStringExtra("topic") ?: "นัดหมายพบลูกค้า"
        val plannedTime = intent.getStringExtra("planned_time") ?: ""
        val leadMinutes = intent.getIntExtra("lead_minutes", 30)

        Log.d("AppointmentAlarm", "Received Alarm for Activity: $activityId ($topic) - Lead: $leadMinutes mins")

        NotificationChannels.ensureCreated(context)

        val mainIntent = Intent(
            Intent.ACTION_VIEW,
            android.net.Uri.parse("salestracking://activity/$activityId")
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            activityId.hashCode(),
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "⏰ มีนัดหมายในอีก $leadMinutes นาที!"
        val body = "$topic ที่ $companyName (เวลา $plannedTime)"

        val notification = NotificationCompat.Builder(context, NotificationChannels.APPOINTMENT_TIME_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(activityId.hashCode(), notification)
    }
}
