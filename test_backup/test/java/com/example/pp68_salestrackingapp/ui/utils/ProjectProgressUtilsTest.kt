package com.example.pp68_salestrackingapp.ui.utils

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class ProjectProgressUtilsTest {

    // ─────────────────────────────────────────
    // getProgress()
    // ─────────────────────────────────────────

    // TC-UNIT-UTIL-01
    @Test
    fun `getProgress returns 0_10 for Lead`() {
        assertEquals(0.10f, ProjectProgressUtils.getProgress("Lead"), 0.001f)
    }

    // TC-UNIT-UTIL-02
    @Test
    fun `getProgress returns 0_20 for New Project`() {
        assertEquals(0.20f, ProjectProgressUtils.getProgress("New Project"), 0.001f)
    }

    // TC-UNIT-UTIL-03
    @Test
    fun `getProgress returns 0_40 for Quotation`() {
        assertEquals(0.40f, ProjectProgressUtils.getProgress("Quotation"), 0.001f)
    }

    // TC-UNIT-UTIL-04
    @Test
    fun `getProgress returns 0_50 for Bidding`() {
        assertEquals(0.50f, ProjectProgressUtils.getProgress("Bidding"), 0.001f)
    }

    // TC-UNIT-UTIL-05
    @Test
    fun `getProgress returns 0_70 for Make a Decision`() {
        assertEquals(0.70f, ProjectProgressUtils.getProgress("Make a Decision"), 0.001f)
    }

    // TC-UNIT-UTIL-06
    @Test
    fun `getProgress returns 0_80 for Assured`() {
        assertEquals(0.80f, ProjectProgressUtils.getProgress("Assured"), 0.001f)
    }

    // TC-UNIT-UTIL-07
    @Test
    fun `getProgress returns 1_00 for PO`() {
        assertEquals(1.00f, ProjectProgressUtils.getProgress("PO"), 0.001f)
    }

    // TC-UNIT-UTIL-08
    @Test
    fun `getProgress returns 0_00 for Lost`() {
        assertEquals(0.00f, ProjectProgressUtils.getProgress("Lost"), 0.001f)
    }

    // TC-UNIT-UTIL-09
    @Test
    fun `getProgress returns 0_00 for Failed`() {
        assertEquals(0.00f, ProjectProgressUtils.getProgress("Failed"), 0.001f)
    }

    // TC-UNIT-UTIL-10
    @Test
    fun `getProgress returns 0_00 for unknown status`() {
        assertEquals(0.00f, ProjectProgressUtils.getProgress("Unknown"), 0.001f)
    }

    // TC-UNIT-UTIL-11
    @Test
    fun `getProgress returns 0_00 for null status`() {
        assertEquals(0.00f, ProjectProgressUtils.getProgress(null), 0.001f)
    }

    // ─────────────────────────────────────────
    // getProgressPercent()
    // ─────────────────────────────────────────

    // TC-UNIT-UTIL-12
    @Test
    fun `getProgressPercent returns integer percent for each status`() {
        val expected = mapOf(
            "Lead"            to 10,
            "New Project"     to 20,
            "Quotation"       to 40,
            "Bidding"         to 50,
            "Make a Decision" to 70,
            "Assured"         to 80,
            "PO"              to 100,
            "Lost"            to 0,
            "Failed"          to 0
        )
        expected.forEach { (status, pct) ->
            assertEquals(
                "Expected $pct for status '$status'",
                pct,
                ProjectProgressUtils.getProgressPercent(status)
            )
        }
    }

    // TC-UNIT-UTIL-13
    @Test
    fun `getProgressPercent returns 0 for null status`() {
        assertEquals(0, ProjectProgressUtils.getProgressPercent(null))
    }

    // ─────────────────────────────────────────
    // getProgressColor()
    // ─────────────────────────────────────────

    // TC-UNIT-UTIL-14
    @Test
    fun `getProgressColor returns green for PO`() {
        assertEquals(Color(0xFF2E7D32), ProjectProgressUtils.getProgressColor("PO"))
    }

    // TC-UNIT-UTIL-15
    @Test
    fun `getProgressColor returns grey for Lost`() {
        assertEquals(Color(0xFF9E9E9E), ProjectProgressUtils.getProgressColor("Lost"))
    }

    // TC-UNIT-UTIL-16
    @Test
    fun `getProgressColor returns grey for Failed`() {
        assertEquals(Color(0xFF9E9E9E), ProjectProgressUtils.getProgressColor("Failed"))
    }

    // TC-UNIT-UTIL-17
    @Test
    fun `getProgressColor returns grey for null status`() {
        assertEquals(Color(0xFF9E9E9E), ProjectProgressUtils.getProgressColor(null))
    }

    // TC-UNIT-UTIL-18
    @Test
    fun `getProgressColor returns different colors for active and failed statuses`() {
        val activeColor = ProjectProgressUtils.getProgressColor("PO")
        val failedColor = ProjectProgressUtils.getProgressColor("Lost")
        assertNotEquals(activeColor, failedColor)
    }
}
