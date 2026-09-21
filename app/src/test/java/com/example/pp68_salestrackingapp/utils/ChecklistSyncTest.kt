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

    private lateinit var sync: SyncManager

    private val items = listOf(
        ActivityPlanItem(id = 1, appointmentId = "A1", masterId = 10, actName = "นำเสนอสินค้า", isDone = true, isSynced = false)
    )

    @Before
    fun setUp() {
        sync = SyncManager(
            context, apiService, tokenManager, customerDao, projectDao, contactDao,
            activityDao, resultDao, photoDao, appointmentContactDao, planItemDao, projectContactDao
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

        sync.doSync()

        coVerify(exactly = 0) { apiService.deleteChecklistByAppointment(any()) }
        coVerify(exactly = 0) { planItemDao.updateSyncStatusByAppointment(any(), any()) }
    }

    @Test
    fun `pending checklist items count as unsynced work`() = runTest {
        coEvery { planItemDao.getUnsyncedAppointmentIds() } returns listOf("A1")

        assertTrue(sync.hasPendingChanges())
    }
}
