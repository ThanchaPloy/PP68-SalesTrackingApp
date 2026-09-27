package com.example.pp68_salestrackingapp.utils

object ProjectProgressUtils {

    // W5a: % มาจาก ProjectStages (master data + fallback) แทนตารางในนี้เอง จุดเดียวพอ
    fun getProgress(status: String?): Float =
        if (status == null) 0f else ProjectStages.probabilityPct(status) / 100f

    fun getProgressPercent(status: String?): Int =
        (getProgress(status) * 100).toInt()

    fun getProgressColor(status: String?): androidx.compose.ui.graphics.Color =
        when (status) {
            "PO"               -> androidx.compose.ui.graphics.Color(0xFF2E7D32) // เขียว
            "Assured"          -> androidx.compose.ui.graphics.Color(0xFF388E3C)
            "Make a Decision"  -> androidx.compose.ui.graphics.Color(0xFF1976D2) // น้ำเงิน
            "Bidding"          -> androidx.compose.ui.graphics.Color(0xFFF57C00) // ส้ม
            "Quotation"        -> androidx.compose.ui.graphics.Color(0xFFCC1D1D) // แดง
            "New Project"      -> androidx.compose.ui.graphics.Color(0xFFCC1D1D)
            "Lead"             -> androidx.compose.ui.graphics.Color(0xFFCC1D1D)
            "Lost", "Failed"   -> androidx.compose.ui.graphics.Color(0xFF9E9E9E) // เทา
            else               -> androidx.compose.ui.graphics.Color(0xFF9E9E9E)
        }
}