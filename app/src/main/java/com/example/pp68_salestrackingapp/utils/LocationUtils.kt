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

private fun bestLastKnown(locationManager: LocationManager): android.location.Location? =
    locationManager.allProviders.mapNotNull { locationManager.getLastKnownLocation(it) }.maxByOrNull { it.time }

@SuppressLint("MissingPermission")
// อ่านพิกัดปัจจุบันของอุปกรณ์และส่งผลผ่าน callback
//
// ขอ GPS กับ network provider "พร้อมกัน" ใครตอบก่อนใช้อันนั้น แทนที่จะเลือก GPS เป็นหลักแล้วรอ
// จนหมดเวลาเหมือนเดิม — งานส่วนใหญ่ของเซลส์คือเช็คอินในอาคาร/ออฟฟิศลูกค้า (B2B) ที่ GPS ต้องมองเห็น
// ท้องฟ้าถึงจะจับดาวเทียมได้ ในตึกจึงช้ามากหรือจับไม่ได้เลย (เวลาจับติดครั้งแรกไม่คงที่ ขึ้นกับมุม
// สัญญาณที่ลอดผ่านกระจก/ผนังตอนนั้นพอดีหรือไม่ ลองใหม่สิบรอบไม่ติดแล้วรอบที่สิบเอ็ดติดเฉยๆ ก็ปกติ)
// ขณะที่ network provider (เสาสัญญาณมือถือ/WiFi) ตอบได้เร็วและสม่ำเสมอกว่ามากในสถานการณ์นี้
// แม่นยำน้อยกว่า GPS แต่พอสำหรับยืนยัน "อยู่แถวนี้จริง" ซึ่งคือสิ่งที่เช็คอินต้องการ
fun fetchCurrentLocation(context: Context, onError: (() -> Unit)? = null, onResult: (Double, Double) -> Unit) {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    if (locationManager == null) { onError?.invoke(); return }
    val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        .filter { locationManager.isProviderEnabled(it) }

    if (providers.isEmpty()) {
        val lastKnown = bestLastKnown(locationManager)
        if (lastKnown != null) onResult(lastKnown.latitude, lastKnown.longitude) else onError?.invoke()
        return
    }

    var settled = false
    // จบเมื่อ "ทุก" provider ที่ขอไปตอบกลับมาแล้วไม่มีพิกัดเลยสักตัว ไม่ใช่แค่ตัวแรกที่ตอบ
    var pendingFailures = providers.size
    fun settleSuccess(lat: Double, lng: Double, cleanup: () -> Unit) {
        if (settled) return
        settled = true
        cleanup()
        onResult(lat, lng)
    }
    fun settleFailureIfAllDone(cleanup: () -> Unit) {
        pendingFailures -= 1
        if (pendingFailures <= 0 && !settled) {
            settled = true
            cleanup()
            onError?.invoke()
        }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val signals = providers.associateWith { CancellationSignal() }
        fun cancelAll() = signals.values.forEach { it.cancel() }
        providers.forEach { provider ->
            locationManager.getCurrentLocation(provider, signals.getValue(provider), context.mainExecutor) { location ->
                if (location != null) settleSuccess(location.latitude, location.longitude, ::cancelAll)
                // ตั้งใจไม่ fallback ไปใช้ getLastKnownLocation ตรงนี้ (ต่างจากกรณี provider ปิดหมด
                // ด้านบน) เพราะพิกัดเก่าอาจเป็นเมื่อหลายนาที/ชั่วโมงก่อนตอนอยู่คนละที่ ใช้ยืนยัน
                // เช็คอิน "อยู่หน้างานจริงตอนนี้" ไม่ได้ — รอ provider อีกตัวต่อดีกว่าเสี่ยงเช็คอินเพี้ยน
                else settleFailureIfAllDone(::cancelAll)
            }
        }
        // ไม่พึ่ง timeout ของ OS อย่างเดียว — บางเครื่อง/ROM ไม่เรียก callback กลับเลยถ้า GPS ค้าง
        // ตั้งนาฬิกาของแอปเองไว้กันสปินเนอร์ค้างถาวร แล้วยกเลิกทุก request ที่ขอไว้ทิ้งไป
        android.os.Handler(Looper.getMainLooper()).postDelayed({
            if (!settled) {
                settled = true
                cancelAll()
                onError?.invoke()
            }
        }, SINGLE_UPDATE_TIMEOUT_MS)
    } else {
        // เส้นนี้ยังใช้จริงเพราะ minSdk = 26 ส่วน API 30+ ไปใช้ getCurrentLocation ด้านบนที่มี
        // timeout ให้อยู่แล้ว — ใช้พิกัดเก่าทันทีถ้ามี (ของเดิมก็ทำแบบนี้) ไม่งั้นค่อยขอพิกัดสดจากทั้ง
        // สอง provider พร้อมกัน เพราะ requestSingleUpdate ไม่รับประกันว่าจะ callback กลับเลยถ้าใน
        // อาคาร/GPS จับดาวเทียมไม่ได้ ผู้เรียกจะค้างหมุนรอตลอดโดยไม่มี onError ให้เลิกรอ
        val lastKnown = bestLastKnown(locationManager)
        if (lastKnown != null) {
            onResult(lastKnown.latitude, lastKnown.longitude)
        } else {
            val listeners = mutableListOf<android.location.LocationListener>()
            fun removeAll() = listeners.forEach { runCatching { locationManager.removeUpdates(it) } }
            providers.forEach { provider ->
                val listener = android.location.LocationListener { location ->
                    settleSuccess(location.latitude, location.longitude, ::removeAll)
                }
                listeners += listener
                locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            }
            android.os.Handler(Looper.getMainLooper()).postDelayed({
                if (!settled) {
                    settled = true
                    removeAll()
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
