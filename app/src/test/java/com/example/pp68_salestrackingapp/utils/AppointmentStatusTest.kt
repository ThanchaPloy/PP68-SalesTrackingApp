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

        assertTrue(AppointmentStatus.isEditLocked("planned", today, "onsite"))
        assertTrue(AppointmentStatus.isEditLocked("planned", in7Days, "onsite"))
        assertFalse(AppointmentStatus.isEditLocked("planned", in8Days, "onsite"))
    }

    @Test
    fun `edit lock never applies to non-planned statuses`() {
        val today = LocalDate.now().toString()
        assertFalse(AppointmentStatus.isEditLocked("checked_in", today, "onsite"))
        assertFalse(AppointmentStatus.isEditLocked("completed", today, "onsite"))
    }

    // เดิมกฎนี้ปล่อยนัดที่เลยวันมาแล้วให้แก้ได้ ซึ่งเป็นช่องโหว่: ลบนัดที่ขาดไม่ได้
    // แต่แก้วันนัดเป็นอนาคตได้ สถานะก็กลับเป็น planned เท่ากับลบร่องรอยทิ้ง
    @Test
    fun `editing an onsite appointment that was already missed is locked`() {
        val yesterday = LocalDate.now().minusDays(1).toString()
        assertTrue(AppointmentStatus.isEditLocked("planned", yesterday, "onsite"))
    }

    // online/call ไม่มีสถานะขาดนัด จึงแก้ย้อนหลังได้ตามเดิม
    @Test
    fun `editing a past online appointment is not locked`() {
        val yesterday = LocalDate.now().minusDays(1).toString()
        assertFalse(AppointmentStatus.isEditLocked("planned", yesterday, "online"))
        assertFalse(AppointmentStatus.isEditLocked("planned", yesterday, "call"))
    }

    // ลบไม่ได้ทั้งสองกรณี: ใกล้วันนัด (กันยกเลิกกระชั้นชิด) และขาดนัดไปแล้ว (กันลบร่องรอยการพลาดนัด)
    @Test
    fun `delete is locked within 7 days of the appointment`() {
        val in3Days = LocalDate.now().plusDays(3).toString()
        assertTrue(AppointmentStatus.isDeleteLocked("planned", in3Days, "onsite"))
    }

    @Test
    fun `delete is locked for an onsite appointment that was already missed`() {
        val lastWeek = LocalDate.now().minusDays(7).toString()
        // ตอนนี้ขาดนัดทำให้ล็อกทั้งแก้และลบ — กติกาเดียวกัน เพราะแก้วันนัดก็ลบร่องรอยได้เหมือนกัน
        assertTrue(AppointmentStatus.isEditLocked("planned", lastWeek, "onsite"))
        assertTrue(AppointmentStatus.isDeleteLocked("planned", lastWeek, "onsite"))
    }

    // online/call ไม่มีสถานะขาดนัด จึงลบแผนที่เลยวันไปแล้วได้ (ไม่มีอะไรให้ "พลาด")
    @Test
    fun `delete stays allowed for past online and call appointments`() {
        val lastWeek = LocalDate.now().minusDays(7).toString()
        assertFalse(AppointmentStatus.isDeleteLocked("planned", lastWeek, "online"))
        assertFalse(AppointmentStatus.isDeleteLocked("planned", lastWeek, "call"))
    }

    @Test
    fun `delete stays allowed for a distant future plan and for finished ones`() {
        val in30Days = LocalDate.now().plusDays(30).toString()
        val lastWeek = LocalDate.now().minusDays(7).toString()
        assertFalse(AppointmentStatus.isDeleteLocked("planned", in30Days, "onsite"))
        assertFalse(AppointmentStatus.isDeleteLocked("checked_in", lastWeek, "onsite"))
        assertFalse(AppointmentStatus.isDeleteLocked("completed", lastWeek, "onsite"))
    }
}
