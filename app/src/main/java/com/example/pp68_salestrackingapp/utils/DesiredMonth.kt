package com.example.pp68_salestrackingapp.utils

import java.time.LocalDate
import java.time.YearMonth

/**
 * "วันที่ต้องการสินค้า" ของโครงการ — ผู้ใช้เลือกแค่เดือน ไม่เจาะจงวัน
 *
 * เก็บลงคอลัมน์เดิม desired_completion_date (ชนิด date) เป็น "วันที่ 1 ของเดือนนั้น" เช่น 2026-12-01
 * ความหมายคือ "ทั้งเดือนธันวาคม" ไม่ใช่วันที่ 1 ธ.ค. — ห้ามเอาไปแสดงเป็นวันที่หรือเทียบเป็นกำหนดส่งรายวัน
 */
object DesiredMonth {
    /** สเตจที่ต้องมีเดือนที่ต้องการสินค้าก่อนบันทึก */
    const val REQUIRED_STATUS = "Quotation"

    private val THAI_MONTHS = listOf(
        "ม.ค.", "ก.พ.", "มี.ค.", "เม.ย.", "พ.ค.", "มิ.ย.",
        "ก.ค.", "ส.ค.", "ก.ย.", "ต.ค.", "พ.ย.", "ธ.ค."
    )

    /** เดือนที่ให้เลือก: เดือนปัจจุบัน + [count]-1 เดือนถัดไป */
    fun upcoming(from: YearMonth, count: Int = 24): List<String> =
        (0 until count).map { toStorage(from.plusMonths(it.toLong())) }

    fun toStorage(ym: YearMonth): String = ym.atDay(1).toString()

    /** "ธ.ค. 2569" — คืน null ถ้าว่างหรืออ่านไม่ออก (ไม่ throw: ค่านี้มาจาก server ได้) */
    fun label(stored: String?): String? {
        if (stored.isNullOrBlank()) return null
        return try {
            val d = LocalDate.parse(stored.take(10))
            "${THAI_MONTHS[d.monthValue - 1]} ${d.year + 543}"
        } catch (_: Exception) { null }
    }

    /** true = สเตจนี้ต้องมีเดือน แต่ยังไม่มี */
    fun isMissing(status: String?, stored: String?): Boolean =
        status == REQUIRED_STATUS && stored.isNullOrBlank()
}
