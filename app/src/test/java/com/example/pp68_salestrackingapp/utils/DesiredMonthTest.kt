package com.example.pp68_salestrackingapp.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class DesiredMonthTest {
    @Test fun `upcoming starts at the given month, crosses the year and is stored as day 1`() {
        val list = DesiredMonth.upcoming(YearMonth.of(2026, 11), 3)
        assertEquals(listOf("2026-11-01", "2026-12-01", "2027-01-01"), list)
    }

    @Test fun `label uses thai month and buddhist year`() {
        assertEquals("ธ.ค. 2569", DesiredMonth.label("2026-12-01"))
        assertEquals("ม.ค. 2570", DesiredMonth.label("2027-01-15"))
    }

    @Test fun `label never throws on blank or garbage from the server`() {
        assertNull(DesiredMonth.label(null))
        assertNull(DesiredMonth.label(""))
        assertNull(DesiredMonth.label("not-a-date"))
    }

    @Test fun `missing only when status is Quotation and the month is blank`() {
        assertTrue(DesiredMonth.isMissing("Quotation", null))
        assertTrue(DesiredMonth.isMissing("Quotation", " "))
        assertFalse(DesiredMonth.isMissing("Quotation", "2026-12-01"))
        assertFalse(DesiredMonth.isMissing("Bidding", null))
        assertFalse(DesiredMonth.isMissing(null, null))
    }
}
