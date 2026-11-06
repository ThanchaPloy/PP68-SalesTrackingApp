package com.example.pp68_salestrackingapp.ui.viewmodels.export

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

class CheckInLateTest {

    private lateinit var original: TimeZone

    @Before
    fun setUp() {
        original = TimeZone.getDefault()
        // เซลส์อยู่ไทย (UTC+7) — checkInTime เก็บเป็น UTC จึงต้องแปลงก่อนเทียบ
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Bangkok"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(original)
    }

    @Test
    fun `arriving after the planned time counts as late`() {
        // 03:15Z = 10:15 ตามเวลาไทย นัดไว้ 09:00
        assertTrue(isCheckInLate("2026-04-06T03:15:00Z", "09:00:00"))
    }

    @Test
    fun `arriving before the planned time is not late`() {
        // 01:45Z = 08:45 ตามเวลาไทย นัดไว้ 09:00
        assertFalse(isCheckInLate("2026-04-06T01:45:00Z", "09:00:00"))
    }

    // เคสที่เคยพัง: เทียบสตริงตรง ๆ ทำให้นัดเช้าถูกตีว่าสายเสมอ
    @Test
    fun `an early morning appointment checked into on time is not flagged`() {
        assertFalse(isCheckInLate("2026-04-06T01:30:00Z", "09:00:00"))
        // ยืนยันว่าการเทียบแบบเดิมให้ผลผิด
        assertTrue("2026-04-06T01:30:00Z" > "09:00:00")
    }

    // เคสที่เคยพัง: นัดสองทุ่มขึ้นไปไม่เคยถูกตีว่าสายเลย
    @Test
    fun `a late evening appointment can still be late`() {
        // 14:30Z = 21:30 ตามเวลาไทย นัดไว้ 20:00
        assertTrue(isCheckInLate("2026-04-06T14:30:00Z", "20:00:00"))
        assertFalse("2026-04-06T14:30:00Z" > "20:00:00")
    }

    // ⚠️ เคสที่เคยพังเงียบที่สุด: แถวที่มาจาก sync ดาวน์โหลดไม่ได้ลงท้ายด้วย Z แต่พก offset มา
    // หรือไม่มี timezone เลย `Instant.parse` ของ Android รุ่นเก่าอ่านไม่ออกแล้ว catch กลืนคืน false
    // ป้าย "ช้ากว่าเวลานัด" จึงไม่เคยขึ้นสำหรับแถวส่วนใหญ่ในระบบ ทั้งที่เช็คอินสายจริง
    @Test
    fun `a check-in that arrived through sync is judged the same as one from a PATCH response`() {
        // 10:15 +07:00 = 03:15Z เวลาเดียวกับเคสแรกเป๊ะ ๆ ผลต้องเหมือนกัน
        assertTrue(isCheckInLate("2026-04-06T10:15:00+07:00", "09:00:00"))
        assertFalse(isCheckInLate("2026-04-06T08:45:00+07:00", "09:00:00"))
        // แบบไม่มี timezone ติดมาเลย (ผ่าน view ที่ cast ทิ้ง) — เป็นเวลาไทย
        assertTrue(isCheckInLate("2026-04-06T10:15:00", "09:00:00"))
        assertFalse(isCheckInLate("2026-04-06T08:45:00", "09:00:00"))
    }

    @Test
    fun `missing or unparseable values are never reported as late`() {
        assertFalse(isCheckInLate(null, "09:00:00"))
        assertFalse(isCheckInLate("2026-04-06T03:15:00Z", null))
        assertFalse(isCheckInLate("2026-04-06T03:15:00Z", "09:00 AM"))
        assertFalse(isCheckInLate("not-a-timestamp", "09:00:00"))
    }
}
