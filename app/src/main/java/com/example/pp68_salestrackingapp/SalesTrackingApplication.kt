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
        }

        // เผื่อมีนัดหมายวันนี้ค้างอยู่ตอนเปิดแอป — service เช็คเองแล้วหยุดถ้าไม่มีอะไรต้องติดตาม
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            ProximityMonitorService.startIfNeeded(this)
        }
    }
}
