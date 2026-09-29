package com.example.pp68_salestrackingapp.utils

import com.example.pp68_salestrackingapp.data.model.ProjectStageMaster

// รายชื่อ stage เดียวที่ทุกจุดในแอปต้องอ้างอิง — เคยกระจาย hardcode อยู่ 8+ ไฟล์ จนมีค่าผีอย่าง
// "Completed"/"Active" หลุดเข้าไปในโค้ดสถิติ ทั้งที่ไม่เคยมีโปรเจกต์จริงเป็นค่านั้นเลยสักแถวเดียว
// (เช็คจาก project_stage_log แล้ว) และ backend ก็ไม่ยอมรับค่านอกลิสต์นี้อยู่แล้ว (chk_project_status)
//
// W5a: ค่าด้านล่างนี้คือ fallback เท่านั้น — ของจริงมาจาก /project_stage_master (SyncManager
// เรียก applyServerData() ให้ตอน login) รหัส (code) ต้องคงเดิมทุกตัวอักษรเสมอไม่ว่าจะแก้จาก DB
// ยังไง เพราะ trg_project_status_update_pct, chk_project_status ฝั่ง backend ยังอ้างค่าเดิมอยู่ —
// ที่แก้ได้จาก master data คือ label/sequence/probability_pct/is_closed/is_won เท่านั้น
object ProjectStages {
    private val DEFAULT_ORDER = listOf(
        "Lead", "New Project", "Quotation", "Bidding",
        "Make a Decision", "Assured", "PO", "Lost", "Failed"
    )
    private val DEFAULT_CLOSED: Set<String> = setOf("Lost", "Failed", "PO")
    private val DEFAULT_WON: Set<String> = setOf("PO")
    private val DEFAULT_PROBABILITY = mapOf(
        "Lead" to 10, "New Project" to 20, "Quotation" to 40, "Bidding" to 50,
        "Make a Decision" to 70, "Assured" to 80, "PO" to 100, "Lost" to 0, "Failed" to 0
    )

    @Volatile
    private var masters: List<ProjectStageMaster>? = null

    val SELECTABLE: List<String>
        get() = masters?.sortedBy { it.sequence }?.map { it.code } ?: DEFAULT_ORDER

    val LOST: Set<String>
        get() = masters?.filter { it.isClosed && !it.isWon }?.map { it.code }?.toSet() ?: setOf("Lost", "Failed")

    val CLOSED: Set<String>
        get() = masters?.filter { it.isClosed }?.map { it.code }?.toSet() ?: DEFAULT_CLOSED

    fun probabilityPct(code: String?): Int =
        masters?.firstOrNull { it.code == code }?.probabilityPct ?: DEFAULT_PROBABILITY[code] ?: 0

    // SELECTABLE คืน code เพราะต้องเป็นค่าที่ผูกกับ project.projectStatus/backend constraint เสมอ —
    // จุดไหนจะ "แสดง" ให้ผู้ใช้เห็นต้องผ่านฟังก์ชันนี้ ไม่งั้นแอดมินแก้ label จาก master data ไปแล้ว
    // ผู้ใช้จะยังเห็น code ภาษาอังกฤษเดิมอยู่ดี ไม่มีผลอะไรเลย
    fun labelFor(code: String?): String =
        masters?.firstOrNull { it.code == code }?.label ?: code.orEmpty()

    // เรียกจาก SyncManager หลัง login สำเร็จ — ว่างเปล่า (offline/error) แปลว่ายังใช้ fallback ต่อ
    fun applyServerData(data: List<ProjectStageMaster>) {
        if (data.isNotEmpty()) masters = data
    }

    // เรียกตอน logout — กันค่า master data ของบัญชีเก่าค้างข้ามไปยังบัญชีถัดไปที่ล็อกอินบนเครื่องเดียวกัน
    fun clearServerData() {
        masters = null
    }
}
