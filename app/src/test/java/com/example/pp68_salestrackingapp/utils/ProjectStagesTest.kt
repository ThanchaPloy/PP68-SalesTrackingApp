package com.example.pp68_salestrackingapp.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectStagesTest {

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
