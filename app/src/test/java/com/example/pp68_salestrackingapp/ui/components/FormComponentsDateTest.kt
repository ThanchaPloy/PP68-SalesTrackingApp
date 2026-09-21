package com.example.pp68_salestrackingapp.ui.components

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class FormComponentsDateTest {

    private lateinit var original: Locale

    @Before
    fun setUp() {
        original = Locale.getDefault()
        // เครื่องของเซลส์ส่วนใหญ่ตั้งเป็นภาษาไทย ซึ่งทำให้ SimpleDateFormat ใช้ BuddhistCalendar
        Locale.setDefault(Locale("th", "TH"))
    }

    @After
    fun tearDown() {
        Locale.setDefault(original)
    }

    private fun utcMillisOf(iso: String): Long =
        SimpleDateFormat("yyyy-MM-dd", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .parse(iso)!!.time

    @Test
    fun `date sent to the server stays in the common era on a Thai device`() {
        val formatted = isoDateFormat().format(Date(utcMillisOf("2026-04-06")))
        assertEquals("2026-04-06", formatted)
    }

    @Test
    fun `stored dates round-trip back through the picker unchanged`() {
        val millis = isoDateFormat().parse("2026-04-06")!!.time
        assertEquals("2026-04-06", isoDateFormat().format(Date(millis)))
    }

    // ตัวเทียบ: ถ้าใช้ Locale.getDefault() ตามเดิม ปีจะกลายเป็น พ.ศ. ซึ่งคือบั๊กที่แก้ไป
    @Test
    fun `the default locale is what used to corrupt the year`() {
        val buggy = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(utcMillisOf("2026-04-06")))
        assertEquals("2569-04-06", buggy)
    }
}
