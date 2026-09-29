package com.example.pp68_salestrackingapp.data.model

import com.google.gson.annotations.SerializedName

// ประวัติการแก้ไขปัจจัยข้อ 4-9 ของโครงการ — อ่านจาก API อย่างเดียว ไม่เก็บลง Room
// (เป็นข้อมูลอ่านย้อนหลังเฉยๆ ไม่ต้องใช้ตอนออฟไลน์ และไม่มีการแก้ไขจากฝั่งแอป)
// แถวถูกเขียนโดย DB trigger trg_log_project_factor_changes ฝั่ง backend
data class ProjectFactorLog(
    @SerializedName("log_id")       val logId: Int,
    @SerializedName("project_code") val projectCode: String,
    // ชื่อคอลัมน์จริงฝั่ง DB เช่น deal_position / is_proposal_sent — แปลงเป็นชื่อไทยที่ชั้น UI
    @SerializedName("field_key")    val fieldKey: String,
    @SerializedName("old_value")    val oldValue: String? = null,
    @SerializedName("new_value")    val newValue: String? = null,
    @SerializedName("changed_by")   val changedBy: String? = null,
    @SerializedName("changed_at")   val changedAt: String
)
