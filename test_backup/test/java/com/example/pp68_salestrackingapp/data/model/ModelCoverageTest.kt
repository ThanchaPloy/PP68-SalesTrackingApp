package com.example.pp68_salestrackingapp.data.model

import org.junit.Assert.*
import org.junit.Test

class ModelCoverageTest {

    @Test
    fun `test RegisterResponse data class`() {
        val original = RegisterResponse(userId = "USR-001", message = "Success")
        
        // Test Properties
        assertEquals("USR-001", original.userId)
        assertEquals("Success", original.message)

        // Test copy
        val copied = original.copy(message = "Updated")
        assertEquals("USR-001", copied.userId)
        assertEquals("Updated", copied.message)

        // Test equals & hashCode
        val same = RegisterResponse("USR-001", "Success")
        assertEquals(original, same)
        assertEquals(original.hashCode(), same.hashCode())
        assertNotEquals(original, copied)
        
        // Test toString
        assertTrue(original.toString().contains("userId=USR-001"))
    }

    @Test
    fun `test UserInfo data class`() {
        val original = UserInfo(
            userId = "U-01",
            fullName = "John Doe",
            role = "Admin",
            email = "john@test.com"
        )
        
        assertEquals("U-01", original.userId)
        assertEquals("John Doe", original.fullName)
        assertEquals("Admin", original.role)
        assertEquals("john@test.com", original.email)

        val copied = original.copy(role = "Sales")
        assertEquals("Sales", copied.role)
        assertEquals("U-01", copied.userId)

        val same = UserInfo("U-01", "John Doe", "Admin", "john@test.com")
        assertEquals(original, same)
        assertEquals(original.hashCode(), same.hashCode())
        
        assertTrue(original.toString().contains("fullName=John Doe"))
    }

    @Test
    fun `test RegisterRequest data class`() {
        val original = RegisterRequest(
            email = "new@test.com",
            passwordHash = "hash123",
            fullName = "New User",
            branchId = "B-001",
            role = "Sales"
        )

        assertEquals("new@test.com", original.email)
        assertEquals("hash123", original.passwordHash)
        assertEquals("New User", original.fullName)
        assertEquals("B-001", original.branchId)
        assertEquals("Sales", original.role)

        val copied = original.copy(email = "updated@test.com")
        assertEquals("updated@test.com", copied.email)
        assertEquals("hash123", copied.passwordHash)

        val same = RegisterRequest("new@test.com", "hash123", "New User", "B-001", "Sales")
        assertEquals(original, same)
        assertEquals(original.hashCode(), same.hashCode())
    }

    @Test
    fun `test ContactWithCompany data class`() {
        val original = ContactWithCompany(
            contactId = "CON-1",
            companyId = "COM-1",
            fullName = "Vipavee",
            nickname = "Vi",
            position = "Manager",
            phoneNum = "081",
            email = "vi@test.com",
            line = "vi_line",
            companyName = "Test Co",
            branch = "Main",
            companyType = "Developer"
        )

        assertEquals("CON-1", original.contactId)
        assertEquals("Test Co", original.companyName)
        assertEquals("Vi", original.nickname)

        val copied = original.copy(nickname = "Vee")
        assertEquals("Vee", copied.nickname)
        assertEquals("CON-1", copied.contactId)

        val same = ContactWithCompany(
            "CON-1", "COM-1", "Vipavee", "Vi", "Manager",
            "081", "vi@test.com", "vi_line", "Test Co", "Main", "Developer"
        )
        assertEquals(original, same)
        assertEquals(original.hashCode(), same.hashCode())
        
        assertTrue(original.toString().contains("companyName=Test Co"))
    }
}
