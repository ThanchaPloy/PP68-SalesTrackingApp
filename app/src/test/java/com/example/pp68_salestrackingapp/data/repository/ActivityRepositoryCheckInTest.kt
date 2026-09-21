package com.example.pp68_salestrackingapp.data.repository

import com.example.pp68_salestrackingapp.data.local.*
import com.example.pp68_salestrackingapp.data.model.SalesActivity
import com.example.pp68_salestrackingapp.data.remote.ApiService
import com.example.pp68_salestrackingapp.data.remote.UploadApiService
import com.example.pp68_salestrackingapp.utils.SyncManager
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class ActivityRepositoryCheckInTest {

    private val apiService: ApiService = mockk(relaxed = true)
    private val uploadApiService: UploadApiService = mockk(relaxed = true)
    private val activityDao: ActivityDao = mockk(relaxed = true)
    private val projectDao: ProjectDao = mockk(relaxed = true)
    private val customerDao: CustomerDao = mockk(relaxed = true)
    private val contactDao: ContactDao = mockk(relaxed = true)
    private val planItemDao: ActivityPlanItemDao = mockk(relaxed = true)
    private val resultDao: ActivityResultDao = mockk(relaxed = true)
    private val photoDao: ActivityResultPhotoDao = mockk(relaxed = true)
    private val appointmentContactDao: AppointmentContactDao = mockk(relaxed = true)
    private val projectRepo: ProjectRepository = mockk(relaxed = true)
    private val syncManager: SyncManager = mockk(relaxed = true)

    private lateinit var repo: ActivityRepository

    private val activity = SalesActivity(
        activityId = "A1",
        userId = "U1",
        customerId = "C1",
        projectId = "PRJ-1",
        activityType = "onsite",
        activityDate = "2026-04-06",
        status = "planned"
    )

    @Before
    fun setUp() {
        repo = ActivityRepository(
            apiService, uploadApiService, activityDao, projectDao, customerDao, contactDao,
            planItemDao, resultDao, photoDao, appointmentContactDao, projectRepo, syncManager
        )
        coEvery { activityDao.getActivityById("A1") } returns activity
    }

    @Test
    fun `a check-in the server accepted is stored as synced`() = runTest {
        coEvery { apiService.updateActivity(any(), any()) } returns
            Response.success(listOf(activity))

        repo.checkIn("A1", 13.7563, 100.5018, isVerified = true, distanceDeviation = 12.0)

        val saved = slot<SalesActivity>()
        coVerify { activityDao.insertActivity(capture(saved)) }
        assertTrue(saved.captured.isSynced)
    }

    // เคสที่เคยพัง: server ปฏิเสธ แต่โค้ดเดิมไม่ดูผลลัพธ์แล้วปัก synced ทิ้งไว้
    // แถวนั้นจะไม่เข้าคิว outbox อีกเลย การเช็คอินจึงหายจาก server ถาวร
    @Test
    fun `a check-in the server rejected stays queued for retry`() = runTest {
        coEvery { apiService.updateActivity(any(), any()) } returns
            Response.error(500, "boom".toResponseBody("text/plain".toMediaType()))

        repo.checkIn("A1", 13.7563, 100.5018, isVerified = true, distanceDeviation = 12.0)

        val saved = slot<SalesActivity>()
        coVerify { activityDao.insertActivity(capture(saved)) }
        assertFalse(saved.captured.isSynced)
        verify { syncManager.scheduleSync() }
    }

    // PATCH ที่ไม่แมตช์แถวไหนเลยตอบ 200 พร้อม array ว่าง — ต้องไม่นับว่าสำเร็จ
    @Test
    fun `an empty success body does not count as written`() = runTest {
        coEvery { apiService.updateActivity(any(), any()) } returns
            Response.success(emptyList())

        repo.checkIn("A1", 13.7563, 100.5018, isVerified = false, distanceDeviation = null)

        val saved = slot<SalesActivity>()
        coVerify { activityDao.insertActivity(capture(saved)) }
        assertFalse(saved.captured.isSynced)
        verify { syncManager.scheduleSync() }
    }
}
