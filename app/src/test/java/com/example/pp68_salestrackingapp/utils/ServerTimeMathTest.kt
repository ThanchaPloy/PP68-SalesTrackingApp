package com.example.pp68_salestrackingapp.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * เคสที่แผนงาน B.4 สั่งให้ทดสอบ: นาฬิกาเครื่องถูกย้อน เครื่องรีบูต และเครื่องไม่ได้อยู่โซนไทย
 *
 * หน่วยทั้งหมดเป็น epoch millis ซึ่งไม่ขึ้นกับโซนเวลา การเปลี่ยนโซนเวลาเครื่องจึงไม่กระทบ
 * ที่นี่เลยโดยนิยาม — เคสโซนเวลาถูกทดสอบที่ AppointmentPolicyTest ซึ่งเป็นที่ที่มันมีความหมาย
 */
class ServerTimeMathTest {

    private val serverNow = 1_760_000_000_000L   // เวลาที่เซิร์ฟเวอร์บอก
    private val uptimeAtCapture = 50_000L
    private val wallAtCapture = 1_759_999_999_000L // นาฬิกาเครื่องเพี้ยนช้าไป 1 วินาที

    private val anchor = ServerTimeMath.capture(serverNow, uptimeAtCapture, wallAtCapture)

    @Test
    fun `time advances by uptime not by the device clock`() {
        // ผ่านไป 10 วินาทีตาม uptime แต่ผู้ใช้หมุนนาฬิกาเครื่องไปข้างหน้าหนึ่งชั่วโมง
        val resolved = ServerTimeMath.resolve(
            anchor,
            elapsedRealtimeNow = uptimeAtCapture + 10_000L,
            wallClockNow = wallAtCapture + 10_000L
        )
        assertEquals(serverNow + 10_000L, resolved)
    }

    @Test
    fun `no anchor means no trusted time`() {
        assertNull(ServerTimeMath.resolve(null, 1_000L, 1_000L))
    }

    @Test
    fun `a reboot invalidates the anchor instead of returning a wrong time`() {
        // หลังรีบูต uptime เริ่มนับใหม่จากศูนย์ และเวลาบูตที่ประมาณได้ขยับไปข้างหน้า
        assertNull(
            ServerTimeMath.resolve(
                anchor,
                elapsedRealtimeNow = 3_000L,
                wallClockNow = wallAtCapture + 600_000L
            )
        )
    }

    /** รีบูตแล้วเปิดเครื่องทิ้งไว้นานกว่ารอบก่อน — uptime ไม่ย้อน ต้องจับด้วยเวลาบูตที่ประมาณได้ */
    @Test
    fun `a reboot with longer uptime than before is still caught`() {
        assertNull(
            ServerTimeMath.resolve(
                anchor,
                elapsedRealtimeNow = uptimeAtCapture + 900_000L,
                wallClockNow = wallAtCapture + 1_800_000L
            )
        )
    }

    @Test
    fun `rolling the device clock backwards invalidates the anchor`() {
        assertNull(
            ServerTimeMath.resolve(
                anchor,
                elapsedRealtimeNow = uptimeAtCapture + 10_000L,
                wallClockNow = wallAtCapture - 3_600_000L
            )
        )
    }

    @Test
    fun `small NTP drift does not throw the anchor away`() {
        val drift = ServerTimeMath.BOOT_DRIFT_TOLERANCE_MS - 1
        val resolved = ServerTimeMath.resolve(
            anchor,
            elapsedRealtimeNow = uptimeAtCapture + 10_000L,
            wallClockNow = wallAtCapture + 10_000L + drift
        )
        assertEquals(serverNow + 10_000L, resolved)
    }

    @Test
    fun `drift beyond the tolerance is treated as tampering`() {
        val drift = ServerTimeMath.BOOT_DRIFT_TOLERANCE_MS + 1
        assertNull(
            ServerTimeMath.resolve(
                anchor,
                elapsedRealtimeNow = uptimeAtCapture + 10_000L,
                wallClockNow = wallAtCapture + 10_000L + drift
            )
        )
    }
}
