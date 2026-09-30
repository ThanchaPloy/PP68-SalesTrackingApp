package com.example.pp68_salestrackingapp.data.repository

import com.example.pp68_salestrackingapp.data.local.AppDatabase
import com.example.pp68_salestrackingapp.data.model.AuthUser
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

    // ช่องที่เคยหลุด: session หมดอายุ -> clearToken() ลบ user_id ทิ้ง -> getUserData() คืน null
    // แล้วโค้ดตีเป็น "คนเดิม login ซ้ำ" ทุกครั้ง ถ้าคนที่มา login คือเซลส์อีกคน งานออฟไลน์ของคนก่อน
    // จะถูกดันขึ้นด้วย token ของคนใหม่ และ backend บังคับเจ้าของจาก JWT = งานไปติดชื่อผิดคน
    // ต้องถอยไปอ่าน local_data_owner ที่ไม่ถูก clearToken ลบ
    @Test
    fun `a different user logging in after a session expiry does not push the previous owner's work`() = runTest {
        every { tokenManager.getUserData() } returns null          // token หมดอายุ user_id หายไปแล้ว
        every { tokenManager.getLocalDataOwner() } returns "U-OLD" // แต่ยังรู้ว่าข้อมูลในเครื่องเป็นของใคร
        coEvery { outbox.hasPendingChanges() } returns true

        repo.login("newbie@test.com", "pw")

        coVerify(exactly = 0) { outbox.doSync() }
        coVerify(exactly = 1) { database.clearAllTables() }
    }

    // คนเดิม login ซ้ำหลัง session หมดอายุ ต้องยังดันงานค้างขึ้นให้ก่อนล้าง ไม่ใช่ทิ้ง
    @Test
    fun `the same user logging in after a session expiry still gets their work flushed`() = runTest {
        every { tokenManager.getUserData() } returns null
        every { tokenManager.getLocalDataOwner() } returns "U1"
        coEvery { outbox.hasPendingChanges() } returns true

        repo.login("u@test.com", "pw")

        coVerifyOrder {
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

    // backend บังคับ owner จาก JWT เสมอ (W1) — ถ้าดันงานค้างของ user A ขึ้นด้วย token ของ user B
    // ที่เพิ่ง login ต่อจากเครื่องเดียวกัน งานนั้นจะไปติดชื่อ B แทน ต้องข้ามการดันขึ้นในกรณีนี้
    @Test
    fun `a different user logging in on the same device skips flushing the previous user's pending work`() = runTest {
        every { tokenManager.getUserData() } returns AuthUser(userId = "U-OLD", email = "old@test.com", role = "sale")
        coEvery { outbox.hasPendingChanges() } returns true

        repo.login("u@test.com", "pw") // resolves to userId "U1" per the stubbed login response

        coVerify(exactly = 0) { outbox.doSync() }
        coVerify(exactly = 1) { database.clearAllTables() }
    }

    // login ซ้ำโดยคนเดิม (เช่น session หมดอายุแล้ว login ใหม่) ต้องยังดันงานค้างขึ้นตามปกติ
    @Test
    fun `the same user re-logging in still flushes their own pending work`() = runTest {
        every { tokenManager.getUserData() } returns AuthUser(userId = "U1", email = "u@test.com", role = "sale")
        coEvery { outbox.hasPendingChanges() } returns true

        repo.login("u@test.com", "pw")

        coVerify(exactly = 1) { outbox.doSync() }
    }

    // ด่าน logout เตือนและให้ยืนยันก่อนทิ้งข้อมูล แต่ login เคย clearAllTables ทิ้งเงียบ ๆ
    // แม้ดันขึ้นไม่หมด — ช่องโหว่เดียวที่ข้ามการยืนยันนั้นได้
    @Test
    fun `work still unsent after the flush keeps the local database`() = runTest {
        coEvery { outbox.hasPendingChanges() } returns true
        coEvery { outbox.pendingSummary() } returns listOf("เช็คอิน" to 2)

        repo.login("u@test.com", "pw")

        coVerify(exactly = 1) { outbox.doSync() }
        coVerify(exactly = 0) { database.clearAllTables() }
    }

    // แถวที่เซิร์ฟเวอร์ปฏิเสธถาวรก็ห้ามลบที่นี่ เพราะตอน login ไม่มี dialog ให้ผู้ใช้ยืนยัน
    @Test
    fun `rows the server refuses also keep the local database`() = runTest {
        coEvery { outbox.hasPendingChanges() } returns false
        coEvery { outbox.pendingSummary() } returns emptyList()
        coEvery { outbox.rejectedSummary() } returns listOf(
            com.example.pp68_salestrackingapp.data.model.SyncRejection(
                entityType = "result", entityId = "R1", httpCode = 403,
                reason = "ไม่มีสิทธิ์", rejectedAt = "2026-09-30T00:00:00Z"
            )
        )

        repo.login("u@test.com", "pw")

        coVerify(exactly = 0) { database.clearAllTables() }
    }

    // ส่งขึ้นครบแล้วต้องล้างตามเดิม ไม่งั้นข้อมูลเก่าจะค้างในเครื่องตลอดไป
    @Test
    fun `a clean flush still wipes the local database`() = runTest {
        coEvery { outbox.hasPendingChanges() } returns true
        coEvery { outbox.pendingSummary() } returns emptyList()
        coEvery { outbox.rejectedSummary() } returns emptyList()

        repo.login("u@test.com", "pw")

        coVerify(exactly = 1) { database.clearAllTables() }
    }
}
