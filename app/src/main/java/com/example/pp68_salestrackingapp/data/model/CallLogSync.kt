package com.example.pp68_salestrackingapp.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

/**
 * Mobile phase 2 ข้อ 2 — เขียนใหม่ทั้งตัว (ของเก่าถอดไปที่ commit 7e66070 ไม่มี Room table เลย
 * อ่านจากเครื่องแล้วยิง API ทันทีในลูปเดียว ไม่มี retry ถ้าพังก็หายไปเงียบๆ)
 *
 * เก็บเฉพาะสายที่ตรงกับเบอร์ผู้ติดต่อในระบบเท่านั้น (กรองตอนอ่านจาก ContentResolver ใน
 * CallLogRepository ก่อน insert ลงตารางนี้ด้วยซ้ำ — ไม่ใช่กรองตอน sync) ลดความเสี่ยงเรื่อง
 * ข้อมูลส่วนตัว (PDPA) ตั้งแต่ต้น ไม่ใช่แค่ตอนส่งขึ้น server
 *
 * isSynced ตามแพทเทิร์นเดียวกับทุก entity อื่นในแอป ไม่ใช้ watermark timestamp แบบของเก่า
 * (TokenManager.saveLastCallLogSyncTime/getLastCallLogSyncTime — ลบไปแล้ว ไม่มีใครเรียกอีก)
 * เพราะพึ่งนาฬิกาเครื่อง ถ้าเครื่องตั้งเวลาผิดจะข้ามแถวจริงไปเงียบๆ
 */
@Entity(
    tableName = "call_log_sync",
    indices = [Index(value = ["is_synced"], name = "index_call_log_sync_is_synced")]
)
data class CallLogSync(
    @PrimaryKey
    @ColumnInfo(name = "log_id")
    @SerializedName("log_id")
    val logId: String,

    @ColumnInfo(name = "cust_id")
    @SerializedName("cust_code")
    val custId: String? = null,

    @ColumnInfo(name = "phone_number")
    @SerializedName("phone_number")
    val phoneNumber: String,

    @ColumnInfo(name = "start_time")
    @SerializedName("start_time")
    val startTime: String,

    @ColumnInfo(name = "end_time")
    @SerializedName("end_time")
    val endTime: String? = null,

    @ColumnInfo(name = "duration")
    @SerializedName("duration")
    val duration: Int = 0,

    // ไม่มี @SerializedName — local-only เหมือน isSynced ของ entity อื่น (ดู ExclusionStrategy
    // ใน NetworkModule) ห้ามหลุดขึ้น server
    @ColumnInfo(name = "is_synced")
    val isSynced: Boolean = false
)
