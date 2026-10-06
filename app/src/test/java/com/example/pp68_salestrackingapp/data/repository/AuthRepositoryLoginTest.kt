package com.example.pp68_salestrackingapp.data.repository

import com.example.pp68_salestrackingapp.data.local.AppDatabase
import com.example.pp68_salestrackingapp.data.model.AuthUser
import com.example.pp68_salestrackingapp.data.model.LoginResponse
import com.example.pp68_salestrackingapp.data.model.InitialSetupInfo
import com.example.pp68_salestrackingapp.data.model.SyncRejection
import com.example.pp68_salestrackingapp.data.remote.ApiService
import com.example.pp68_salestrackingapp.data.remote.AuthService
import com.example.pp68_salestrackingapp.di.TokenManager
import com.example.pp68_salestrackingapp.utils.SyncManager as OutboxSyncManager
import com.example.pp68_salestrackingapp.utils.SyncTrigger
import io.mockk.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class AuthRepositoryLoginTest {
    private val apiService: ApiService = mockk(relaxed = true)
    private val authService: AuthService = mockk(relaxed = true)
    private val tokenManager: TokenManager = mockk(relaxed = true)
    private val database: AppDatabase = mockk(relaxed = true)
    private val outbox: OutboxSyncManager = mockk(relaxed = true)
    private lateinit var repo: AuthRepository

    @Before
    fun setUp() {
        clearAllMocks()
        repo = AuthRepository(apiService, authService, tokenManager, database, outbox)
        coEvery { authService.login(any()) } returns Response.success(LoginResponse(token = "jwt-123", userId = "U1"))
        coEvery { outbox.pendingSummary() } returns emptyList()
        coEvery { outbox.rejectedSummary() } returns emptyList()
    }

    @Test
    fun `same user enters immediately and keeps local cache`() = runTest {
        every { tokenManager.getUserData() } returns AuthUser("U1", "old@test.com", "sale")
        coEvery { outbox.pendingSummary() } returns listOf("เช็คอิน" to 2)

        val result = repo.login("u@test.com", "pw")

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { outbox.doSync() }
        coVerify(exactly = 0) { database.clearAllTables() }
        verify { outbox.scheduleSync(SyncTrigger.LOGIN) }
        verify { outbox.scheduleDownload() }
    }

    @Test
    fun `different user with pending rows is blocked before token changes`() = runTest {
        every { tokenManager.getUserData() } returns AuthUser("U-OLD", "old@test.com", "sale")
        coEvery { outbox.pendingSummary() } returns listOf("บันทึกผล" to 1)

        val result = repo.login("new@test.com", "pw")

        assertTrue(result.exceptionOrNull() is AuthRepository.AccountSwitchBlockedException)
        verify(exactly = 0) { tokenManager.saveToken(any()) }
        coVerify(exactly = 0) { database.clearAllTables() }
        verify(exactly = 0) { outbox.scheduleSync(any()) }
    }

    @Test
    fun `unknown legacy owner with pending rows is also blocked`() = runTest {
        every { tokenManager.getUserData() } returns null
        every { tokenManager.getLocalDataOwner() } returns null
        coEvery { outbox.pendingSummary() } returns listOf("โครงการ" to 1)

        val result = repo.login("u@test.com", "pw")

        assertTrue(result.exceptionOrNull() is AuthRepository.AccountSwitchBlockedException)
        verify(exactly = 0) { tokenManager.saveToken(any()) }
    }

    @Test
    fun `different user with rejected rows is blocked and receives the list`() = runTest {
        every { tokenManager.getUserData() } returns AuthUser("U-OLD", "old@test.com", "sale")
        val rejection = SyncRejection("result", "R1", 403, "ไม่มีสิทธิ์", "2026-10-01T00:00:00Z")
        coEvery { outbox.rejectedSummary() } returns listOf(rejection)

        val result = repo.login("new@test.com", "pw")
        val error = result.exceptionOrNull() as AuthRepository.AccountSwitchBlockedException

        assertTrue(error.rejected == listOf(rejection))
        coVerify(exactly = 0) { database.clearAllTables() }
    }

    @Test
    fun `confirmed account switch clears old rows before saving new session`() = runTest {
        every { tokenManager.getUserData() } returns AuthUser("U-OLD", "old@test.com", "sale")
        coEvery { outbox.pendingSummary() } returns listOf("ลูกค้า" to 1)

        val result = repo.login("new@test.com", "pw", discardPreviousData = true)

        assertTrue(result.isSuccess)
        coVerifyOrder {
            database.clearAllTables()
            tokenManager.saveToken("jwt-123")
            tokenManager.saveUserData(any())
        }
        verify { outbox.scheduleDownload() }
    }

    @Test
    fun `clean account switch clears cache without synchronous download`() = runTest {
        every { tokenManager.getUserData() } returns AuthUser("U-OLD", "old@test.com", "sale")

        val result = repo.login("new@test.com", "pw")

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { database.clearAllTables() }
        verify { outbox.scheduleDownload() }
    }

    @Test
    fun `required initial setup stores restricted session and does not start business sync`() = runTest {
        every { tokenManager.getUserData() } returns AuthUser("U1", "u@test.com", "sale")
        coEvery { authService.login(any()) } returns Response.success(
            LoginResponse(
                token = "setup-token",
                userId = "U1",
                setupRequired = true,
                passwordChangeRequired = true,
                phoneRequired = true,
                phoneNoticeVersion = "2026-10-06"
            )
        )

        val result = repo.login("u@test.com", "old-password")

        assertTrue(result.isSuccess)
        verify { tokenManager.saveToken("setup-token") }
        verify { tokenManager.saveInitialSetupRequirement(true, true, "2026-10-06") }
        verify(exactly = 0) { outbox.scheduleSync(any()) }
        verify(exactly = 0) { outbox.scheduleDownload() }
    }

    @Test
    fun `completing initial setup replaces token and starts sync only after server success`() = runTest {
        every { tokenManager.getInitialSetupInfo() } returns InitialSetupInfo(true, true, "2026-10-06")
        coEvery { authService.completeInitialSetup(any()) } returns Response.success(
            LoginResponse(token = "full-token", userId = "U1")
        )

        val result = repo.completeInitialSetup("old-password", "new-password", "0812345678")

        assertTrue(result.isSuccess)
        verifyOrder {
            tokenManager.saveToken("full-token")
            tokenManager.saveInitialSetupRequirement(false, false, null)
            outbox.scheduleSync(SyncTrigger.LOGIN)
            outbox.scheduleDownload()
        }
    }

    @Test
    fun `failed initial setup keeps restricted token and never starts sync`() = runTest {
        every { tokenManager.getInitialSetupInfo() } returns InitialSetupInfo(true, true, "2026-10-06")
        coEvery { authService.completeInitialSetup(any()) } returns Response.error(
            400,
            "{\"error\":\"BAD_REQUEST\",\"message\":\"เบอร์ไม่ถูกต้อง\"}"
                .toResponseBody("application/json".toMediaType())
        )

        val result = repo.completeInitialSetup("old-password", "new-password", "123")

        assertTrue(result.isFailure)
        verify(exactly = 0) { tokenManager.saveToken("full-token") }
        verify(exactly = 0) { outbox.scheduleSync(any()) }
        verify(exactly = 0) { outbox.scheduleDownload() }
    }
}
