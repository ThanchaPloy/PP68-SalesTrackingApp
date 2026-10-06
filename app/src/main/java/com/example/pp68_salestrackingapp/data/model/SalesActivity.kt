package com.example.pp68_salestrackingapp.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(
    tableName = "activity_table",
    indices = [
        Index(
            value = ["user_id", "planned_date", "planned_time", "appointment_id"],
            name = "index_activity_user_date_id"
        ),
        Index(value = ["is_synced"], name = "index_activity_is_synced")
    ]
)
data class SalesActivity(
    @PrimaryKey
    @ColumnInfo(name = "appointment_id")
    @SerializedName("appointment_id")
    val activityId: String,

    @ColumnInfo(name = "user_id")
    @SerializedName("emp_code")
    val userId: String,

    @ColumnInfo(name = "cust_id")
    @SerializedName("cust_code")
    val customerId: String? = null,

    @ColumnInfo(name = "project_id")
    @SerializedName("project_code")
    val projectId: String? = null,

    @ColumnInfo(name = "type")
    @SerializedName("type")
    val activityType: String,

    @ColumnInfo(name = "is_appointment")
    @SerializedName("is_appointment")
    val isAppointment: Boolean = false,

    @ColumnInfo(name = "topic")
    @SerializedName("topic")
    val detail: String? = null,

    @ColumnInfo(name = "planned_date")
    @SerializedName("planned_date")
    val activityDate: String,

    @ColumnInfo(name = "planned_time")
    @SerializedName("planned_time")
    val plannedTime: String? = null,

    @ColumnInfo(name = "planned_end_time")
    @SerializedName("planned_end_time")
    val plannedEndTime: String? = null,

    @ColumnInfo(name = "planned_lat")
    @SerializedName("planned_lat")
    val plannedLat: Double? = null,

    @ColumnInfo(name = "planned_long")
    @SerializedName("planned_long")
    val plannedLong: Double? = null,

    @ColumnInfo(name = "check_in_time")
    @SerializedName("check_in_time")
    val checkInTime: String? = null,

    @ColumnInfo(name = "check_in_lat")
    @SerializedName("check_in_lat")
    val checkInLat: Double? = null,

    @ColumnInfo(name = "check_in_long")
    @SerializedName("check_in_long")
    val checkInLong: Double? = null,

    @ColumnInfo(name = "distance_deviation")
    @SerializedName("distance_deviation")
    val distanceDeviation: Double? = null,

    @ColumnInfo(name = "is_location_verified")
    @SerializedName("is_location_verified")
    val isLocationVerified: Boolean = false,

    @ColumnInfo(name = "plan_status")
    @SerializedName("plan_status")
    val status: String,

    @ColumnInfo(name = "note")
    @SerializedName("note")
    val note: String? = null,

    @ColumnInfo(name = "created_at")
    @SerializedName("created_at")
    val createdAt: String? = null,

    // Local-only fields
    @ColumnInfo(name = "project_name")
    val projectName: String? = null,
    /**
     * ชื่อบริษัทของนัดหมาย — อ่านจาก server ได้ แต่ไม่เคยถูกส่งกลับขึ้นไป
     *
     * ตั้งแต่ลูกค้า ERP เป็น remote-only ตาราง customer ในเครื่องมีแต่ lead ที่แอปสร้างเอง
     * การ join หาชื่อจึงได้ค่าว่างสำหรับลูกค้า ERP และหน้ารายละเอียดนัดโชว์ช่องบริษัทเปล่า
     * server จึงแนบ customer_name มากับนัดหมายให้เลย
     *
     * ใส่ @SerializedName ได้โดยไม่ทำให้ค่านี้หลุดขึ้น server เพราะทุกทางที่เขียนนัดหมาย
     * (CreateRequestPayloads.activity และ patchBody ใน SyncManager) ประกอบ Map เองทั้งหมด
     * ไม่ได้ปล่อย entity ให้ Gson แปลง
     */
    @ColumnInfo(name = "company_name")
    @SerializedName("customer_name")
    val companyName: String? = null,
    @ColumnInfo(name = "contact_name")
    val contactName: String? = null,
    @ColumnInfo(name = "weekly_note")
    val weeklyNote: String? = null,
    // ชื่อสถานที่จากพิกัด (reverse geocode ผ่าน Geoapify) — resolve ครั้งเดียวแล้วเก็บไว้
    // ไม่ต้องยิงซ้ำทุกครั้งที่ export รายงาน (Geoapify free plan จำกัด 3,000 credits/วัน)
    @ColumnInfo(name = "location_name")
    val locationName: String? = null,

    @ColumnInfo(name = "is_synced")
    val isSynced: Boolean = true,

    @ColumnInfo(name = "operation_id")
    @SerializedName("operation_id")
    val operationId: String? = null,

    /**
     * เวลาที่ผู้ใช้กดแก้แผนครั้งล่าสุด ตามเวลาที่อ้างอิงจากเซิร์ฟเวอร์ (แผนงาน B.4 ข้อ 2)
     *
     * ต้องเก็บลงแถว ไม่ใช่ถือไว้ใน memory เพราะการแก้ตอนออฟไลน์อาจถูกส่งขึ้นอีกหลายชั่วโมง
     * ให้หลัง และ outbox ประกอบ payload ใหม่จากแถวนี้ ไม่ได้เก็บ map เดิมที่ผู้ใช้กดไว้
     *
     * ส่งขึ้น server ด้วย จึงมี @SerializedName — ดู ExclusionStrategy ใน NetworkModule
     */
    @ColumnInfo(name = "plan_edit_at")
    @SerializedName("client_modified_at")
    val planEditAt: String? = null,

    /** เวลาด้านบนมาจาก anchor ที่เชื่อถือได้หรือจากนาฬิกาเครื่องล้วน ๆ */
    @ColumnInfo(name = "plan_edit_time_trusted")
    @SerializedName("client_time_trusted")
    val planEditTimeTrusted: Boolean = false
)
