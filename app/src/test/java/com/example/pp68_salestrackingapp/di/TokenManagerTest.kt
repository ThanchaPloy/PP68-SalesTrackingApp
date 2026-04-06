package com.example.pp68_salestrackingapp.di

import android.content.Context
import android.content.SharedPreferences
import com.example.pp68_salestrackingapp.data.model.AuthUser
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TokenManagerTest {

    private lateinit var tokenManager: TokenManager
    private val mockContext: Context = mockk()
    private val mockPrefs: SharedPreferences = mockk()
    private val mockEditor: SharedPreferences.Editor = mockk(relaxed = true)

    @Before
    fun setUp() {
        every { mockContext.getSharedPreferences("sales_prefs", Context.MODE_PRIVATE) } returns mockPrefs
        every { mockPrefs.edit() } returns mockEditor
        every { mockEditor.putString(any(), any()) } returns mockEditor
        every { mockEditor.putBoolean(any(), any()) } returns mockEditor
        every { mockEditor.clear() } returns mockEditor
        tokenManager = TokenManager(mockContext)
    }

    // TC-UNIT-TM-01
    @Test
    fun `saveToken should persist JWT token`() {
        tokenManager.saveToken("jwt_abc123")

        verify { mockEditor.putString("jwt_token", "jwt_abc123") }
        verify { mockEditor.apply() }
    }

    // TC-UNIT-TM-02
    @Test
    fun `getToken should return stored JWT token`() {
        every { mockPrefs.getString("jwt_token", null) } returns "jwt_abc123"

        val token = tokenManager.getToken()

        assertEquals("jwt_abc123", token)
    }

    // TC-UNIT-TM-03
    @Test
    fun `getToken should return null when no token is saved`() {
        every { mockPrefs.getString("jwt_token", null) } returns null

        val token = tokenManager.getToken()

        assertNull(token)
    }

    // TC-UNIT-TM-04
    @Test
    fun `clearToken should call prefs clear and apply`() {
        tokenManager.clearToken()

        verify { mockEditor.clear() }
        verify { mockEditor.apply() }
    }

    // TC-UNIT-TM-05
    @Test
    fun `saveUserData should persist all user fields`() {
        val user = AuthUser(
            userId     = "USR-001",
            email      = "test@example.com",
            role       = "sale",
            teamId     = "TEAM-01",
            fullName   = "John Doe",
            branchName = "Main Branch"
        )

        tokenManager.saveUserData(user)

        verify { mockEditor.putString("user_id",     "USR-001") }
        verify { mockEditor.putString("user_email",  "test@example.com") }
        verify { mockEditor.putString("user_role",   "sale") }
        verify { mockEditor.putString("user_team",   "TEAM-01") }
        verify { mockEditor.putString("user_name",   "John Doe") }
        verify { mockEditor.putString("user_branch", "Main Branch") }
        verify { mockEditor.apply() }
    }

    // TC-UNIT-TM-06
    @Test
    fun `getUserData should reconstruct AuthUser from prefs`() {
        every { mockPrefs.getString("user_id",     null)   } returns "USR-001"
        every { mockPrefs.getString("user_email",  "")     } returns "test@example.com"
        every { mockPrefs.getString("user_role",   "sale") } returns "sale"
        every { mockPrefs.getString("user_team",   null)   } returns "TEAM-01"
        every { mockPrefs.getString("user_name",   null)   } returns "John Doe"
        every { mockPrefs.getString("user_branch", null)   } returns "Main Branch"

        val user = tokenManager.getUserData()

        assertNotNull(user)
        assertEquals("USR-001",         user?.userId)
        assertEquals("test@example.com",user?.email)
        assertEquals("sale",            user?.role)
        assertEquals("TEAM-01",         user?.teamId)
        assertEquals("John Doe",        user?.fullName)
        assertEquals("Main Branch",     user?.branchName)
    }

    // TC-UNIT-TM-07
    @Test
    fun `getUserData should return null when no userId is stored`() {
        every { mockPrefs.getString("user_id", null) } returns null

        val user = tokenManager.getUserData()

        assertNull(user)
    }

    // TC-UNIT-TM-08
    @Test
    fun `saveFcmToken should persist FCM token`() {
        tokenManager.saveFcmToken("fcm_xyz789")

        verify { mockEditor.putString("fcm_token", "fcm_xyz789") }
        verify { mockEditor.apply() }
    }

    // TC-UNIT-TM-09
    @Test
    fun `getFcmToken should return stored FCM token`() {
        every { mockPrefs.getString("fcm_token", null) } returns "fcm_xyz789"

        val token = tokenManager.getFcmToken()

        assertEquals("fcm_xyz789", token)
    }

    // TC-UNIT-TM-10
    @Test
    fun `getFcmToken should return null when not set`() {
        every { mockPrefs.getString("fcm_token", null) } returns null

        val token = tokenManager.getFcmToken()

        assertNull(token)
    }

    // TC-UNIT-TM-11
    @Test
    fun `savePushEnabled should persist push notification setting`() {
        tokenManager.savePushEnabled(false)

        verify { mockEditor.putBoolean("push_enabled", false) }
        verify { mockEditor.apply() }
    }

    // TC-UNIT-TM-12
    @Test
    fun `isPushEnabled should return true by default`() {
        every { mockPrefs.getBoolean("push_enabled", true) } returns true

        assertTrue(tokenManager.isPushEnabled())
    }

    // TC-UNIT-TM-13
    @Test
    fun `isPushEnabled should return false when disabled`() {
        every { mockPrefs.getBoolean("push_enabled", true) } returns false

        assertFalse(tokenManager.isPushEnabled())
    }

    // TC-UNIT-TM-14
    @Test
    fun `saveVisitReminderEnabled should persist reminder setting`() {
        tokenManager.saveVisitReminderEnabled(false)

        verify { mockEditor.putBoolean("visit_reminder_enabled", false) }
        verify { mockEditor.apply() }
    }

    // TC-UNIT-TM-15
    @Test
    fun `isVisitReminderEnabled should return true by default`() {
        every { mockPrefs.getBoolean("visit_reminder_enabled", true) } returns true

        assertTrue(tokenManager.isVisitReminderEnabled())
    }
}
