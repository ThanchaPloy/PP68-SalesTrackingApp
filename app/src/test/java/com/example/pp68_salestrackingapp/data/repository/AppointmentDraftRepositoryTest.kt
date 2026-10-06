package com.example.pp68_salestrackingapp.data.repository

import androidx.room.withTransaction
import com.example.pp68_salestrackingapp.data.local.AppDatabase
import com.example.pp68_salestrackingapp.data.local.AppointmentDraftDao
import com.example.pp68_salestrackingapp.data.model.AppointmentDraft
import com.example.pp68_salestrackingapp.data.model.AuthUser
import com.example.pp68_salestrackingapp.di.TokenManager
import io.mockk.called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class AppointmentDraftRepositoryTest {

    private val dao: AppointmentDraftDao = mockk(relaxed = true)
    private val database: AppDatabase = mockk(relaxed = true)
    private val tokenManager: TokenManager = mockk(relaxed = true)
    private val clock = Clock.fixed(Instant.parse("2026-10-06T03:00:00Z"), ZoneId.of("UTC"))
    private lateinit var repo: AppointmentDraftRepository

    /** owner key เป็น SHA-256 ของ userId — คำนวณด้วยสูตรเดียวกับของจริง ไม่ฮาร์ดโค้ด */
    private val ownerKey get() = syncAccountKey("U1")

    @Before
    fun setUp() {
        every { database.appointmentDraftDao() } returns dao
        every { tokenManager.getUserData() } returns AuthUser("U1", "u@test.com", "sale")

        mockkStatic("androidx.room.RoomDatabaseKt")
        val block = slot<suspend () -> Any>()
        coEvery { database.withTransaction(capture(block)) } coAnswers { block.captured.invoke() }

        repo = AppointmentDraftRepository(database, tokenManager, clock)
    }

    private fun existing(id: String, createdAt: String) = AppointmentDraft(
        draftId = id,
        ownerKey = ownerKey,
        schemaVersion = 1,
        payloadJson = "{}",
        createdAt = createdAt,
        updatedAt = createdAt,
        expiresAt = "2026-11-05T03:00:00Z"
    )

    @Test
    fun `a new draft gets an id an expiry thirty days out and the caller's owner key`() = runTest {
        coEvery { dao.countForOwner(ownerKey) } returns 0

        val result = repo.saveDraft(draftId = null, schemaVersion = 1, payloadJson = """{"a":1}""")

        val saved = slot<AppointmentDraft>()
        coVerify { dao.upsert(capture(saved)) }
        assertTrue(result is DraftSaveResult.Saved)
        assertEquals(ownerKey, saved.captured.ownerKey)
        assertEquals("2026-10-06T03:00:00Z", saved.captured.createdAt)
        assertEquals("2026-11-05T03:00:00Z", saved.captured.expiresAt)
        assertNotNull(saved.captured.draftId)
    }

    /** กดบันทึกร่างเดิมซ้ำ ๆ ต้องทับของเดิม ไม่ใช่งอกสำเนาจนเต็มโควตา */
    @Test
    fun `saving an existing draft updates it in place and keeps the original created time`() = runTest {
        coEvery { dao.getById(ownerKey, "D1") } returns existing("D1", "2026-10-01T03:00:00Z")
        coEvery { dao.countForOwner(ownerKey) } returns 20

        val result = repo.saveDraft(draftId = "D1", schemaVersion = 1, payloadJson = """{"a":2}""")

        val saved = slot<AppointmentDraft>()
        coVerify { dao.upsert(capture(saved)) }
        assertEquals(DraftSaveResult.Saved("D1"), result)
        assertEquals("D1", saved.captured.draftId)
        // วันที่สร้างต้องไม่ขยับ ไม่งั้นร่างที่แก้ทุกวันจะไม่มีวันหมดอายุ
        assertEquals("2026-10-01T03:00:00Z", saved.captured.createdAt)
        assertEquals("2026-10-06T03:00:00Z", saved.captured.updatedAt)
    }

    /** เต็มโควตาแล้วต้องบอกผู้ใช้ ห้ามลบของเก่าสุดทิ้งเงียบ ๆ */
    @Test
    fun `the twenty-first draft is refused and nothing is written`() = runTest {
        coEvery { dao.countForOwner(ownerKey) } returns AppointmentDraftRepository.MAX_DRAFTS_PER_ACCOUNT

        val result = repo.saveDraft(draftId = null, schemaVersion = 1, payloadJson = "{}")

        assertEquals(
            DraftSaveResult.LimitReached(AppointmentDraftRepository.MAX_DRAFTS_PER_ACCOUNT),
            result
        )
        coVerify(exactly = 0) { dao.upsert(any()) }
        coVerify(exactly = 0) { dao.deleteById(any(), any()) }
    }

    @Test
    fun `a draft id that does not belong to this account is treated as a new draft`() = runTest {
        // getById กรองด้วย owner อยู่แล้ว จึงคืน null สำหรับร่างของบัญชีอื่น
        coEvery { dao.getById(ownerKey, "SOMEONE-ELSE") } returns null
        coEvery { dao.countForOwner(ownerKey) } returns 0

        repo.saveDraft(draftId = "SOMEONE-ELSE", schemaVersion = 1, payloadJson = "{}")

        val saved = slot<AppointmentDraft>()
        coVerify { dao.upsert(capture(saved)) }
        assertEquals(ownerKey, saved.captured.ownerKey)
    }

    @Test
    fun `without a logged-in user nothing is read or written`() = runTest {
        every { tokenManager.getUserData() } returns null

        val result = repo.saveDraft(draftId = null, schemaVersion = 1, payloadJson = "{}")

        assertTrue(result is DraftSaveResult.Failed)
        assertEquals(0, repo.countDrafts())
        assertEquals(0, repo.deleteExpired())
        assertEquals(null, repo.getDraft("D1"))
        repo.deleteDraft("D1")
        coVerify { dao wasNot called }
    }

    @Test
    fun `expiry cleanup is scoped to this account and uses the injected clock`() = runTest {
        coEvery { dao.deleteExpired(ownerKey, "2026-10-06T03:00:00Z") } returns 3

        assertEquals(3, repo.deleteExpired())

        coVerify(exactly = 1) { dao.deleteExpired(ownerKey, "2026-10-06T03:00:00Z") }
    }

    @Test
    fun `deleting a draft is scoped to this account`() = runTest {
        repo.deleteDraft("D1")
        coVerify(exactly = 1) { dao.deleteById(ownerKey, "D1") }
    }
}
