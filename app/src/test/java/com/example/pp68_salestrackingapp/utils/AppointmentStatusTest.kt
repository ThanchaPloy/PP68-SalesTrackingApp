package com.example.pp68_salestrackingapp.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * เหลือเฉพาะกติกาเรื่อง "ชนิดนัด" — กฎที่ขึ้นกับเวลาย้ายไป AppointmentPolicyTest แล้ว
 */
class AppointmentStatusTest {

    @Test
    fun `only onsite appointments require a check-in`() {
        assertTrue(AppointmentStatus.requiresCheckIn("onsite"))
        assertFalse(AppointmentStatus.requiresCheckIn("online"))
        assertFalse(AppointmentStatus.requiresCheckIn("call"))
    }

    @Test
    fun `activity type matching ignores case`() {
        assertTrue(AppointmentStatus.requiresCheckIn("ONSITE"))
        assertTrue(AppointmentStatus.requiresCheckIn("OnSite"))
    }

    @Test
    fun `an unknown or missing type never requires a check-in`() {
        assertFalse(AppointmentStatus.requiresCheckIn(null))
        assertFalse(AppointmentStatus.requiresCheckIn("something-else"))
    }

    @Test
    fun `type labels are readable and unknown codes pass through`() {
        assertEquals("เข้าพบที่ไซต์งาน", AppointmentStatus.typeLabel("onsite"))
        assertEquals("ประชุมออนไลน์", AppointmentStatus.typeLabel("online"))
        assertEquals("โทรศัพท์", AppointmentStatus.typeLabel("call"))
        assertEquals("weird", AppointmentStatus.typeLabel("weird"))
        assertEquals("", AppointmentStatus.typeLabel(null))
    }
}
