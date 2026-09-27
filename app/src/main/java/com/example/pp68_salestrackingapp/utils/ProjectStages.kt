package com.example.pp68_salestrackingapp.utils

// รายชื่อ stage เดียวที่ทุกจุดในแอปต้องอ้างอิง — เคยกระจาย hardcode อยู่ 8+ ไฟล์ จนมีค่าผีอย่าง
// "Completed"/"Active" หลุดเข้าไปในโค้ดสถิติ ทั้งที่ไม่เคยมีโปรเจกต์จริงเป็นค่านั้นเลยสักแถวเดียว
// (เช็คจาก project_stage_log แล้ว) และ backend ก็ไม่ยอมรับค่านอกลิสต์นี้อยู่แล้ว (chk_project_status)
object ProjectStages {
    val SELECTABLE = listOf(
        "Lead", "New Project", "Quotation", "Bidding",
        "Make a Decision", "Assured", "PO", "Lost", "Failed"
    )
    val LOST: Set<String> = setOf("Lost", "Failed")
    val CLOSED: Set<String> = LOST + "PO"
}
