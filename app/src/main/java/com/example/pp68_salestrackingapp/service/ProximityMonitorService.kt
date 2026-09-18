package com.example.pp68_salestrackingapp.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.pp68_salestrackingapp.R
import com.example.pp68_salestrackingapp.data.local.AppDatabase
import com.example.pp68_salestrackingapp.utils.NotificationChannels
import com.example.pp68_salestrackingapp.utils.haversineMeters
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Collections
import javax.inject.Inject
import com.example.pp68_salestrackingapp.data.model.SalesActivity

/**
 * แทนที่ Play Services Geofencing ด้วยการติดตามพิกัดเองผ่าน Foreground Service —
 * ทำงานเฉพาะช่วงที่มีนัดหมาย "วันนี้" ที่ยังไม่เช็คอินเท่านั้น แล้วหยุดตัวเองเมื่อไม่มีอะไรต้องติดตามแล้ว
 * (ต่างจาก Play Services เดิมที่ค้างไว้ตลอดไปโดย OS จัดการให้แบบประหยัดแบตในพื้นหลัง)
 */
@AndroidEntryPoint
class ProximityMonitorService : Service() {

    @Inject lateinit var db: AppDatabase

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var locationManager: LocationManager? = null
    // location callbacks can fire from more than one thread — keep this thread-safe
    private val notifiedActivityIds = Collections.synchronizedSet(mutableSetOf<String>())

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            serviceScope.launch { checkProximity(location) }
        }
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildMonitoringNotification())
        startLocationUpdates()

        // ตรวจสอบเป็นระยะแยกจาก location callback — เผื่อไม่มีนัดหมายเลยตั้งแต่แรก (ควรหยุดทันที
        // ไม่ต้องรอ GPS fix) หรือสัญญาณ GPS อ่อน/ไม่มีเข้ามาเลยขณะที่นัดหมายที่เหลือถูกเช็คอินหมดแล้ว
        serviceScope.launch {
            while (isActive) {
                if (getPendingAppointments().isEmpty()) {
                    stopSelf()
                    return@launch
                }
                delay(PERIODIC_CHECK_MS)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        locationManager?.removeUpdates(locationListener)
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf()
            return
        }
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        locationManager = lm
        val provider = when {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> null
        }
        if (provider == null) {
            stopSelf()
            return
        }
        lm.requestLocationUpdates(provider, UPDATE_INTERVAL_MS, UPDATE_DISTANCE_M, locationListener, Looper.getMainLooper())
    }

    private suspend fun getPendingAppointments(): List<SalesActivity> {
        val today = LocalDate.now().toString()
        return db.activityDao().getActivitiesByDateRange(today, today).first()
            .filter { it.status != "checked_in" && it.plannedLat != null && it.plannedLong != null }
    }

    private suspend fun checkProximity(location: Location) {
        val pending = getPendingAppointments()
        if (pending.isEmpty()) {
            stopSelf()
            return
        }

        pending.forEach { activity ->
            val distance = haversineMeters(
                location.latitude, location.longitude,
                activity.plannedLat!!, activity.plannedLong!!
            )
            if (distance <= RADIUS_METERS && notifiedActivityIds.add(activity.activityId)) {
                showProximityAlert(activity.activityId, activity.companyName ?: "สถานที่นัดหมาย")
            }
        }
    }

    private fun buildMonitoringNotification(): Notification {
        NotificationChannels.ensureCreated(this)
        return NotificationCompat.Builder(this, NotificationChannels.LOCATION_ALERT_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("กำลังติดตามตำแหน่งนัดหมายวันนี้")
            .setContentText("แอปจะแจ้งเตือนเมื่อคุณเดินทางเข้าใกล้จุดนัดหมาย")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun showProximityAlert(activityId: String, companyName: String) {
        val mainIntent = Intent(
            Intent.ACTION_VIEW,
            android.net.Uri.parse("salestracking://activity/$activityId")
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, activityId.hashCode(), mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, NotificationChannels.LOCATION_ALERT_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("📍 คุณอยู่ใกล้สถานที่นัดหมาย!")
            .setContentText("สถานที่: $companyName")
            .setStyle(NotificationCompat.BigTextStyle().bigText("สถานที่: $companyName"))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .build()
        (getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager)
            .notify(activityId.hashCode(), notification)
    }

    companion object {
        private const val NOTIFICATION_ID = 9001
        private const val RADIUS_METERS = 500.0
        private const val UPDATE_INTERVAL_MS = 30_000L
        private const val UPDATE_DISTANCE_M = 50f
        private const val PERIODIC_CHECK_MS = 5 * 60_000L

        fun startIfNeeded(context: Context) {
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
            ) return
            ContextCompat.startForegroundService(context, Intent(context, ProximityMonitorService::class.java))
        }
    }
}
