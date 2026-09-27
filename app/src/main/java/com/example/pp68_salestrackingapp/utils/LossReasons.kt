package com.example.pp68_salestrackingapp.utils

// รายชื่อเหตุผลที่ไม่ได้งานเดียวที่ทุกจุดในแอปต้องอ้างอิง — เคยกระจาย hardcode อยู่ 3 ไฟล์
// (AddProjectViewModel, SalesResultViewModel, SalesResultScreen preview) เสี่ยง wording เพี้ยนกันเอง
object LossReasons {
    val OPTIONS = listOf(
        "ผลิตไม่ได้/ผลิตไม่ทัน",
        "เทคโนโลยีไม่ผ่าน",
        "สู้ราคาไม่ไหว",
        "อื่น ๆ"
    )
    const val OTHER = "อื่น ๆ"
}
