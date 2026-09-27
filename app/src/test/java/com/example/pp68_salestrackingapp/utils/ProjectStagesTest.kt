package com.example.pp68_salestrackingapp.utils

import com.example.pp68_salestrackingapp.data.model.LossReasonMaster
import com.example.pp68_salestrackingapp.data.model.ProjectStageMaster
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectStagesTest {

    // W5a: applyServerData mutates process-wide object state — reset after every test so
    // one test's server override can't leak into the next test's assertions.
    @After
    fun resetServerData() {
        ProjectStages.clearServerData()
        LossReasons.clearServerData()
    }

    @Test
    fun `applyServerData overrides the selectable list and probability`() {
        ProjectStages.applyServerData(listOf(
            ProjectStageMaster(code = "OnlyStage", label = "Only", sequence = 1, probabilityPct = 55, isClosed = true, isWon = true)
        ))

        assertEquals(listOf("OnlyStage"), ProjectStages.SELECTABLE)
        assertEquals(55, ProjectStages.probabilityPct("OnlyStage"))
        assertTrue("OnlyStage" in ProjectStages.CLOSED)
        assertTrue("OnlyStage" !in ProjectStages.LOST)
    }

    @Test
    fun `applyServerData with empty list keeps the fallback`() {
        ProjectStages.applyServerData(emptyList())
        assertEquals(9, ProjectStages.SELECTABLE.size)
        assertTrue("Lead" in ProjectStages.SELECTABLE)
    }

    @Test
    fun `clearServerData reverts to the fallback list`() {
        ProjectStages.applyServerData(listOf(ProjectStageMaster(code = "X", sequence = 1, probabilityPct = 1)))
        ProjectStages.clearServerData()
        assertEquals(9, ProjectStages.SELECTABLE.size)
    }

    @Test
    fun `LossReasons applyServerData overrides OPTIONS`() {
        LossReasons.applyServerData(listOf(LossReasonMaster(code = "budget_cut", label = "Budget cut", sequence = 1)))
        assertEquals(listOf("budget_cut"), LossReasons.OPTIONS)
    }

    @Test
    fun `every selectable stage has a non-null progress percent`() {
        ProjectStages.SELECTABLE.forEach { stage ->
            assertTrue(
                "no progress % defined for stage '$stage'",
                ProjectProgressUtils.getProgress(stage) > 0f || stage in ProjectStages.LOST
            )
        }
    }

    @Test
    fun `Lost and Failed both give 0 percent`() {
        ProjectStages.LOST.forEach { stage ->
            assertEquals(0, ProjectProgressUtils.getProgressPercent(stage))
        }
    }

    @Test
    fun `PO is the only closed-won stage and gives 100 percent`() {
        assertEquals(100, ProjectProgressUtils.getProgressPercent("PO"))
        assertTrue("PO" in ProjectStages.CLOSED)
        assertTrue("PO" !in ProjectStages.LOST)
    }

    @Test
    fun `CLOSED is exactly LOST plus PO`() {
        assertEquals(ProjectStages.LOST + "PO", ProjectStages.CLOSED)
    }
}
