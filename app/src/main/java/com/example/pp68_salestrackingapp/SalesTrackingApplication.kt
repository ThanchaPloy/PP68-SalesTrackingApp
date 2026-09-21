package com.example.pp68_salestrackingapp

import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.pp68_salestrackingapp.service.ProximityMonitorService
import com.example.pp68_salestrackingapp.utils.NotificationChannels
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

private const val TILE_CACHE_DURATION_MS = 60L * 24 * 60 * 60 * 1000 // 60 วัน

@HiltAndroidApp
class SalesTrackingApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensureCreated(this)

        org.osmdroid.config.Configuration.getInstance().apply {
            // load() reads userAgentValue back from SharedPreferences (defaulting to the
            // package name) so it must run BEFORE we override it, otherwise our custom
            // User-Agent gets clobbered and OSM's tile servers 403 the generic package name.
            load(this@SalesTrackingApplication, android.preference.PreferenceManager.getDefaultSharedPreferences(this@SalesTrackingApplication))
            userAgentValue = BuildConfig.OSM_USER_AGENT

            // ✅ osmdroid's default tile-cache path resolution can land on SHARED external
            // storage (StorageUtils.getBestWritableStorage), which `uninstall` does NOT clear —
            // any tiles cached while the User-Agent bug was still live (403 error responses
            // written as if they were tiles) would keep being served from disk forever, even
            // after the real fix. Pin it to app-private storage so it's always clean on uninstall.
            osmdroidBasePath = java.io.File(getExternalFilesDir(null), "osmdroid")
            osmdroidTileCache = java.io.File(osmdroidBasePath, "tiles")

            // ✅ ปกติ osmdroid เคารพวันหมดอายุที่ tile server ส่งมา (~7 วัน) แล้วโหลดรูปเดิมซ้ำใหม่
            // ทั้งที่ถนน/อาคารไม่ได้เปลี่ยน — เซลส์วนดูเขตเดิมทุกวัน เลยยิงซ้ำเดือนละหลายรอบโดยไม่จำเป็น
            // แอปนี้ใช้แผนที่เป็นพื้นหลังสำหรับปักหมุด/ดูระยะเช็คอิน ไม่ได้ใช้นำทาง ข้อมูลเก่าสองเดือน
            // จึงไม่กระทบ แต่ช่วยลดโหลดบนเซิร์ฟเวอร์อาสาสมัครของ OSM ได้มาก
            expirationOverrideDuration = TILE_CACHE_DURATION_MS
        }

        // เผื่อมีนัดหมายวันนี้ค้างอยู่ตอนเปิดแอป — service เช็คเองแล้วหยุดถ้าไม่มีอะไรต้องติดตาม
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            ProximityMonitorService.startIfNeeded(this)
        }
    }
}
