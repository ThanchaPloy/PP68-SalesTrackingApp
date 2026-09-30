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
        NotificationChannels.ensureCreated(this)

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
