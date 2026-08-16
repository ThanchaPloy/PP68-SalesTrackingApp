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
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent) ?: return
        if (geofencingEvent.hasError()) {
            Log.e("GeofenceReceiver", "Geofencing error code: ${geofencingEvent.errorCode}")
            return
        }

        val geofenceTransition = geofencingEvent.geofenceTransition
        if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER || geofenceTransition == Geofence.GEOFENCE_TRANSITION_DWELL) {
            val triggeringGeofences = geofencingEvent.triggeringGeofences ?: emptyList()
            for (geofence in triggeringGeofences) {
                val requestId = geofence.requestId // Format: "ACT_<id>|<companyName>|<plannedDate>|<plannedTime>"
                Log.d("GeofenceReceiver", "Triggered Geofence: $requestId")

                val parts = requestId.split("|")
                val activityId = parts.getOrNull(0)?.removePrefix("ACT_") ?: ""
                val companyName = parts.getOrNull(1) ?: "สถานที่นัดหมาย"
                val plannedDate = parts.getOrNull(2) ?: ""
                val plannedTime = parts.getOrNull(3) ?: ""

                // Prevent multiple triggers in a short time
                val prefs = context.getSharedPreferences("GeofencePrefs", Context.MODE_PRIVATE)
                val lastTriggerTime = prefs.getLong("last_trigger_$activityId", 0L)
                val currentTime = System.currentTimeMillis()
                
                // If triggered within the last 1 hour (3600000 ms), ignore it to prevent spam
                if (currentTime - lastTriggerTime < 3600000L) {
                    Log.d("GeofenceReceiver", "Geofence for $activityId ignored (already triggered recently)")
                    continue
                }
                
                prefs.edit().putLong("last_trigger_$activityId", currentTime).apply()

                val title = "📍 คุณอยู่ใกล้สถานที่นัดหมาย!"
                val dateInfo = if (plannedDate.isNotBlank()) "วันที่ $plannedDate $plannedTime" else ""
                val body = "สถานที่: $companyName $dateInfo"

                showLocationNotification(context, title, body, activityId)
            }
        }
    }

    private fun showLocationNotification(context: Context, title: String, body: String, activityId: String) {
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

        val notification = NotificationCompat.Builder(context, NotificationChannels.LOCATION_ALERT_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(activityId.hashCode(), notification)
    }
}
