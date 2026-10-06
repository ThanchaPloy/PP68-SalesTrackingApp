package com.example.pp68_salestrackingapp.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * ฉบับร่างนัดหมายที่ผู้ใช้เก็บไว้ทำต่อ — เก็บได้หลายรายการต่อบัญชี (แผนงาน C.1)
 *
 * เป็นข้อมูลในเครื่องล้วน ไม่เข้า snapshot/change feed ไม่มี `is_synced` และไม่มี `@SerializedName`
 * สักฟิลด์ จึงไม่มีทางหลุดขึ้น server ผ่าน Gson (ดู ExclusionStrategy ใน NetworkModule)
 *
 * [ownerKey] คือ account key แบบเดียวกับ sync state ทุก query ต้องกรองด้วยคีย์นี้เสมอ
 * ไม่ใช่เพื่อความสวยงาม แต่เพราะเครื่องเดียวอาจมีหลายคนใช้ และฉบับร่างของคนก่อนต้องไม่โผล่
 * ให้คนถัดไปเห็น
 *
 * [payloadJson] เก็บฟอร์มทั้งก้อน ส่วนฟิลด์ที่แยกออกมาข้างนอกมีไว้ให้หน้ารายการแสดงผลและ
 * เรียงลำดับได้โดยไม่ต้อง parse JSON ทุกแถว
 *
 * [projectNameSnapshot] / [customerNameSnapshot] คือชื่อ ณ ตอนที่บันทึกร่าง เก็บไว้เพราะโครงการ
 * หรือบริษัทอาจถูกลบไปก่อนที่ผู้ใช้จะกลับมาทำต่อ ถ้าไม่เก็บจะเหลือแต่รหัสที่ไม่มีความหมาย
 */
@Entity(
    tableName = "appointment_draft",
    indices = [
        Index(value = ["owner_key", "updated_at"], name = "index_appointment_draft_owner_updated"),
        Index(value = ["owner_key", "expires_at"], name = "index_appointment_draft_owner_expires")
    ]
)
data class AppointmentDraft(
    @PrimaryKey
    @ColumnInfo(name = "draft_id")
    val draftId: String,

    @ColumnInfo(name = "owner_key")
    val ownerKey: String,

    /** รูปแบบของ payload — ขึ้นเลขเมื่อฟิลด์ในฟอร์มเปลี่ยนจนของเก่าอ่านไม่ได้ */
    @ColumnInfo(name = "schema_version")
    val schemaVersion: Int,

    @ColumnInfo(name = "title")
    val title: String? = null,

    @ColumnInfo(name = "planned_date")
    val plannedDate: String? = null,

    @ColumnInfo(name = "planned_time")
    val plannedTime: String? = null,

    @ColumnInfo(name = "project_id")
    val projectId: String? = null,

    @ColumnInfo(name = "project_name_snapshot")
    val projectNameSnapshot: String? = null,

    @ColumnInfo(name = "customer_id")
    val customerId: String? = null,

    @ColumnInfo(name = "customer_name_snapshot")
    val customerNameSnapshot: String? = null,

    @ColumnInfo(name = "payload_json")
    val payloadJson: String,

    @ColumnInfo(name = "created_at")
    val createdAt: String,

    @ColumnInfo(name = "updated_at")
    val updatedAt: String,

    @ColumnInfo(name = "expires_at")
    val expiresAt: String
)
