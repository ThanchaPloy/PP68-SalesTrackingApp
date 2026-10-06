package com.example.pp68_salestrackingapp

import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.example.pp68_salestrackingapp.di.TokenManager
import com.example.pp68_salestrackingapp.service.ProximityMonitorService
import com.example.pp68_salestrackingapp.utils.NotificationChannels
import com.example.pp68_salestrackingapp.utils.SyncManager
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import javax.inject.Inject


@HiltAndroidApp
class SalesTrackingApplication : Application(), Configuration.Provider, ImageLoaderFactory {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var okHttpClient: OkHttpClient

    @Inject
    lateinit var tokenManager: TokenManager

    @Inject
    lateinit var syncManager: SyncManager

    @Inject
    lateinit var diagnostics: com.example.pp68_salestrackingapp.utils.SyncDiagnostics

    /**
     * แอปล่มแล้วไม่เหลือร่องรอยในไฟล์ diagnostics เลย ผู้ใช้ส่งไฟล์มาให้ก็ไม่เห็นว่าเกิดอะไร
     *
     * เก็บแค่ชนิดของ exception กับชื่อเธรด ไม่เก็บข้อความหรือ stack trace เพราะสองอย่างนั้น
     * มักมีค่าจากข้อมูลจริงปนมา (ชื่อลูกค้า รหัสรายการ บางทีก็ token)
     *
     * ต้องเขียนลงดิสก์แบบรอให้เสร็จ เพราะ process กำลังจะตาย แล้วส่งต่อให้ handler เดิมเสมอ
     * ไม่งั้นจะกลืน crash จนระบบไม่รู้ว่าแอปล่ม
     */
    private fun recordCrashesInDiagnostics() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                diagnostics.record(
                    "app_crash",
                    mapOf(
                        "error_type" to error::class.java.name,
                        "thread" to thread.name
                    ),
                    flush = true
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    // รูปเข้าเยี่ยมอยู่หลังการยืนยันตัวตนแล้ว — Coil ต้องใช้ client ตัวเดียวกับที่ยิง API
    // ไม่งั้นมันสร้าง OkHttpClient ของตัวเองที่ไม่มี AuthInterceptor แล้วโหลดรูปไม่ได้
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient { okHttpClient }
            .build()

    override fun onCreate() {
        super.onCreate()
        recordCrashesInDiagnostics()
        NotificationChannels.ensureCreated(this)
        syncManager.scheduleEndOfDayReminder()

        // MapLibre เริ่มต้นเองตอนสร้าง MapView (ดู MapLibreMapView) ไม่ต้องตั้งค่าอะไรตรงนี้
        // และ OpenFreeMap ไม่ต้องใช้ API key หรือ User-Agent เฉพาะแบบ tile server ของ OSM

        // เผื่อมีนัดหมายวันนี้ค้างอยู่ตอนเปิดแอป — service เช็คเองแล้วหยุดถ้าไม่มีอะไรต้องติดตาม
        // ✅ ต้องเช็ค toggle "แจ้งเตือนนัดหมาย" เหมือน AppointmentAlarmReceiver ไม่งั้นผู้ใช้ปิดสวิตช์
        // ไปแล้วแต่ proximity alert (แจ้งเตือนตอนเข้าใกล้ 500m) ยังยิงอยู่เหมือนเดิม
        // ✅ ต้องเช็คว่ายัง login อยู่ด้วย — onCreate ตัวนี้รันทุกครั้งที่ process เกิด รวมตอนที่
        // WorkManager ปลุกขึ้นมาทำ SyncWorker เบื้องหลัง ซึ่งแอปไม่ได้อยู่หน้าจอ การสตาร์ต
        // foreground service จากเบื้องหลังบน Android 12+ ถูกปฏิเสธด้วย
        // ForegroundServiceStartNotAllowedException = แอปแครชทั้งตัว ไม่ใช่แค่ service ไม่ขึ้น
        if (tokenManager.getToken() != null &&
            tokenManager.isVisitReminderEnabled() &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        ) {
            ProximityMonitorService.startIfNeeded(this)
        }
    }
}
