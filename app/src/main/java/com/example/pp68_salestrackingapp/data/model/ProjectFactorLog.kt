package com.example.pp68_salestrackingapp.data.model

import com.google.gson.annotations.SerializedName

// ค่าปัจจัยทั้ง 7 ฟิลด์ ณ เวลาหนึ่ง — ตรงกับ JSONB คอลัมน์ factors ฝั่ง DB
// ชนิดข้อมูลตรงตามจริง (Boolean/Int) ไม่ใช่สตริงทั้งหมด เพราะ JSONB คงชนิดไว้ได้
data class ProjectFactorSnapshot(
    @SerializedName("deal_position")     val dealPosition: String? = null,
    @SerializedName("current_solution")  val previousSolution: String? = null,
    @SerializedName("counterparty_type") val counterpartyType: String? = null,
    @SerializedName("response_speed")    val responseSpeed: String? = null,
    @SerializedName("is_proposal_sent")  val isProposalSent: Boolean? = null,
    @SerializedName("proposal_date")     val proposalDate: String? = null,
    @SerializedName("competitor_count")  val competitorCount: Int? = null
)

// ประวัติปัจจัยของโครงการ — อ่านจาก API อย่างเดียว ไม่เก็บลง Room
// (อ่านย้อนหลังเฉยๆ ไม่ต้องใช้ตอนออฟไลน์ และไม่มีการแก้ไขจากฝั่งแอป)
//
// 1 แถว = snapshot ของค่าทั้งชุด ณ ตอนที่มีการเปลี่ยน ไม่ใช่ 1 แถวต่อ 1 ฟิลด์ — การหาว่า
// "ข้อไหนเปลี่ยนจากอะไรเป็นอะไร" ทำโดย diff snapshot ของแถวนี้กับแถวที่เก่ากว่าถัดไป
// (ดู EditProjectFactorsViewModel.buildHistoryEntries) แถวถูกเขียนโดย DB trigger ฝั่ง backend
data class ProjectFactorLog(
    @SerializedName("log_id")       val logId: Int,
    @SerializedName("project_code") val projectCode: String,
    @SerializedName("factors")      val factors: ProjectFactorSnapshot = ProjectFactorSnapshot(),
    @SerializedName("changed_by")   val changedBy: String? = null,
    @SerializedName("changed_at")   val changedAt: String
)
