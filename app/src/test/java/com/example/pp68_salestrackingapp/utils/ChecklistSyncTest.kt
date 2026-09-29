package com.example.pp68_salestrackingapp.utils

import android.content.Context
import com.example.pp68_salestrackingapp.data.local.*
import com.example.pp68_salestrackingapp.data.model.ActivityPlanItem
import com.example.pp68_salestrackingapp.data.remote.ApiService
import com.example.pp68_salestrackingapp.di.TokenManager
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class ChecklistSyncTest {

    private val context: Context = mockk(relaxed = true)
    private val apiService: ApiService = mockk(relaxed = true)
    private val tokenManager: TokenManager = mockk(relaxed = true)
    private val customerDao: CustomerDao = mockk(relaxed = true)
    private val projectDao: ProjectDao = mockk(relaxed = true)
    private val contactDao: ContactDao = mockk(relaxed = true)
    private val activityDao: ActivityDao = mockk(relaxed = true)
    private val resultDao: ActivityResultDao = mockk(relaxed = true)
    private val photoDao: ActivityResultPhotoDao = mockk(relaxed = true)
    private val appointmentContactDao: AppointmentContactDao = mockk(relaxed = true)
    private val planItemDao: ActivityPlanItemDao = mockk(relaxed = true)
    private val projectContactDao: ProjectContactDao = mockk(relaxed = true)
    private val syncRejectionDao: SyncRejectionDao = mockk(relaxed = true)

    private lateinit var sync: SyncManager

    private val items = listOf(
        ActivityPlanItem(id = 1, appointmentId = "A1", masterId = 10, actName = "นำเสนอสินค้า", isDone = true, isSynced = false)
    )

    @Before
    fun setUp() {
        sync = SyncManager(
            context, apiService, tokenManager, customerDao, projectDao, contactDao,
            activityDao, resultDao, photoDao, appointmentContactDao, planItemDao, projectContactDao,
            syncRejectionDao
        )
        coEvery { customerDao.getUnsyncedCustomers() } returns emptyList()
        coEvery { contactDao.getUnsyncedContacts() } returns emptyList()
        coEvery { projectDao.getUnsyncedProjects() } returns emptyList()
        coEvery { activityDao.getUnsyncedActivities() } returns emptyList()
        coEvery { resultDao.getUnsyncedResults() } returns emptyList()
        coEvery { planItemDao.getUnsyncedAppointmentIds() } returns emptyList()
    }

    // checklist ที่ติ๊กตอนออฟไลน์เคยหายถาวรเพราะ outbox ไม่เคยวนตารางนี้
    @Test
    fun `a pending checklist is pushed and then cleared`() = runTest {
        coEvery { planItemDao.getUnsyncedAppointmentIds() } returns listOf("A1")
        coEvery { planItemDao.getPlanItemsByAppointmentId("A1") } returns items
        coEvery { apiService.deleteChecklistByAppointment(any()) } returns Response.success(Unit)
        coEvery { apiService.insertChecklist(any()) } returns Response.success(emptyList())

        sync.doSync()

        coVerifyOrder {
            apiService.deleteChecklistByAppointment("eq.A1")
            apiService.insertChecklist(match { it.size == 1 && it.first().masterId == 10 })
        }
        coVerify(exactly = 1) { planItemDao.updateSyncStatusByAppointment("A1", true) }
    }

    @Test
    fun `a rejected push stays pending for the next run`() = runTest {
        coEvery { planItemDao.getUnsyncedAppointmentIds() } returns listOf("A1")
        coEvery { planItemDao.getPlanItemsByAppointmentId("A1") } returns items
        coEvery { apiService.deleteChecklistByAppointment(any()) } returns Response.success(Unit)
        coEvery { apiService.insertChecklist(any()) } returns
            Response.error<List<com.example.pp68_salestrackingapp.data.model.ChecklistInsertDto>>(
                500, "boom".toResponseBody("text/plain".toMediaType())
            )

        sync.doSync()

        coVerify(exactly = 0) { planItemDao.updateSyncStatusByAppointment("A1", true) }
    }

    // นัดหมายที่ยังเป็น TEMP- ต้องรอให้ได้ id จริงก่อน ไม่งั้น checklist จะผูกกับ id ที่ไม่มีอยู่
    @Test
    fun `a checklist on an unsynced appointment is left alone`() = runTest {
        coEvery { planItemDao.getUnsyncedAppointmentIds() } returns listOf("TEMP-ABC123")
        // นัดหมายแม่ยังอยู่ในเครื่อง แค่ยังไม่ได้ id จริง — ต้องรอ ไม่ใช่ลบทิ้ง
        coEvery { activityDao.getActivityById("TEMP-ABC123") } returns activity("TEMP-ABC123")

        sync.doSync()

        coVerify(exactly = 0) { apiService.deleteChecklistByAppointment(any()) }
        coVerify(exactly = 0) { planItemDao.updateSyncStatusByAppointment(any(), any()) }
        coVerify(exactly = 0) { planItemDao.deletePlanItemsByAppointmentId(any()) }
    }

    @Test
    fun `pending checklist items count as unsynced work`() = runTest {
        coEvery { planItemDao.getUnsyncedAppointmentIds() } returns listOf("A1")
        coEvery { activityDao.getActivityById("A1") } returns activity("A1")

        assertTrue(sync.hasPendingChanges())
    }

    private fun activity(id: String) = com.example.pp68_salestrackingapp.data.model.SalesActivity(
        activityId = id, userId = "U1", activityType = "onsite",
        activityDate = "2026-04-01", status = "planned"
    )

    // เคสที่ทำให้ logout ค้างถาวรแม้เน็ตดี: นัดหมายแม่ถูกลบไปแล้ว แต่ checklist ยังค้าง is_synced = 0
    // id เป็น TEMP- จึงถูก continue ข้ามทุกรอบ ไม่มีวันถูกส่งและไม่มีวันถูก mark synced
    @Test
    fun `an orphaned TEMP checklist is deleted instead of blocking sync forever`() = runTest {
        coEvery { planItemDao.getUnsyncedAppointmentIds() } returns listOf("TEMP-GONE")
        coEvery { activityDao.getActivityById("TEMP-GONE") } returns null

        sync.doSync()

        coVerify(exactly = 1) { planItemDao.deletePlanItemsByAppointmentId("TEMP-GONE") }
        coVerify(exactly = 0) { apiService.deleteChecklistByAppointment(any()) }
    }

    // เคสเดียวกันแต่ id จริง: ส่งขึ้นไปจะได้ 404 เพราะ server ไม่มีนัดหมายนั้นแล้ว ต้องลบทิ้งเหมือนกัน
    @Test
    fun `an orphaned checklist with a real id is deleted rather than pushed`() = runTest {
        coEvery { planItemDao.getUnsyncedAppointmentIds() } returns listOf("A-GONE")
        coEvery { activityDao.getActivityById("A-GONE") } returns null

        sync.doSync()

        coVerify(exactly = 1) { planItemDao.deletePlanItemsByAppointmentId("A-GONE") }
        coVerify(exactly = 0) { apiService.insertChecklist(any()) }
    }

    // เคลียร์ขยะกำพร้าแล้วต้องไม่เหลืองานค้าง = logout ผ่าน
    @Test
    fun `sync clears the pending flag once orphans are gone`() = runTest {
        coEvery { planItemDao.getUnsyncedAppointmentIds() } returnsMany listOf(
            listOf("TEMP-GONE"),
            emptyList()
        )
        coEvery { activityDao.getActivityById("TEMP-GONE") } returns null

        sync.doSync()

        assertTrue(sync.pendingSummary().isEmpty())
    }
}
