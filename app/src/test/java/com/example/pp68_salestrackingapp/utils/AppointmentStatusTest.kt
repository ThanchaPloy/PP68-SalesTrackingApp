package com.example.pp68_salestrackingapp.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AppointmentStatusTest {

    @Test
    fun `planned appointment with a future date is not missing`() {
        val future = LocalDate.now().plusDays(1).toString()
        assertEquals("planned", AppointmentStatus.effective("planned", future, "onsite"))
    }

    @Test
    fun `planned appointment with today's date is not missing`() {
        val today = LocalDate.now().toString()
        assertEquals("planned", AppointmentStatus.effective("planned", today, "onsite"))
    }

    @Test
    fun `planned appointment with a past date becomes missing`() {
        val yesterday = LocalDate.now().minusDays(1).toString()
        assertEquals(AppointmentStatus.MISSING, AppointmentStatus.effective("planned", yesterday, "onsite"))
    }

    @Test
    fun `checked_in or completed with a past date is never missing`() {
        val yesterday = LocalDate.now().minusDays(1).toString()
        assertEquals("checked_in", AppointmentStatus.effective("checked_in", yesterday, "onsite"))
        assertEquals("completed", AppointmentStatus.effective("completed", yesterday, "onsite"))
    }

    @Test
    fun `blank or unparseable date falls back to the raw status`() {
        assertEquals("planned", AppointmentStatus.effective("planned", null, "onsite"))
        assertEquals("planned", AppointmentStatus.effective("planned", "not-a-date", "onsite"))
    }

    // ขาดนัด = ไม่ไปปรากฏตัวตามนัด วัดจากการเช็คอิน — online/call ไม่มีขั้นตอนเช็คอินเลย
    // จึงบันทึกผลย้อนหลังเมื่อไหร่ก็ได้ ไม่มีวันขึ้นว่าขาดนัด
    @Test
    fun `online and call appointments are never missing even long past their date`() {
        val lastMonth = LocalDate.now().minusDays(30).toString()
        assertEquals("planned", AppointmentStatus.effective("planned", lastMonth, "online"))
        assertEquals("planned", AppointmentStatus.effective("planned", lastMonth, "call"))
    }

    @Test
    fun `activity type matching ignores case`() {
        val yesterday = LocalDate.now().minusDays(1).toString()
        assertEquals(AppointmentStatus.MISSING, AppointmentStatus.effective("planned", yesterday, "ONSITE"))
    }

    // ชนิดที่ไม่รู้จัก/ว่าง ถือว่าไม่ต้องเช็คอิน จึงไม่ถูกตีเป็นขาดนัด — ปลอดภัยกว่าการกล่าวหาผิด
    @Test
    fun `unknown or missing activity type is not treated as missing`() {
        val yesterday = LocalDate.now().minusDays(1).toString()
        assertEquals("planned", AppointmentStatus.effective("planned", yesterday, null))
        assertEquals("planned", AppointmentStatus.effective("planned", yesterday, "something-else"))
    }

    @Test
    fun `edit lock applies only to planned appointments within 7 days`() {
        val in7Days = LocalDate.now().plusDays(7).toString()
        val in8Days = LocalDate.now().plusDays(8).toString()
        val today = LocalDate.now().toString()

        assertTrue(AppointmentStatus.isEditLocked("planned", today))
        assertTrue(AppointmentStatus.isEditLocked("planned", in7Days))
        assertFalse(AppointmentStatus.isEditLocked("planned", in8Days))
    }

    @Test
    fun `edit lock never applies to non-planned statuses`() {
        val today = LocalDate.now().toString()
        assertFalse(AppointmentStatus.isEditLocked("checked_in", today))
        assertFalse(AppointmentStatus.isEditLocked("completed", today))
    }

    @Test
    fun `edit lock does not apply once the date has already passed`() {
        val yesterday = LocalDate.now().minusDays(1).toString()
        assertFalse(AppointmentStatus.isEditLocked("planned", yesterday))
    }
}
