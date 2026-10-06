package com.example.pp68_salestrackingapp.utils

// เหลือเฉพาะกติกาเรื่อง "ชนิดนัด" — กฎเรื่องเวลา (ขาดนัด / ห้ามแก้ / ห้ามลบ / บันทึกผล)
// ย้ายไป AppointmentPolicy ซึ่งรับ Clock เข้าไปและเขียนเทสต์เส้นแบ่งเวลาได้
object AppointmentStatus {
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

}
