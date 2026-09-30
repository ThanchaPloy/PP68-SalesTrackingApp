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

    /**
     * นัดชนิดนี้ต้องไปปรากฏตัวที่หน้างานหรือไม่ — ใช้ตัดสินทั้งเรื่องขาดนัดและเรื่องที่ควร
     * เตือนพิกัด/เสนอเช็คอิน เปิดออกมาเพราะ ProximityMonitorService ต้องถามกติกาเดียวกันนี้
     */
    fun requiresCheckIn(activityType: String?): Boolean =
        activityType?.lowercase() in REQUIRES_CHECK_IN

    /**
     * ชื่อชนิดนัดที่เอาไปโชว์ให้ผู้ใช้อ่านได้ — รหัสที่เก็บใน DB เป็นภาษาอังกฤษตัวเล็ก
     * ("onsite"/"online"/"call") ซึ่งไม่ควรหลุดขึ้นหน้าจอ อยู่ที่นี่เพราะเป็นกติกาของ "ชนิดนัด"
     * ชุดเดียวกับ requiresCheckIn รหัสที่ไม่รู้จักคืนค่าเดิมไป ดีกว่าโชว์ค่าว่าง
     */
    fun typeLabel(activityType: String?): String = when (activityType?.lowercase()) {
        "onsite" -> "เข้าพบที่ไซต์งาน"
        "online" -> "ประชุมออนไลน์"
        "call"   -> "โทรศัพท์"
        else     -> activityType.orEmpty()
    }

    // ต้องเลยวัน ไม่ใช่เลยเวลา — นัดตอนเช้าของวันนี้ที่ยังไม่เช็คอินตอนบ่ายยังไม่นับขาดนัด
    // ต้องข้ามเที่ยงคืนไปแล้วเท่านั้นถึงจะกลายเป็น missing
    //
    // activityType เป็นพารามิเตอร์บังคับโดยตั้งใจ (ไม่ใส่ default) — ทุกจุดที่เรียกต้องตัดสินใจเอง
    // ว่านัดชนิดไหน ถ้าใส่ default ไว้ จุดที่ลืมส่งจะเงียบๆ กลับไปคิดว่าทุกชนิดขาดนัดได้เหมือนเดิม
    fun effective(status: String?, plannedDate: String?, activityType: String?): String {
        val fallback = status ?: "planned"
        if (!requiresCheckIn(activityType)) return fallback
        val date = parseDate(plannedDate) ?: return fallback
        return if (status == "planned" && date.isBefore(LocalDate.now())) MISSING else fallback
    }

    // ห้ามแก้ไขแผนนัดหมายเมื่อ
    //   (ก) เหลือเวลา <= 7 วันก่อนถึงวันนัด — กันเปลี่ยน/ยกเลิกกระชั้นชิด
    //   (ข) ขาดนัดไปแล้ว — ด้วยเหตุผลเดียวกับที่ห้ามลบ: แผนที่ขาดนัดคือหลักฐาน
    //       การแก้วันนัดเป็นอนาคตทำให้สถานะกลับเป็น planned เท่ากับลบร่องรอยทิ้ง แค่ใช้ทางอ้อม
    //
    // การ "บันทึกผล" ไม่ถูกล็อกโดยกติกานี้ — นัดที่ขาดไปต้องบันทึกย้อนหลังได้เสมอ
    fun isEditLocked(status: String?, plannedDate: String?, activityType: String?): Boolean {
        if (status != "planned") return false
        if (effective(status, plannedDate, activityType) == MISSING) return true
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
    // ตอนนี้กติกาเดียวกับการแก้ไข — คงชื่อไว้เพราะผู้เรียกอ่านแล้วเข้าใจเจตนาชัดกว่า
    fun isDeleteLocked(status: String?, plannedDate: String?, activityType: String?): Boolean =
        isEditLocked(status, plannedDate, activityType)

    private fun parseDate(raw: String?): LocalDate? =
        raw?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
}
