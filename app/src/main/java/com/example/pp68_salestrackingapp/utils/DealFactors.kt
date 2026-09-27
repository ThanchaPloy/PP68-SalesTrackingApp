package com.example.pp68_salestrackingapp.utils

import com.example.pp68_salestrackingapp.data.model.DealFactorOption
import com.example.pp68_salestrackingapp.data.model.DealFactorQuestion

// W5b: คำถามปัจจัยข้อ 4-7 ในหน้าบันทึกผลการขาย — ค่าด้านล่างนี้คือ fallback เท่านั้น ของจริงมาจาก
// /deal_factor_question (SyncManager เรียก applyServerData() ให้ตอน login) question_key/code
// ต้องคงเดิมทุกตัวอักษรเสมอ เพราะ SalesResultViewModel อ้างชื่อเหล่านี้ตรงๆ
object DealFactors {
    const val DEAL_POSITION = "deal_position"
    const val PREVIOUS_SOLUTION = "previous_solution"
    const val COUNTERPARTY_TYPE = "counterparty_type"
    const val RESPONSE_SPEED = "response_speed"

    private val DEFAULT_QUESTIONS: Map<String, DealFactorQuestion> = listOf(
        DealFactorQuestion(
            questionKey = DEAL_POSITION, sequence = 4, defaultCode = "undetermined",
            options = listOf(
                DealFactorOption("incumbent", "ลูกค้าใช้เราอยู่แล้ว การต่อสัญญามีโอกาสสูงมาก", 1),
                DealFactorOption("vendor_of_choice", "ลูกค้าเลือกเราเป็นตัวหลัก คู่แข่งอื่นเป็นแค่ backup", 2),
                DealFactorOption("invited_to_compare", "ถูกเชิญมาเพื่อ benchmark ราคา โอกาสต่ำ", 3),
                DealFactorOption("undetermined", "ยังระบุไม่ได้", 4)
            )
        ),
        DealFactorQuestion(
            questionKey = PREVIOUS_SOLUTION, sequence = 5, defaultCode = "undetermined",
            options = listOf(
                DealFactorOption("no_solution", "ไม่มี Solution เดิม", 1),
                DealFactorOption("non_competitor_system", "มีระบบเดิมที่ไม่ใช่คู่แข่ง", 2),
                DealFactorOption("competitor_no_issue", "ใช้คู่แข่งอยู่และไม่มีปัญหา", 3),
                DealFactorOption("undetermined", "ยังระบุไม่ได้", 4)
            )
        ),
        DealFactorQuestion(
            questionKey = COUNTERPARTY_TYPE, sequence = 6, defaultCode = "undetermined",
            options = listOf(
                DealFactorOption("direct_main_contractor", "ดีลกับ Main Contractor โดยตรง", 1),
                DealFactorOption("via_installer_main_awarded", "ดีลผ่าน Installer — Main Contractor ได้งานแล้ว", 2),
                DealFactorOption("via_installer_main_pending", "ดีลผ่าน Installer — Main Contractor ยังไม่ได้งาน", 3),
                DealFactorOption("undetermined", "ยังระบุไม่ได้", 4)
            )
        ),
        DealFactorQuestion(
            questionKey = RESPONSE_SPEED, sequence = 7, defaultCode = "normal",
            options = listOf(
                DealFactorOption("fast", "เร็ว", 1),
                DealFactorOption("normal", "ปกติ", 2),
                DealFactorOption("slow_silent", "ช้าหรือเงียบ", 3)
            )
        )
    ).associateBy { it.questionKey }

    @Volatile
    private var serverQuestions: Map<String, DealFactorQuestion>? = null

    // ต้อง fallback เป็นรายคำถาม ไม่ใช่ทั้งก้อน — ถ้า server ตอบมาไม่ครบ 4 คำถาม (seed พลาด/บั๊ก)
    // คำถามที่ขาดไปต้องยังใช้ fallback ของตัวเองได้ ไม่ใช่กลายเป็นไม่มีตัวเลือกเลย
    private fun question(questionKey: String): DealFactorQuestion? =
        serverQuestions?.get(questionKey) ?: DEFAULT_QUESTIONS[questionKey]

    // ป้าย -> รหัส ตามลำดับตัวเลือกของคำถามนั้น (Map ที่คืนเป็น LinkedHashMap รักษาลำดับ insertion)
    fun labelToCode(questionKey: String): Map<String, String> =
        question(questionKey)?.options?.sortedBy { it.sequence }
            ?.associate { it.label to it.code } ?: emptyMap()

    fun codeToLabel(questionKey: String): Map<String, String> =
        question(questionKey)?.options?.associate { it.code to it.label } ?: emptyMap()

    // เรียกจาก SyncManager หลัง login สำเร็จ — ว่างเปล่า (offline/error) แปลว่ายังใช้ fallback ต่อ
    fun applyServerData(questions: List<DealFactorQuestion>) {
        if (questions.isNotEmpty()) serverQuestions = questions.associateBy { it.questionKey }
    }

    // เรียกตอน logout — กันค่า master data ของบัญชีเก่าค้างข้ามไปยังบัญชีถัดไปที่ล็อกอินบนเครื่องเดียวกัน
    fun clearServerData() {
        serverQuestions = null
    }
}
