package com.example.pp68_salestrackingapp.utils

import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * กติกาเดียวของเรื่อง "แก้ไข / ลบ / บันทึกผล" นัดหมาย (แผนงาน B.1)
 *
 * เป็น pure function ล้วน รับเวลาผ่าน [Clock] ไม่เรียก now() เองที่ไหน เพราะกติกาทั้งชุดขึ้นกับ
 * "ตอนนี้กี่โมง" ถ้าอ่านนาฬิกาข้างในจะเขียน test เรื่องเส้นแบ่งเวลาไม่ได้เลย
 *
 * ใช้เขตเวลาไทยคงที่ ไม่ใช่เขตเวลาเครื่อง เพราะวันนัดกับเวลานัดที่เก็บในฐานข้อมูลเป็นเวลาไทย
 * เครื่องที่ตั้งโซนอื่นต้องได้คำตอบเดียวกับเครื่องในไทย
 *
 * ฝั่ง backend มี AppointmentPolicy ที่ให้คำตอบชุดเดียวกัน รหัสเหตุผลต้องตรงกันทั้งสองฝั่ง
 * เพราะ sync classifier ฝั่งแอปใช้รหัสนี้ตัดสินว่าเป็น rejection ถาวร ไม่ใช่ error ชั่วคราวที่ retry ได้
 */
object AppointmentPolicy {

    val THAI_ZONE: ZoneId = ZoneId.of("Asia/Bangkok")

    const val MISSING = "missing"

    // รหัสเหตุผล — ต้องตรงกับฝั่ง backend ตัวอักษรต่อตัวอักษร
    const val EDIT_WINDOW_CLOSED = "APPOINTMENT_EDIT_WINDOW_CLOSED"
    const val DELETE_WINDOW_CLOSED = "APPOINTMENT_DELETE_WINDOW_CLOSED"
    const val ONSITE_CHECKIN_REQUIRED = "ONSITE_CHECKIN_REQUIRED"
    const val MISSED_ONSITE_RESULT_NOT_ALLOWED = "MISSED_ONSITE_RESULT_NOT_ALLOWED"
    const val TIME_MISSING = "APPOINTMENT_TIME_MISSING"
    const val STATUS_LOCKED = "APPOINTMENT_STATUS_LOCKED"

    /** ข้อมูลเท่าที่กติกาต้องใช้ ไม่รับทั้ง entity เพื่อให้ฝั่ง card/row ที่มีไม่ครบก็เรียกได้ */
    data class Facts(
        val status: String?,
        val activityType: String?,
        val plannedDate: String?,
        val plannedTime: String?,
        val checkedIn: Boolean
    )

    sealed interface Decision {
        data object Allowed : Decision
        data class Denied(val code: String, val message: String) : Decision
    }

    fun Decision.allowed(): Boolean = this is Decision.Allowed
    fun Decision.deniedMessage(): String? = (this as? Decision.Denied)?.message

    /**
     * ขาดนัด = นัดหน้างาน + พ้นวันนัดแล้ว + ไม่เคยเช็คอิน
     *
     * ต่างจากของเดิมตรงที่เดิมไม่ดูเช็คอินเลย นัดที่เช็คอินแล้วแต่ยังไม่บันทึกผลจึงถูกเรียกว่าขาดนัด
     * ทั้งที่ไปถึงหน้างานจริง
     */
    fun effectiveStatus(facts: Facts, clock: Clock = systemClock()): String {
        val fallback = facts.status ?: "planned"
        if (fallback != "planned") return fallback
        if (!AppointmentStatus.requiresCheckIn(facts.activityType)) return fallback
        if (facts.checkedIn) return fallback
        val date = parseDate(facts.plannedDate) ?: return fallback
        return if (today(clock).isAfter(date)) MISSING else fallback
    }

    /**
     * แก้ไขแผนได้จนถึงก่อนเวลาเริ่มนัด เวลาเท่ากับเวลานัดพอดีถือว่าปิดช่วงแก้ไขแล้ว
     *
     * แถวเก่าที่ไม่มีเวลาเริ่มจะถูกล็อกตั้งแต่ 00:00 ของวันนัด เพราะการเดาเวลาแทนผู้ใช้
     * อาจเปิดให้แก้แผนหลังเริ่มนัดไปแล้วจริง ๆ
     */
    fun canEdit(facts: Facts, clock: Clock = systemClock()): Decision {
        lockedByStatus(facts, clock, "แก้ไขแผน")?.let { return it }
        val date = parseDate(facts.plannedDate)
            ?: return Decision.Denied(TIME_MISSING, "นัดนี้ไม่มีวันนัดที่อ่านได้ จึงแก้ไขแผนไม่ได้ กรุณาแจ้งผู้ดูแลระบบ")

        val now = now(clock)
        if (now.toLocalDate().isBefore(date)) return Decision.Allowed
        if (now.toLocalDate().isAfter(date)) {
            return Decision.Denied(EDIT_WINDOW_CLOSED, "เลยเวลาเริ่มนัดแล้ว จึงแก้ไขแผนไม่ได้")
        }

        val start = parseTime(facts.plannedTime)
            ?: return Decision.Denied(TIME_MISSING, "นัดนี้ไม่มีเวลาเริ่ม จึงล็อกการแก้ไขตั้งแต่ 00:00 ของวันนัด")
        return if (now.isBefore(date.atTime(start))) Decision.Allowed
        else Decision.Denied(EDIT_WINDOW_CLOSED, "เลยเวลาเริ่มนัดแล้ว จึงแก้ไขแผนไม่ได้")
    }

    /**
     * ลบได้เฉพาะก่อนถึงวันนัด ไม่ใช่ก่อนเวลานัด — พอขึ้นวันนัดแล้วแผนกลายเป็นหลักฐานว่าวันนั้น
     * มีนัดอะไรอยู่ ลบทิ้งเท่ากับลบร่องรอย ต่างจากการแก้ไขที่ยังยอมให้ขยับได้จนถึงเวลาเริ่ม
     */
    fun canDelete(facts: Facts, clock: Clock = systemClock()): Decision {
        lockedByStatus(facts, clock, "ลบแผน")?.let { return it }
        val date = parseDate(facts.plannedDate)
            ?: return Decision.Denied(TIME_MISSING, "นัดนี้ไม่มีวันนัดที่อ่านได้ จึงลบแผนไม่ได้ กรุณาแจ้งผู้ดูแลระบบ")
        return if (today(clock).isBefore(date)) Decision.Allowed
        else Decision.Denied(DELETE_WINDOW_CLOSED, "ถึงวันนัดแล้ว จึงลบแผนไม่ได้ หากไม่ได้ไปตามนัดให้บันทึกผลแทน")
    }

    /**
     * นัดออนไลน์/โทรศัพท์บันทึกผลได้เสมอ เพราะไม่มีขั้นตอนไปปรากฏตัวให้พิสูจน์
     * นัดหน้างานต้องผ่านเช็คอิน และถ้าพ้นวันนัดไปแล้วโดยไม่เคยเช็คอิน ถือว่าขาดนัด บันทึกย้อนหลังไม่ได้
     */
    fun canCreateResult(facts: Facts, clock: Clock = systemClock()): Decision {
        if (facts.status == "completed") {
            return Decision.Denied(STATUS_LOCKED, "นัดนี้บันทึกผลไปแล้ว หากต้องการแก้ไขให้ใช้การแก้ไขผลการขาย")
        }
        if (facts.status == "cancelled") {
            return Decision.Denied(STATUS_LOCKED, "นัดนี้ถูกยกเลิกแล้ว จึงบันทึกผลไม่ได้")
        }
        if (!AppointmentStatus.requiresCheckIn(facts.activityType)) return Decision.Allowed
        if (facts.checkedIn) return Decision.Allowed

        val date = parseDate(facts.plannedDate)
            ?: return Decision.Denied(ONSITE_CHECKIN_REQUIRED, "นัดเข้าพบที่ไซต์งานต้อง Check-in ก่อนบันทึกผล")
        return if (today(clock).isAfter(date)) {
            Decision.Denied(
                MISSED_ONSITE_RESULT_NOT_ALLOWED,
                "นัด On-site นี้ขาดนัดและไม่มี Check-in จึงบันทึกผลย้อนหลังไม่ได้"
            )
        } else {
            Decision.Denied(ONSITE_CHECKIN_REQUIRED, "นัดเข้าพบที่ไซต์งานต้อง Check-in ก่อนบันทึกผล")
        }
    }

    /**
     * แถวที่กติกาตัดสินจากข้อมูลไม่ครบ ควรถูกรายงานเป็นปัญหาคุณภาพข้อมูล ไม่ใช่ปล่อยให้ผู้ใช้
     * เจอแต่ปุ่มที่กดไม่ได้โดยไม่มีใครรู้ว่าต้นเหตุคือแถวไม่มีเวลานัด
     */
    fun hasTimeQualityIssue(facts: Facts): Boolean =
        parseDate(facts.plannedDate) == null || parseTime(facts.plannedTime) == null

    private fun lockedByStatus(facts: Facts, clock: Clock, action: String): Decision.Denied? =
        when (effectiveStatus(facts, clock)) {
            "planned" -> null
            "completed" -> Decision.Denied(STATUS_LOCKED, "นัดนี้บันทึกผลแล้ว จึง${action}ไม่ได้")
            "cancelled" -> Decision.Denied(STATUS_LOCKED, "นัดนี้ถูกยกเลิกแล้ว จึง${action}ไม่ได้")
            MISSING -> Decision.Denied(STATUS_LOCKED, "นัดนี้ขาดนัดไปแล้ว จึง${action}ไม่ได้ ให้บันทึกผลย้อนหลังแทน")
            else -> Decision.Denied(STATUS_LOCKED, "สถานะนัดปัจจุบันไม่อนุญาตให้${action}")
        }

    /**
     * สำหรับ composable ที่ไม่มีทางรับ Clock ผ่าน DI — ส่วน repository/ViewModel ต้องส่งนาฬิกา
     * ที่ถูกฉีดเข้ามาเสมอ เพื่อให้เทสต์คุมเวลาได้
     */
    private fun systemClock(): Clock = Clock.system(THAI_ZONE)

    private fun now(clock: Clock): LocalDateTime = LocalDateTime.now(clock.withZone(THAI_ZONE))

    private fun today(clock: Clock): LocalDate = now(clock).toLocalDate()

    private fun parseDate(raw: String?): LocalDate? =
        raw?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    // เก็บมาทั้ง "HH:mm" และ "HH:mm:ss" แล้วแต่ว่าใครเขียนแถวนั้น
    private fun parseTime(raw: String?): LocalTime? =
        raw?.trim()?.take(5)?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
}

/** สะพานจาก entity/row ที่ใช้จริงมาเป็น [AppointmentPolicy.Facts] — กันการประกอบ Facts ผิดคนละแบบในแต่ละหน้า */
fun com.example.pp68_salestrackingapp.data.model.SalesActivity.policyFacts() = AppointmentPolicy.Facts(
    status = status,
    activityType = activityType,
    plannedDate = activityDate,
    plannedTime = plannedTime,
    checkedIn = !checkInTime.isNullOrBlank()
)

fun com.example.pp68_salestrackingapp.ui.viewmodels.activity.ActivityCard.policyFacts() = AppointmentPolicy.Facts(
    status = planStatus,
    activityType = activityType,
    plannedDate = plannedDate,
    plannedTime = plannedTime,
    checkedIn = !checkInTime.isNullOrBlank()
)
