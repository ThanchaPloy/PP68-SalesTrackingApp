package com.example.pp68_salestrackingapp.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoordinateParserTest {

    @Test
    fun `รูปแบบที่ Google Maps คัดลอกมา — คอมมาเว้นวรรค`() {
        val (lat, lon) = CoordinateParser.parse("13.540690, 100.613096")!!
        assertEquals(13.540690, lat, 0.0)
        assertEquals(100.613096, lon, 0.0)
    }

    @Test
    fun `คอมมาไม่เว้นวรรค และเว้นวรรคเฉย ๆ ก็ได้`() {
        assertEquals(13.54069 to 100.613096, CoordinateParser.parse("13.54069,100.613096"))
        assertEquals(13.5407 to 100.6131, CoordinateParser.parse("13.5407 100.6131"))
    }

    @Test
    fun `มีช่องว่างหน้าหลังยังอ่านได้`() {
        assertEquals(13.7563 to 100.5018, CoordinateParser.parse("  13.7563 , 100.5018  "))
    }

    @Test
    fun `พิกัดติดลบอ่านได้`() {
        assertEquals(-33.8688 to 151.2093, CoordinateParser.parse("-33.8688, 151.2093"))
    }

    @Test
    fun `ค่าเกินช่วงที่เป็นไปได้ไม่ผ่าน`() {
        assertNull(CoordinateParser.parse("128.5, 160.2"))   // lat เกิน 90
        assertNull(CoordinateParser.parse("13.5, 200.1"))    // lon เกิน 180
    }

    @Test
    fun `เลขกลมไม่มีทศนิยมไม่ถือเป็นพิกัด`() {
        assertNull(CoordinateParser.parse("10, 20"))
        assertNull(CoordinateParser.parse("88 99"))
    }

    @Test
    fun `ข้อความค้นหาปกติไม่ถูกตีความเป็นพิกัด`() {
        assertNull(CoordinateParser.parse("สยามพารากอน"))
        assertNull(CoordinateParser.parse("ถนนพระราม 2"))
        assertNull(CoordinateParser.parse("13.540690"))
        assertNull(CoordinateParser.parse("13.5, 100.6, 15"))
    }

    @Test
    fun `กรอบประเทศไทย`() {
        assertTrue(CoordinateParser.isWithinThailand(13.7563, 100.5018))
        assertFalse(CoordinateParser.isWithinThailand(-33.8688, 151.2093))
    }

    // 0.0 ในแกนใดแกนหนึ่งชนกับ sentinel "ยังไม่ตั้งพิกัด" ที่ MapPickerField ใช้อยู่ — ต้องไม่ผ่าน parse
    @Test
    fun `พิกัดที่มีแกนใดแกนหนึ่งเป็น 0-0 ไม่ผ่าน`() {
        assertNull(CoordinateParser.parse("0.0, 100.5018"))
        assertNull(CoordinateParser.parse("13.7563, 0.0"))
    }

    // บาง IME/คีย์บอร์ดคัดลอกมาด้วย non-breaking space ( ) แทนช่องว่างปกติ
    @Test
    fun `คั่นด้วย non-breaking space ก็อ่านได้`() {
        assertEquals(13.7563 to 100.5018, CoordinateParser.parse("13.7563, 100.5018"))
    }
}
