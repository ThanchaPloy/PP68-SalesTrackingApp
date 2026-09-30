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
import com.example.pp68_salestrackingapp.di.TokenManager
import com.example.pp68_salestrackingapp.utils.AppointmentStatus
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
    @Inject lateinit var tokenManager: TokenManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var locationManager: LocationManager? = null
    // ✅ เก็บเฉพาะนัดหมายที่ "ตอนนี้อยู่ในรัศมี" (ไม่ใช่ "เคยแจ้งแล้วตลอดไป") — ถ้าออกนอกรัศมีแล้ว
    // กลับเข้ามาใหม่ วันเดียวกัน จะแจ้งเตือนซ้ำได้ จนกว่านัดหมายนั้นจะเช็คอินหรือเสร็จสิ้น (หลุดออกจาก
    // getPendingAppointments() แล้ว retainAll จะเอาออกจาก set นี้ไปด้วย)
    // location callbacks can fire from more than one thread — keep this thread-safe
    private val activitiesInsideRadius = Collections.synchronizedSet(mutableSetOf<String>())

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
        // ✅ ผู้ใช้อาจปิดสวิตช์ "แจ้งเตือนนัดหมาย" ระหว่างที่ service ตัวนี้ยังทำงานค้างอยู่ตั้งแต่ก่อน
        // เปิด Settings ไปปิด — ต้องหยุดทันทีไม่ใช่แค่กันตอนสตาร์ท (ดู SalesTrackingApplication)
        if (!tokenManager.isVisitReminderEnabled()) {
            stopSelf()
            return
        }
        startForeground(NOTIFICATION_ID, buildMonitoringNotification())
        startLocationUpdates()

        // ตรวจสอบเป็นระยะแยกจาก location callback — เผื่อไม่มีนัดหมายเลยตั้งแต่แรก (ควรหยุดทันที
        // ไม่ต้องรอ GPS fix) หรือสัญญาณ GPS อ่อน/ไม่มีเข้ามาเลยขณะที่นัดหมายที่เหลือถูกเช็คอินหมดแล้ว
        serviceScope.launch {
            while (isActive) {
                if (!tokenManager.isVisitReminderEnabled() || getPendingAppointments().isEmpty()) {
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
            .filter { it.status != "checked_in" && it.status != "completed" && it.plannedLat != null && it.plannedLong != null }
            // ✅ การโทร/ประชุมออนไลน์ไม่มีการไปปรากฏตัวที่หน้างาน เตือน "คุณอยู่ใกล้สถานที่นัดหมาย"
            // แล้วกดเข้าไปก็ไม่มีปุ่มเช็คอินให้ (ปิดไปแล้วใน 6d70e38) ปกติสองชนิดนี้ไม่มีพิกัดจึงถูก
            // กรองทิ้งด้วย plannedLat != null อยู่แล้ว แต่นัดที่สร้างเป็น onsite แล้วแก้ชนิดทีหลัง
            // พิกัดเดิมยังค้างอยู่ ไม่มีใครล้างให้ — กรองด้วยกติกาเดียวกับเรื่องขาดนัดตรงนี้เลย
            .filter { AppointmentStatus.requiresCheckIn(it.activityType) }
    }

    private suspend fun checkProximity(location: Location) {
        if (!tokenManager.isVisitReminderEnabled()) {
            stopSelf()
            return
        }
        val pending = getPendingAppointments()
        if (pending.isEmpty()) {
            stopSelf()
            return
        }

        // เลิกติดตามนัดหมายที่เช็คอิน/เสร็จสิ้นไปแล้ว ไม่งั้น set นี้จะค้างและกิน memory ไปเรื่อยๆ
        activitiesInsideRadius.retainAll(pending.map { it.activityId }.toSet())

        pending.forEach { activity ->
            val distance = haversineMeters(
                location.latitude, location.longitude,
                activity.plannedLat!!, activity.plannedLong!!
            )
            if (distance <= RADIUS_METERS) {
                // แจ้งเตือนเฉพาะตอนเพิ่งเข้ารัศมี (rising edge) — add() คืน false ถ้าอยู่ในรัศมีอยู่แล้ว
                if (activitiesInsideRadius.add(activity.activityId)) {
                    showProximityAlert(activity.activityId, activity.companyName ?: "สถานที่นัดหมาย")
                }
            } else {
                // ออกนอกรัศมีแล้ว — ถ้ากลับเข้ามาใหม่จะแจ้งเตือนซ้ำได้
                activitiesInsideRadius.remove(activity.activityId)
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
        // ✅ ใส่ prefix กันชนกับ notification/PendingIntent ของ AppointmentAlarmReceiver ที่ใช้
        // activityId.hashCode() เปล่าๆ เหมือนกัน — ไม่งั้นถ้านัดหมายเดียวกันโดนแจ้งทั้งสองแบบ
        // ใกล้ๆ กัน อันที่มาทีหลังจะทับอันแรกในแถบแจ้งเตือนทันทีโดยไม่มีการเตือนเลย
        val notifId = "proximity:$activityId".hashCode()
        val pendingIntent = PendingIntent.getActivity(
            this, notifId, mainIntent,
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
            .notify(notifId, notification)
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
            // OS ปฏิเสธการสตาร์ต foreground service จากเบื้องหลังได้ตลอด (Android 12+) ทั้งกรณี
            // ที่คาดไว้และกรณีที่คาดไม่ถึง — ปล่อยให้ exception หลุดออกไปเท่ากับแครชแอปเพื่อ
            // แลกกับการเตือนพิกัดซึ่งเป็นของเสริม ยอมไม่ได้ ถือว่าไม่ได้เริ่มก็จบ
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, ProximityMonitorService::class.java))
            }.onFailure { android.util.Log.w("ProximityMonitor", "สตาร์ต service ไม่ได้: ${it.message}") }
        }
    }
}
