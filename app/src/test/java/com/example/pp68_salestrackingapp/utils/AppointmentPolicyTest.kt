package com.example.pp68_salestrackingapp.utils

import com.example.pp68_salestrackingapp.utils.AppointmentPolicy.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * ตาราง B.1 ของแผน แปลงเป็น test ตรงตัว — ทั้งสอง repo ต้องตอบเหมือนกันทุกช่อง
 * นัดตัวอย่างคือ 2026-10-10 14:00 เวลาไทย (= 07:00Z)
 */
class AppointmentPolicyTest {

    private val day = "2026-10-10"
    private val startTime = "14:00"

    /** ตั้งนาฬิกาด้วย UTC โดยตั้งใจ เพื่อพิสูจน์ว่า policy ไม่ได้พึ่งโซนเวลาของเครื่อง */
    private fun clockAt(utc: String): Clock = Clock.fixed(Instant.parse(utc), ZoneId.of("UTC"))

    private fun facts(
        type: String = "onsite",
        status: String = "planned",
        date: String? = day,
        time: String? = startTime,
        checkedIn: Boolean = false
    ) = AppointmentPolicy.Facts(status, type, date, time, checkedIn)

    private fun assertDenied(expectedCode: String, decision: Decision) {
        val denied = decision as? Decision.Denied
            ?: throw AssertionError("คาดว่าจะถูกปฏิเสธด้วย $expectedCode แต่อนุญาต")
        assertEquals(expectedCode, denied.code)
        assertTrue("ต้องมีข้อความอธิบายเหตุผลให้ผู้ใช้อ่าน", denied.message.isNotBlank())
    }

    private val beforeDay = clockAt("2026-10-09T03:00:00Z")      // 09 ต.ค. 10:00 ไทย
    private val dayBeforeStart = clockAt("2026-10-10T06:59:00Z") // 10 ต.ค. 13:59 ไทย
    private val exactlyAtStart = clockAt("2026-10-10T07:00:00Z") // 10 ต.ค. 14:00 ไทย
    private val afterStartSameDay = clockAt("2026-10-10T10:00:00Z") // 10 ต.ค. 17:00 ไทย
    private val afterDay = clockAt("2026-10-11T03:00:00Z")       // 11 ต.ค. 10:00 ไทย

    // ── แถวที่ 1: ก่อนวันนัด ────────────────────────────────────
    @Test
    fun `before the appointment day everything except an unchecked onsite result is open`() {
        assertTrue(AppointmentPolicy.canEdit(facts(), beforeDay) is Decision.Allowed)
        assertTrue(AppointmentPolicy.canDelete(facts(), beforeDay) is Decision.Allowed)
        assertDenied(
            AppointmentPolicy.ONSITE_CHECKIN_REQUIRED,
            AppointmentPolicy.canCreateResult(facts(), beforeDay)
        )
        assertTrue(AppointmentPolicy.canCreateResult(facts(type = "online"), beforeDay) is Decision.Allowed)
        assertTrue(AppointmentPolicy.canCreateResult(facts(type = "call"), beforeDay) is Decision.Allowed)
    }

    // ── แถวที่ 2: วันนัด ก่อนเวลาเริ่ม ───────────────────────────
    @Test
    fun `on the appointment day before the start time editing stays open but deleting does not`() {
        assertTrue(AppointmentPolicy.canEdit(facts(), dayBeforeStart) is Decision.Allowed)
        assertDenied(
            AppointmentPolicy.DELETE_WINDOW_CLOSED,
            AppointmentPolicy.canDelete(facts(), dayBeforeStart)
        )
        assertTrue(AppointmentPolicy.canCreateResult(facts(type = "online"), dayBeforeStart) is Decision.Allowed)
    }

    // ── แถวที่ 3: วันนัด ตั้งแต่เวลาเริ่ม ────────────────────────
    @Test
    fun `the start minute itself already closes the edit window`() {
        assertDenied(
            AppointmentPolicy.EDIT_WINDOW_CLOSED,
            AppointmentPolicy.canEdit(facts(), exactlyAtStart)
        )
        assertDenied(
            AppointmentPolicy.DELETE_WINDOW_CLOSED,
            AppointmentPolicy.canDelete(facts(), exactlyAtStart)
        )
        assertDenied(
            AppointmentPolicy.ONSITE_CHECKIN_REQUIRED,
            AppointmentPolicy.canCreateResult(facts(), exactlyAtStart)
        )
        assertTrue(AppointmentPolicy.canCreateResult(facts(checkedIn = true), exactlyAtStart) is Decision.Allowed)
        assertTrue(AppointmentPolicy.canCreateResult(facts(type = "online"), exactlyAtStart) is Decision.Allowed)
    }

    // ── แถวที่ 4: หลังวันนัด และเคย check-in ─────────────────────
    @Test
    fun `after the day a checked-in onsite appointment can still be written up`() {
        val checkedIn = facts(checkedIn = true)
        assertDenied(AppointmentPolicy.EDIT_WINDOW_CLOSED, AppointmentPolicy.canEdit(checkedIn, afterDay))
        assertDenied(AppointmentPolicy.DELETE_WINDOW_CLOSED, AppointmentPolicy.canDelete(checkedIn, afterDay))
        assertTrue(AppointmentPolicy.canCreateResult(checkedIn, afterDay) is Decision.Allowed)
        assertEquals("planned", AppointmentPolicy.effectiveStatus(checkedIn, afterDay))
    }

    // ── แถวที่ 5: หลังวันนัด ไม่มี check-in ──────────────────────
    @Test
    fun `after the day an onsite appointment with no check-in is missed and cannot be written up`() {
        assertEquals(AppointmentPolicy.MISSING, AppointmentPolicy.effectiveStatus(facts(), afterDay))
        assertDenied(
            AppointmentPolicy.MISSED_ONSITE_RESULT_NOT_ALLOWED,
            AppointmentPolicy.canCreateResult(facts(), afterDay)
        )
        assertTrue(AppointmentPolicy.canEdit(facts(), afterDay) is Decision.Denied)
        assertTrue(AppointmentPolicy.canDelete(facts(), afterDay) is Decision.Denied)
    }

    @Test
    fun `an online appointment is never missed and can be written up late`() {
        assertEquals("planned", AppointmentPolicy.effectiveStatus(facts(type = "online"), afterDay))
        assertTrue(AppointmentPolicy.canCreateResult(facts(type = "online"), afterDay) is Decision.Allowed)
        assertTrue(AppointmentPolicy.canCreateResult(facts(type = "call"), afterDay) is Decision.Allowed)
    }

    // ── แถวที่ 6: completed ─────────────────────────────────────
    @Test
    fun `a completed appointment is closed for edit delete and a second result`() {
        val done = facts(status = "completed")
        assertDenied(AppointmentPolicy.STATUS_LOCKED, AppointmentPolicy.canEdit(done, beforeDay))
        assertDenied(AppointmentPolicy.STATUS_LOCKED, AppointmentPolicy.canDelete(done, beforeDay))
        assertDenied(AppointmentPolicy.STATUS_LOCKED, AppointmentPolicy.canCreateResult(done, beforeDay))
    }

    @Test
    fun `a cancelled appointment is closed too`() {
        val cancelled = facts(status = "cancelled")
        assertDenied(AppointmentPolicy.STATUS_LOCKED, AppointmentPolicy.canEdit(cancelled, beforeDay))
        assertDenied(AppointmentPolicy.STATUS_LOCKED, AppointmentPolicy.canCreateResult(cancelled, beforeDay))
    }

    // ── เส้นแบ่งและข้อมูลไม่ครบ ─────────────────────────────────
    @Test
    fun `one minute before the start is still editable`() {
        assertTrue(AppointmentPolicy.canEdit(facts(), dayBeforeStart) is Decision.Allowed)
        assertTrue(AppointmentPolicy.canEdit(facts(), clockAt("2026-10-10T06:59:59Z")) is Decision.Allowed)
    }

    @Test
    fun `a row without a start time locks from midnight instead of guessing`() {
        val noTime = facts(time = null)
        // เที่ยงคืนห้านาทีของวันนัด — ถ้าเดาเวลาเป็น 09:00 หรือปล่อยผ่าน แถวนี้จะยังแก้ได้
        assertDenied(
            AppointmentPolicy.TIME_MISSING,
            AppointmentPolicy.canEdit(noTime, clockAt("2026-10-09T17:05:00Z"))
        )
        // ก่อนวันนัดยังแก้ได้ตามปกติ การไม่มีเวลาไม่ได้ล็อกทั้งชีวิตของแถว
        assertTrue(AppointmentPolicy.canEdit(noTime, beforeDay) is Decision.Allowed)
        assertTrue(AppointmentPolicy.hasTimeQualityIssue(noTime))
        assertFalse(AppointmentPolicy.hasTimeQualityIssue(facts()))
    }

    @Test
    fun `a row with an unreadable date is reported rather than silently allowed`() {
        val broken = facts(date = "ไม่ใช่วันที่")
        assertDenied(AppointmentPolicy.TIME_MISSING, AppointmentPolicy.canEdit(broken, beforeDay))
        assertDenied(AppointmentPolicy.TIME_MISSING, AppointmentPolicy.canDelete(broken, beforeDay))
        assertTrue(AppointmentPolicy.hasTimeQualityIssue(broken))
    }

    @Test
    fun `seconds in the stored time do not break parsing`() {
        assertTrue(AppointmentPolicy.canEdit(facts(time = "14:00:00"), dayBeforeStart) is Decision.Allowed)
        assertDenied(
            AppointmentPolicy.EDIT_WINDOW_CLOSED,
            AppointmentPolicy.canEdit(facts(time = "14:00:00"), exactlyAtStart)
        )
    }

    @Test
    fun `the answer does not depend on the device time zone`() {
        // เครื่องตั้งเป็นโตเกียว เวลาเดียวกันเป็นวันที่ 11 แล้วในโซนนั้น แต่ยังเป็นวันนัดในเวลาไทย
        val tokyoClock = Clock.fixed(Instant.parse("2026-10-10T16:00:00Z"), ZoneId.of("Asia/Tokyo"))
        assertEquals("planned", AppointmentPolicy.effectiveStatus(facts(), tokyoClock))
        assertDenied(AppointmentPolicy.EDIT_WINDOW_CLOSED, AppointmentPolicy.canEdit(facts(), tokyoClock))
    }

    /**
     * เลยเวลาเริ่มนัดแล้วแต่ยังอยู่ในวันนัด ต้องยังเช็คอินได้
     *
     * เส้นที่คุมการเช็คอินคือ effectiveStatus ไม่ใช่ canEdit — ทั้ง CheckInScreen และ
     * ActivityRepository.checkIn ยอมให้เช็คอินเมื่อ effectiveStatus ยังเป็น "planned"
     * ส่วน MISSING เกิดเมื่อ "พ้นวันนัด" แล้วเท่านั้น ไม่ใช่พ้นเวลาเริ่ม
     * ช่วงแก้ไขแผนปิดไปแล้วตอนนี้โดยตั้งใจ และไม่เกี่ยวกับการเช็คอิน
     */
    @Test
    fun `onsite check-in stays open after the start time as long as it is still the appointment day`() {
        assertEquals("planned", AppointmentPolicy.effectiveStatus(facts(), afterStartSameDay))
        assertEquals(
            AppointmentPolicy.MISSING,
            AppointmentPolicy.effectiveStatus(facts(), afterDay)
        )
        assertDenied(
            AppointmentPolicy.EDIT_WINDOW_CLOSED,
            AppointmentPolicy.canEdit(facts(), afterStartSameDay)
        )
    }
}
