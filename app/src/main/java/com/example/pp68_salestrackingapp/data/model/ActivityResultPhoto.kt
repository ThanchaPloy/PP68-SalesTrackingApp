package com.example.pp68_salestrackingapp.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.google.gson.annotations.SerializedName

// ✅ รูปภาพยืนยันการเข้าพบ รองรับหลายรูปต่อบันทึกผลการขาย (สูงสุด 5 รูป)
// รูปแรก (photo_order = 0) เป็นรูปเดียวกับที่ผูก metadata (เวลา/พิกัด/รุ่นเครื่อง) ไว้ใน activity_result
@Entity(
    tableName = "activity_result_photo",
    primaryKeys = ["result_id", "photo_order"],
    foreignKeys = [
        ForeignKey(
            entity = ActivityResult::class,
            parentColumns = ["result_id"],
            childColumns = ["result_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("result_id")]
)
data class ActivityResultPhoto(
    @ColumnInfo(name = "result_id")
    @SerializedName("result_id")
    val resultId: String,

    @ColumnInfo(name = "photo_order")
    @SerializedName("photo_order")
    val photoOrder: Int,

    @ColumnInfo(name = "photo_url")
    @SerializedName("photo_url")
    val photoUrl: String
)
