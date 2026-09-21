package com.example.pp68_salestrackingapp.data.model

import androidx.room.Entity

@Entity(tableName = "activity_plan_item")
data class ActivityPlanItem(
    @androidx.room.PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val appointmentId: String,
    val masterId: Int,
    val actName: String?,
    val isDone: Boolean = false,
    // ตารางนี้เคยไม่มีธงซิงค์ ทำให้ outbox มองไม่เห็น การติ๊ก checklist ตอนเน็ตไม่ดี
    // จึงหายถาวรโดยไม่มีการลองใหม่ ค่าเริ่มต้นเป็น true เพราะแถวที่ดึงมาจาก server ตรงกันอยู่แล้ว
    @androidx.room.ColumnInfo(name = "is_synced")
    val isSynced: Boolean = true
)