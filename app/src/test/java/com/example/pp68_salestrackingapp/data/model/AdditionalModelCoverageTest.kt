package com.example.pp68_salestrackingapp.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdditionalModelCoverageTest {

    @Test
    fun `Branch should preserve nullable defaults and support copy`() {
        val original = Branch(
            branchId = "BR-01",
            branchName = "Bangkok HQ"
        )

        assertEquals("BR-01", original.branchId)
        assertEquals("Bangkok HQ", original.branchName)
        assertNull(original.region)

        val copied = original.copy(region = "Central")
        assertEquals("Central", copied.region)
        assertEquals("BR-01", copied.branchId)
        assertFalse(original == copied)
    }

    @Test
    fun `Customer should support equals hashCode and optional values`() {
        val c1 = Customer(
            custId = "C-001",
            companyName = "Acme Co",
            branch = null,
            custType = "Developer",
            companyAddr = null,
            companyLat = null,
            companyLong = null,
            companyStatus = "new lead",
            firstCustomerDate = null
        )
        val c2 = c1.copy()

        assertEquals(c1, c2)
        assertEquals(c1.hashCode(), c2.hashCode())
        assertTrue(c1.toString().contains("Acme Co"))
    }

    @Test
    fun `ActivityResult should keep default primitive values`() {
        val result = ActivityResult(activityId = "APT-001")

        assertEquals("APT-001", result.activityId)
        assertFalse(result.dmInvolved)
        assertFalse(result.isProposalSent)
        assertEquals(0, result.competitorCount)
        assertNull(result.summary)
    }

    @Test
    fun `ProjectContact should support composite-key style equality`() {
        val a = ProjectContact(project_id = "PJ-1", contact_id = "CT-1")
        val b = ProjectContact(project_id = "PJ-1", contact_id = "CT-1")
        val c = ProjectContact(project_id = "PJ-1", contact_id = "CT-2")

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertFalse(a == c)
    }
}
