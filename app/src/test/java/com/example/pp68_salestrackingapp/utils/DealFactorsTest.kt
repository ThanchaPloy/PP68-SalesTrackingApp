package com.example.pp68_salestrackingapp.utils

import com.example.pp68_salestrackingapp.data.model.DealFactorOption
import com.example.pp68_salestrackingapp.data.model.DealFactorQuestion
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DealFactorsTest {

    // W5b: applyServerData mutates process-wide object state — reset after every test so
    // one test's server override can't leak into another test class's assertions
    // (SalesResultViewModelTest reads these maps too).
    @After
    fun resetServerData() {
        DealFactors.clearServerData()
    }

    @Test
    fun `fallback has 4 questions with the exact real option counts`() {
        assertEquals(4, DealFactors.labelToCode(DealFactors.DEAL_POSITION).size)
        assertEquals(4, DealFactors.labelToCode(DealFactors.PREVIOUS_SOLUTION).size)
        assertEquals(4, DealFactors.labelToCode(DealFactors.COUNTERPARTY_TYPE).size)
        assertEquals(3, DealFactors.labelToCode(DealFactors.RESPONSE_SPEED).size)
    }

    @Test
    fun `applyServerData overrides a question's options`() {
        DealFactors.applyServerData(listOf(
            DealFactorQuestion(
                questionKey = DealFactors.DEAL_POSITION,
                defaultCode = "new_code",
                options = listOf(DealFactorOption("new_code", "ตัวเลือกใหม่", 1))
            )
        ))

        assertEquals(mapOf("ตัวเลือกใหม่" to "new_code"), DealFactors.labelToCode(DealFactors.DEAL_POSITION))
        assertEquals(mapOf("new_code" to "ตัวเลือกใหม่"), DealFactors.codeToLabel(DealFactors.DEAL_POSITION))
    }

    @Test
    fun `applyServerData falls back per-question when the server response is incomplete`() {
        DealFactors.applyServerData(listOf(
            DealFactorQuestion(
                questionKey = DealFactors.DEAL_POSITION,
                defaultCode = "new_code",
                options = listOf(DealFactorOption("new_code", "ตัวเลือกใหม่", 1))
            )
        ))

        // response_speed wasn't in this (incomplete) override payload — it must still use its
        // own fallback, not end up with zero options just because a sibling question overrode
        assertEquals(3, DealFactors.labelToCode(DealFactors.RESPONSE_SPEED).size)
        assertTrue("ปกติ" in DealFactors.labelToCode(DealFactors.RESPONSE_SPEED))
    }

    @Test
    fun `applyServerData with empty list keeps the fallback`() {
        DealFactors.applyServerData(emptyList())
        assertEquals(4, DealFactors.labelToCode(DealFactors.DEAL_POSITION).size)
    }

    @Test
    fun `clearServerData reverts to the fallback`() {
        DealFactors.applyServerData(listOf(
            DealFactorQuestion(questionKey = DealFactors.RESPONSE_SPEED, defaultCode = "x", options = emptyList())
        ))
        DealFactors.clearServerData()
        assertEquals(3, DealFactors.labelToCode(DealFactors.RESPONSE_SPEED).size)
    }
}
