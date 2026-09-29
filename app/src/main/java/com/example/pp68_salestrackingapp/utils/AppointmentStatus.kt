package com.example.pp68_salestrackingapp.utils

import java.time.LocalDate
import java.time.temporal.ChronoUnit

// W6: กติกาเรื่อง "ขาดนัด" (เลยวันนัดไปแล้วยังไม่เช็คอิน/บันทึกผล) และ "ห้ามแก้ไข/ลบแผนใกล้วันนัด"
// รวมไว้ที่เดียวเพราะทั้งสองกติกาต้องใช้ status + วันนัดหมายชุดเดียวกัน ในหลายหน้า
// (HomeScreen, ActivityDetailScreen, CheckInScreen, CreateAppointmentViewModel)
object AppointmentStatus {
    const val MISSING = "missing"

    // นัดที่ไม่ต้องเช็คอิน (online/call) ไม่มีวันเป็น "ขาดนัด" — ขาดนัดคือการไม่ไปปรากฏตัวตามนัด
    // ซึ่งวัดจากการเช็คอินด้วย GPS นัดออนไลน์/โทรศัพท์ไม่มีขั้นตอนนั้น จึงบันทึกผลย้อนหลังเมื่อไหร่ก็ได้
    private val REQUIRES_CHECK_IN = setOf("onsite")

    // ต้องเลยวัน ไม่ใช่เลยเวลา — นัดตอนเช้าของวันนี้ที่ยังไม่เช็คอินตอนบ่ายยังไม่นับขาดนัด
    // ต้องข้ามเที่ยงคืนไปแล้วเท่านั้นถึงจะกลายเป็น missing
    //
    // activityType เป็นพารามิเตอร์บังคับโดยตั้งใจ (ไม่ใส่ default) — ทุกจุดที่เรียกต้องตัดสินใจเอง
    // ว่านัดชนิดไหน ถ้าใส่ default ไว้ จุดที่ลืมส่งจะเงียบๆ กลับไปคิดว่าทุกชนิดขาดนัดได้เหมือนเดิม
    fun effective(status: String?, plannedDate: String?, activityType: String?): String {
        val fallback = status ?: "planned"
        if (activityType?.lowercase() !in REQUIRES_CHECK_IN) return fallback
        val date = parseDate(plannedDate) ?: return fallback
        return if (status == "planned" && date.isBefore(LocalDate.now())) MISSING else fallback
    }

    // ห้ามแก้ไข/ลบแผนที่ยังไม่เสร็จ (planned) เมื่อเหลือเวลา <= 7 วันก่อนถึงวันนัด — กันเปลี่ยน/ยกเลิก
    // กระชั้นชิด แผนที่ขาดนัดไปแล้ว (เลยวันมาแล้ว) ไม่เข้าเงื่อนไขนี้ เพราะเป็นคนละกติกากัน
    fun isEditLocked(status: String?, plannedDate: String?): Boolean {
        if (status != "planned") return false
        val date = parseDate(plannedDate) ?: return false
        val daysUntil = ChronoUnit.DAYS.between(LocalDate.now(), date)
        return daysUntil in 0..7
    }

    // กฎการลบแผน รวมไว้ที่เดียวเพราะมีสองที่ที่ต้องเห็นตรงกันเสมอ: ปุ่มลบในหน้า Home (ซ่อน/แสดง)
    // กับ ActivityRepository.deleteActivity (บล็อกจริง) ถ้าแยกกันเขียนจะดริฟต์กันแน่นอน
    //
    // ห้ามลบเมื่อ (ก) อยู่ในช่วง 7 วันก่อนวันนัด — กันยกเลิกกระชั้นชิด
    // หรือ (ข) ขาดนัดไปแล้ว — แผนที่ขาดนัดเป็นหลักฐานว่าเกิดอะไรขึ้น ลบทิ้งเท่ากับลบร่องรอย
    // การพลาดนัด ให้บันทึกผลย้อนหลังแทน (online/call ไม่มีสถานะขาดนัด จึงไม่โดนข้อ (ข) นี้)
    fun isDeleteLocked(status: String?, plannedDate: String?, activityType: String?): Boolean =
        isEditLocked(status, plannedDate) || effective(status, plannedDate, activityType) == MISSING

    private fun parseDate(raw: String?): LocalDate? =
        raw?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
}
