package com.example.pp68_salestrackingapp.utils

import com.example.pp68_salestrackingapp.data.model.LossReasonMaster

// รายชื่อเหตุผลที่ไม่ได้งานเดียวที่ทุกจุดในแอปต้องอ้างอิง — เคยกระจาย hardcode อยู่ 3 ไฟล์
// (AddProjectViewModel, SalesResultViewModel, SalesResultScreen preview) เสี่ยง wording เพี้ยนกันเอง
//
// W5a: ค่าด้านล่างนี้คือ fallback เท่านั้น — ของจริงมาจาก /loss_reason_master (SyncManager เรียก
// applyServerData() ให้ตอน login) รหัส (code) ต้องคงเดิมทุกตัวอักษรเสมอ เพราะ
// chk_project_loss_reason/chk_activity_result_loss_reason ฝั่ง backend ยังอ้างค่าเดิมอยู่ —
// ที่แก้ได้จาก master data คือ label/sequence เท่านั้น
object LossReasons {
    const val OTHER = "อื่น ๆ"

    private val DEFAULT_OPTIONS = listOf(
        "ผลิตไม่ได้/ผลิตไม่ทัน",
        "เทคโนโลยีไม่ผ่าน",
        "สู้ราคาไม่ไหว",
        OTHER
    )

    @Volatile
    private var masters: List<LossReasonMaster>? = null

    val OPTIONS: List<String>
        get() = masters?.sortedBy { it.sequence }?.map { it.code } ?: DEFAULT_OPTIONS

    // OPTIONS คืน code เพราะเป็นค่าที่ต้องผูกกับ backend constraint เสมอ — จุดที่ "แสดง" ให้ผู้ใช้
    // เห็นต้องผ่านฟังก์ชันนี้ ไม่งั้นแอดมินแก้ label จาก master data ไปแล้วไม่มีผลกับสิ่งที่ผู้ใช้เห็น
    fun labelFor(code: String?): String =
        masters?.firstOrNull { it.code == code }?.label ?: code.orEmpty()

    // เรียกจาก SyncManager หลัง login สำเร็จ — ว่างเปล่า (offline/error) แปลว่ายังใช้ fallback ต่อ
    fun applyServerData(data: List<LossReasonMaster>) {
        if (data.isNotEmpty()) masters = data
    }

    // เรียกตอน logout — กันค่า master data ของบัญชีเก่าค้างข้ามไปยังบัญชีถัดไปที่ล็อกอินบนเครื่องเดียวกัน
    fun clearServerData() {
        masters = null
    }
}
