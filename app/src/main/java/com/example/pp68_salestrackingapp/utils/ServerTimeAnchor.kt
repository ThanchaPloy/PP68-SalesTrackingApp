package com.example.pp68_salestrackingapp.utils

import android.content.Context
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * เวลาที่อ้างอิงจากเซิร์ฟเวอร์ ไม่ใช่นาฬิกาเครื่อง (แผนงาน B.4 ข้อ 1)
 *
 * ปัญหาที่แก้: กติกานัดหมายตัดสินจาก "ตอนนี้กี่โมง" แต่ผู้ใช้เปลี่ยนนาฬิกาเครื่องเองได้
 * การแก้แผนตอนออฟไลน์แล้วค่อย sync ทีหลังจึงพิสูจน์ไม่ได้ว่าแก้ตอนไหนจริง ๆ
 *
 * วิธี: จำเวลาที่เซิร์ฟเวอร์บอกมาครั้งล่าสุด คู่กับ [SystemClock.elapsedRealtime] ซึ่งเดินหน้า
 * อย่างเดียวและผู้ใช้แก้ไม่ได้ แล้วคำนวณเวลาปัจจุบันจากส่วนต่างของ elapsedRealtime
 *
 * ไม่ได้ป้องกันการปลอมแบบตั้งใจได้ 100% (แอปที่ถูกแก้ส่งค่าอะไรขึ้นมาก็ได้) แต่กันเคสจริงที่
 * พบบ่อยคือเวลาเครื่องเพี้ยนหรือถูกปรับ ซึ่งทำให้งานที่แก้ไว้ถูกปฏิเสธทั้งที่ผู้ใช้ไม่ได้ทำอะไรผิด
 */
data class TimeAnchor(
    val serverEpochMillis: Long,
    val elapsedRealtimeMillis: Long,
    /** เวลาที่เครื่องบูต ประมาณจาก wall clock ลบ uptime — เปลี่ยนเมื่อรีบูตหรือเวลาเครื่องถูกแก้ */
    val bootEpochEstimate: Long
)

object ServerTimeMath {

    /**
     * ยอมให้คลาดได้เล็กน้อยเพราะ wall clock กับ elapsedRealtime อ่านคนละจังหวะกัน
     * และ NTP ขยับเวลาทีละนิดตลอดเวลา
     */
    const val BOOT_DRIFT_TOLERANCE_MS = 5_000L

    fun capture(serverEpochMillis: Long, elapsedRealtimeMillis: Long, wallClockMillis: Long) =
        TimeAnchor(
            serverEpochMillis = serverEpochMillis,
            elapsedRealtimeMillis = elapsedRealtimeMillis,
            bootEpochEstimate = wallClockMillis - elapsedRealtimeMillis
        )

    /**
     * คืนเวลาปัจจุบันตามเซิร์ฟเวอร์ หรือ null เมื่อเชื่อ anchor เดิมไม่ได้แล้ว
     *
     * เชื่อไม่ได้เมื่อ:
     * - ยังไม่เคยคุยกับเซิร์ฟเวอร์เลย
     * - เครื่องรีบูต (uptime ย้อนกลับ และเวลาบูตที่ประมาณได้ขยับ)
     * - เวลาเครื่องถูกแก้ ซึ่งทางเทคนิคไม่กระทบการคำนวณ แต่เป็นสัญญาณว่ามีการยุ่งกับเวลา
     *   จึงเลือกไม่เชื่อไว้ก่อน แล้วให้แอปเตือนผู้ใช้แทนการบอกว่าสำเร็จแน่นอน
     */
    fun resolve(anchor: TimeAnchor?, elapsedRealtimeNow: Long, wallClockNow: Long): Long? {
        if (anchor == null) return null
        if (elapsedRealtimeNow < anchor.elapsedRealtimeMillis) return null
        val bootNow = wallClockNow - elapsedRealtimeNow
        if (kotlin.math.abs(bootNow - anchor.bootEpochEstimate) > BOOT_DRIFT_TOLERANCE_MS) return null
        return anchor.serverEpochMillis + (elapsedRealtimeNow - anchor.elapsedRealtimeMillis)
    }
}

@Singleton
class ServerTimeAnchor @Inject constructor(@ApplicationContext context: Context) {

    private val prefs = context.getSharedPreferences("server_time_anchor", Context.MODE_PRIVATE)

    /** เรียกจาก interceptor ทุกครั้งที่ได้ response — header Date มีอยู่ในทุกคำตอบอยู่แล้ว */
    fun record(serverEpochMillis: Long) {
        val anchor = ServerTimeMath.capture(
            serverEpochMillis = serverEpochMillis,
            elapsedRealtimeMillis = SystemClock.elapsedRealtime(),
            wallClockMillis = System.currentTimeMillis()
        )
        prefs.edit()
            .putLong(KEY_SERVER, anchor.serverEpochMillis)
            .putLong(KEY_ELAPSED, anchor.elapsedRealtimeMillis)
            .putLong(KEY_BOOT, anchor.bootEpochEstimate)
            .apply()
    }

    private fun stored(): TimeAnchor? {
        if (!prefs.contains(KEY_SERVER)) return null
        return TimeAnchor(
            serverEpochMillis = prefs.getLong(KEY_SERVER, 0L),
            elapsedRealtimeMillis = prefs.getLong(KEY_ELAPSED, 0L),
            bootEpochEstimate = prefs.getLong(KEY_BOOT, 0L)
        )
    }

    /** null = ไม่มีเวลาที่เชื่อถือได้ ผู้เรียกต้องเตือนผู้ใช้ ไม่ใช่เงียบแล้วใช้เวลาเครื่องแทน */
    fun nowOrNull(): java.time.Instant? =
        ServerTimeMath.resolve(stored(), SystemClock.elapsedRealtime(), System.currentTimeMillis())
            ?.let { java.time.Instant.ofEpochMilli(it) }

    private companion object {
        const val KEY_SERVER = "server_epoch_ms"
        const val KEY_ELAPSED = "elapsed_realtime_ms"
        const val KEY_BOOT = "boot_epoch_estimate"
    }
}
