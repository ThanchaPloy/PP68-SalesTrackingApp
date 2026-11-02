package com.example.pp68_salestrackingapp.utils

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper

/**
 * แทนที่ FusedLocationProviderClient (Play Services) ด้วย android.location.LocationManager
 * ของ Android เอง
 */
// onError: เรียกทุกครั้งที่หาพิกัดไม่ได้เลย (ปิด location service ทั้งเครื่อง/ไม่เคยมีพิกัดแคชไว้)
// ไม่งั้นผู้เรียกไม่มีทางรู้เลยว่าควรเลิกรอ onResult แล้ว — เดิม optional เพื่อไม่กระทบจุดเรียกอื่น
// ที่ยังไม่ได้ผูก error handling ไว้
// ค่านี้เคยเป็น 20s และผูกไว้เฉพาะเส้น API < 30 — เส้น API >= 30 (ที่มือถือส่วนใหญ่ใช้งานจริง
// เพราะ minSdk 26 แต่ตลาดส่วนใหญ่อยู่ API 30+) ไม่มี timeout ของแอปเองเลย พึ่ง OS ล้วน ๆ ว่าจะ
// เรียก callback กลับใน "เวลาที่สมเหตุสมผล" ซึ่งบางเครื่อง/บาง ROM ไม่ทำตามจริง ทำให้สปินเนอร์
// ค้างถาวรไม่มี error ให้เลิกรอ ลดเหลือ 10s และผูก timeout เดียวกันนี้เข้าทั้งสองเส้นแล้ว
private const val SINGLE_UPDATE_TIMEOUT_MS = 10_000L

@SuppressLint("MissingPermission")
// อ่านพิกัดปัจจุบันของอุปกรณ์และส่งผลผ่าน callback
fun fetchCurrentLocation(context: Context, onError: (() -> Unit)? = null, onResult: (Double, Double) -> Unit) {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    if (locationManager == null) { onError?.invoke(); return }
    val provider = when {
        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
        else -> null
    }

    if (provider == null) {
        val lastKnown = locationManager.allProviders
            .mapNotNull { locationManager.getLastKnownLocation(it) }
            .maxByOrNull { it.time }
        if (lastKnown != null) onResult(lastKnown.latitude, lastKnown.longitude) else onError?.invoke()
        return
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        var settled = false
        val cancellationSignal = CancellationSignal()
        fun settle(action: () -> Unit) {
            if (settled) return
            settled = true
            action()
        }
        locationManager.getCurrentLocation(provider, cancellationSignal, context.mainExecutor) { location ->
            settle {
                if (location != null) {
                    onResult(location.latitude, location.longitude)
                } else {
                    val lastKnown = locationManager.getLastKnownLocation(provider)
                    if (lastKnown != null) onResult(lastKnown.latitude, lastKnown.longitude) else onError?.invoke()
                }
            }
        }
        // ไม่พึ่ง timeout ของ OS อย่างเดียว — บางเครื่อง/ROM ไม่เรียก callback กลับเลยถ้า GPS
        // ค้าง ตั้งนาฬิกาของแอปเองไว้กันสปินเนอร์ค้างถาวร แล้วยกเลิก request เดิมทิ้งไป
        // ตั้งใจไม่ fallback ไปใช้ getLastKnownLocation ตรงนี้ (ต่างจากกรณี location == null
        // ด้านบนที่ OS ตอบมาแล้วว่าไม่มี) เพราะพิกัดเก่าอาจเป็นเมื่อหลายนาที/ชั่วโมงก่อนตอนอยู่
        // คนละที่ — ใช้ยืนยันเช็คอิน "อยู่หน้างานจริงตอนนี้" ไม่ได้ ให้ onError แล้วให้ผู้ใช้กด
        // ลองใหม่ดีกว่าเสี่ยงเช็คอินเพี้ยน
        android.os.Handler(Looper.getMainLooper()).postDelayed({
            settle {
                cancellationSignal.cancel()
                onError?.invoke()
            }
        }, SINGLE_UPDATE_TIMEOUT_MS)
    } else {
        val lastKnown = locationManager.getLastKnownLocation(provider)
        if (lastKnown != null) {
            onResult(lastKnown.latitude, lastKnown.longitude)
        } else {
            // requestSingleUpdate ไม่รับประกันว่าจะ callback — ในอาคาร/GPS จับดาวเทียมไม่ได้
            // มันเงียบไปเลย ผู้เรียกจะค้างหมุนรอตลอดโดยไม่มี onError ให้เลิกรอ (เส้นนี้ยังใช้จริง
            // เพราะ minSdk = 26 ส่วน API 30+ ไปใช้ getCurrentLocation ที่มี timeout ให้อยู่แล้ว)
            var settled = false
            val listener = android.location.LocationListener { location ->
                if (settled) return@LocationListener
                settled = true
                onResult(location.latitude, location.longitude)
            }
            locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            android.os.Handler(Looper.getMainLooper()).postDelayed({
                if (!settled) {
                    settled = true
                    runCatching { locationManager.removeUpdates(listener) }
                    onError?.invoke()
                }
            }, SINGLE_UPDATE_TIMEOUT_MS)
        }
    }
}

// คำนวณระยะทางระหว่างพิกัดสองจุดด้วยสูตร Haversine
fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371e3
    val phi1 = Math.toRadians(lat1)
    val phi2 = Math.toRadians(lat2)
    val deltaPhi = Math.toRadians(lat2 - lat1)
    val deltaLambda = Math.toRadians(lon2 - lon1)

    val a = Math.sin(deltaPhi / 2).let { it * it } +
            Math.cos(phi1) * Math.cos(phi2) *
            Math.sin(deltaLambda / 2).let { it * it }
    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

    return r * c
}
