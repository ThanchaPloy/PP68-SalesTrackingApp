package com.example.pp68_salestrackingapp.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity

// แถวที่เซิร์ฟเวอร์ "ปฏิเสธถาวร" (403 ไม่มีสิทธิ์ / 404 ของถูกลบไปแล้ว / 400 ข้อมูลไม่ผ่าน)
// ต่างจากแถวที่แค่ยังส่งไม่สำเร็จเพราะเน็ต — พวกนี้ลองกี่รอบก็ไม่ผ่าน จนกว่าต้นเหตุจะถูกแก้
//
// ทำไมต้องเก็บลง DB ไม่ใช่ Set ในหน่วยความจำแบบเดิม:
//   1. ด่าน logout ต้องแยกให้ออกว่า "รอเน็ต" (ต้องบล็อก) กับ "ถูกปฏิเสธถาวร" (บล็อกไปก็ไม่มีประโยชน์)
//   2. ของเดิมหายทุกครั้งที่ปิดแอป แถวเดิมจึงถูกยิงซ้ำใหม่ทุกครั้งที่เปิดแอปและโดนปฏิเสธซ้ำเหมือนเดิม
//   3. ต้องบอกผู้ใช้ได้ว่าอะไรค้างและเพราะอะไร ไม่ใช่เงียบแล้วไปโผล่เป็น "logout ไม่ได้"
@Entity(tableName = "sync_rejection", primaryKeys = ["entity_type", "entity_id"])
data class SyncRejection(
    @ColumnInfo(name = "entity_type") val entityType: String,
    @ColumnInfo(name = "entity_id")   val entityId: String,
    @ColumnInfo(name = "http_code")   val httpCode: Int,
    @ColumnInfo(name = "reason")      val reason: String? = null,
    @ColumnInfo(name = "rejected_at") val rejectedAt: String
)
