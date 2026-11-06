package com.example.pp68_salestrackingapp.utils

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * แปลงสตริงเวลาที่ backend ส่งมาให้เป็น [Instant] — จุดเดียวของทั้งแอปที่รู้ว่า server ส่งมากี่แบบ
 *
 * ### ทำไมต้องมีไฟล์นี้
 *
 * ค่าเวลาเดียวกันเดินทางมาถึงแอปได้ 3 รูปแบบ แล้วแต่ว่าเดินมาทางไหน — ไม่ใช่ความไม่สม่ำเสมอแบบสุ่ม
 * แต่มาจากตัวแปลง 3 ตัวคนละชั้นกันฝั่ง server:
 *
 * | ทางที่ค่าเดินมา | ตัวแปลง | ผลลัพธ์ |
 * |---|---|---|
 * | REST route ปกติ (GET/PATCH response) | Exposed `timestamp()` → `Instant.toString()` | `2026-11-06T23:22:20.244199Z` |
 * | delta sync — คอลัมน์เป็น `timestamptz` | trigger ใช้ `to_jsonb(NEW)` ซึ่ง Postgres render ตาม timezone ของ session | `2026-11-07T06:18:16.371395+07:00` |
 * | delta sync — ผ่าน view `v_unified_customer` | view cast `updated_at::TIMESTAMP` ทิ้ง timezone ไปตั้งแต่ใน SQL | `2026-11-07T05:50:54.990561` |
 *
 * ### ทำไมใช้ `Instant.parse` เฉย ๆ ไม่ได้
 *
 * แอปตั้ง `minSdk = 26` และไม่ได้เปิด core library desugaring `java.time` จึงมาจากแพลตฟอร์ม Android
 * ซึ่งบนรุ่นเก่ากว่า `Instant.parse` รับได้เฉพาะรูปแบบที่ลงท้ายด้วย `Z` (ความสามารถรับ offset เพิ่มเข้ามา
 * ใน JDK 12 เครื่อง Android รุ่นเก่ายังไม่มี) ผลคือ parse ไม่ผ่านแล้วโค้ดเดิมกลืน exception เงียบ ๆ —
 * `isCheckInLate` คืน `false` ตลอด ป้าย "ช้ากว่าเวลานัด" จึงไม่เคยขึ้นเลยสำหรับแถวที่มาจาก sync
 *
 * ### ค่าที่ไม่มี timezone ติดมา ตีความเป็นเวลาไทย
 *
 * server เขียนค่าด้วย `Instant.now()` ผ่าน JDBC ลงคอลัมน์ `timestamp without time zone` ไดรเวอร์จึง
 * แปลงด้วย timezone ของ JVM ฝั่ง server (Asia/Bangkok) ค่าที่ไม่มี offset จึงเป็นเวลาไทยเสมอ —
 * ไม่ใช่ UTC และไม่ใช่ timezone ของเครื่องผู้ใช้ (ถ้าเซลส์เปิดแอปตอนอยู่ต่างประเทศ การใช้ timezone
 * ของเครื่องจะทำให้เวลาเพี้ยนไปตามที่เขายืนอยู่) ใช้โซนเดียวกับ [AppointmentPolicy] ที่ปักหมุดไว้แล้ว
 */
private val SERVER_ZONE: ZoneId = ZoneId.of("Asia/Bangkok")

fun parseServerInstant(iso: String?): Instant? {
    val text = iso?.trim()
    if (text.isNullOrEmpty()) return null
    // ลงท้าย Z — ทางที่เจอบ่อยที่สุด ลองก่อน
    runCatching { return Instant.parse(text) }
    // มี offset ติดมา เช่น +07:00
    runCatching { return OffsetDateTime.parse(text).toInstant() }
    // ไม่มี timezone เลย — เวลาไทย
    runCatching { return LocalDateTime.parse(text).atZone(SERVER_ZONE).toInstant() }
    return null
}

/** เวลาตามนาฬิกาเครื่องผู้ใช้ สำหรับเอาไปโชว์ คืน null ถ้าอ่านค่าไม่ออก */
fun parseServerLocalTime(iso: String?): java.time.LocalTime? =
    parseServerInstant(iso)?.atZone(ZoneId.systemDefault())?.toLocalTime()

/** ฟอร์แมตไว้โชว์ตามรูปแบบที่ให้มา คืนสตริงดิบถ้าอ่านค่าไม่ออก ดีกว่าโชว์ช่องว่าง */
fun formatServerTime(iso: String?, pattern: String, locale: java.util.Locale = java.util.Locale.getDefault()): String {
    if (iso.isNullOrBlank()) return ""
    val instant = parseServerInstant(iso) ?: return iso
    return instant.atZone(ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern(pattern, locale))
}
