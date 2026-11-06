package com.example.pp68_salestrackingapp.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import java.time.Instant
import org.junit.Test

/**
 * สามรูปแบบข้างล่างไม่ได้แต่งขึ้น — ดึงมาจาก Room ของเครื่องจริงที่ใช้งานอยู่ (7 พ.ย. 2569)
 * ค่าเดียวกันหน้าตาต่างกันเพราะเดินทางมาคนละเส้น ดู KDoc ของ [parseServerInstant]
 */
class IsoTimeTest {

    // REST route: Exposed timestamp() -> Instant.toString()
    @Test
    fun `reads the Z form the REST routes send`() {
        assertEquals(
            Instant.parse("2026-11-06T23:22:20.244199Z"),
            parseServerInstant("2026-11-06T23:22:20.244199Z")
        )
    }

    // delta sync บนคอลัมน์ timestamptz: to_jsonb render ตาม timezone ของ DB session
    @Test
    fun `reads the offset form the sync feed sends`() {
        assertEquals(
            Instant.parse("2026-11-06T23:18:16.371395Z"),
            parseServerInstant("2026-11-07T06:18:16.371395+07:00")
        )
    }

    // delta sync ผ่าน view v_unified_customer ที่ cast updated_at::TIMESTAMP ทิ้ง timezone ไปแล้ว
    // ค่าที่เหลือเป็นเวลาไทย เพราะ JVM ฝั่ง server เขียนลงคอลัมน์ไร้ timezone ด้วยโซนของตัวเอง
    @Test
    fun `reads the form with no timezone as Bangkok time`() {
        assertEquals(
            Instant.parse("2026-11-06T22:50:54.990561Z"), // 05:50:54 +07:00
            parseServerInstant("2026-11-07T05:50:54.990561")
        )
    }

    @Test
    fun `handles whitespace, null and blank without throwing`() {
        assertEquals(
            Instant.parse("2026-11-06T23:22:20Z"),
            parseServerInstant("  2026-11-06T23:22:20Z  ")
        )
        assertNull(parseServerInstant(null))
        assertNull(parseServerInstant("   "))
    }

    // อ่านไม่ออกต้องคืน null ไม่ใช่โยน exception — ผู้เรียกทุกตัวพึ่งพฤติกรรมนี้เพื่อ fallback ไปโชว์ค่าดิบ
    @Test
    fun `returns null rather than throwing on a value it cannot read`() {
        assertNull(parseServerInstant("ไม่ใช่เวลา"))
        assertNull(parseServerInstant("2026-13-45T99:99:99Z"))
        assertNull(parseServerInstant("2026-11-07")) // วันที่ล้วน ไม่มีส่วนเวลา
    }

    // formatServerTime คืนค่าดิบเมื่ออ่านไม่ออก ดีกว่าปล่อยช่องว่างให้ผู้ใช้งง
    @Test
    fun `formatting falls back to the raw string`() {
        assertEquals("ค่าเพี้ยน", formatServerTime("ค่าเพี้ยน", "dd MMM HH:mm"))
        assertEquals("", formatServerTime(null, "dd MMM HH:mm"))
    }
}
