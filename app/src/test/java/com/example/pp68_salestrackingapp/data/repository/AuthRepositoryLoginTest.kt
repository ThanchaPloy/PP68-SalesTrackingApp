package com.example.pp68_salestrackingapp.data.repository

import com.example.pp68_salestrackingapp.data.local.AppDatabase
import com.example.pp68_salestrackingapp.data.model.LoginResponse
import com.example.pp68_salestrackingapp.data.remote.ApiService
import com.example.pp68_salestrackingapp.data.remote.AuthService
import com.example.pp68_salestrackingapp.di.TokenManager
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import com.example.pp68_salestrackingapp.utils.SyncManager as OutboxSyncManager

@OptIn(ExperimentalCoroutinesApi::class)
class AuthRepositoryLoginTest {

    private val apiService: ApiService = mockk(relaxed = true)
    private val authService: AuthService = mockk(relaxed = true)
    private val tokenManager: TokenManager = mockk(relaxed = true)
    private val database: AppDatabase = mockk(relaxed = true)
    private val syncManager: SyncManager = mockk(relaxed = true)
    private val outbox: OutboxSyncManager = mockk(relaxed = true)

    private lateinit var repo: AuthRepository

    @Before
    fun setUp() {
        repo = AuthRepository(apiService, authService, tokenManager, database, syncManager, outbox)
        coEvery { authService.login(any()) } returns Response.success(
            LoginResponse(token = "jwt-123", userId = "U1")
        )
    }

    // session หมดอายุลบแค่ token ไม่ลบ Room — งานที่ทำตอนออฟไลน์จึงยังค้างอยู่ตอน login ใหม่
    // ต้องดันขึ้นเซิร์ฟเวอร์ก่อน clearAllTables ไม่งั้นหายถาวร
    @Test
    fun `pending offline work is flushed before the local database is wiped`() = runTest {
        coEvery { outbox.hasPendingChanges() } returns true

        repo.login("u@test.com", "pw")

        coVerifyOrder {
            tokenManager.saveToken("jwt-123")
            outbox.doSync()
            database.clearAllTables()
        }
    }

    @Test
    fun `nothing pending means no extra sync before the wipe`() = runTest {
        coEvery { outbox.hasPendingChanges() } returns false

        repo.login("u@test.com", "pw")

        coVerify(exactly = 0) { outbox.doSync() }
        coVerify(exactly = 1) { database.clearAllTables() }
    }

    // ยังออฟไลน์อยู่ก็ต้อง login ต่อได้ ไม่ใช่ค้างหรือพัง
    @Test
    fun `a failing flush still lets the login finish`() = runTest {
        coEvery { outbox.hasPendingChanges() } returns true
        coEvery { outbox.doSync() } throws RuntimeException("offline")

        val result = repo.login("u@test.com", "pw")

        assert(result.isSuccess)
        coVerify(exactly = 1) { database.clearAllTables() }
    }
}
