package com.example.pp68_salestrackingapp.data.repository

import com.example.pp68_salestrackingapp.data.local.*
import com.example.pp68_salestrackingapp.data.model.SalesActivity
import com.example.pp68_salestrackingapp.data.remote.ApiService
import com.example.pp68_salestrackingapp.utils.SyncManager
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.Response

/**
 * ลูกค้าเก่า(dynamic) เป็น remote-only ไม่มีแถวใน Room ให้ join หาชื่อ
 * enrichActivity จึงถอยไปใช้ companyName ที่ค้างอยู่ในแถวเดิมเสมอ
 *
 * ถ้าตอนแก้ไขนัดไม่ประทับชื่อใหม่ลงแถวด้วย cust_code จะเปลี่ยนจริงแต่ชื่อที่แสดงยังเป็นของเดิม
 * ผู้ใช้เห็นเหมือนกดแล้วไม่มีอะไรเกิดขึ้น กว่าจะตรงต้องรอ sync รอบถัดไป (เจอจริง 2026-10-07)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ActivityRepositoryCompanyStampTest {

    private val apiService: ApiService = mockk(relaxed = true)
    private val activityDao: ActivityDao = mockk(relaxed = true)
    private val projectDao: ProjectDao = mockk(relaxed = true)
    private val localIdMappingDao: LocalIdMappingDao = mockk(relaxed = true)
    private val customerDao: CustomerDao = mockk(relaxed = true)
    private val contactDao: ContactDao = mockk(relaxed = true)
    private val planItemDao: ActivityPlanItemDao = mockk(relaxed = true)
    private val resultDao: ActivityResultDao = mockk(relaxed = true)
    private val photoDao: ActivityResultPhotoDao = mockk(relaxed = true)
    private val appointmentContactDao: AppointmentContactDao = mockk(relaxed = true)
    private val projectRepo: ProjectRepository = mockk(relaxed = true)
    private val syncManager: SyncManager = mockk(relaxed = true)
    private val networkMonitor: com.example.pp68_salestrackingapp.utils.NetworkMonitor = mockk(relaxed = true)
    private val serverTimeAnchor: com.example.pp68_salestrackingapp.utils.ServerTimeAnchor = mockk(relaxed = true)
    private val context: android.content.Context = mockk(relaxed = true)

    private lateinit var repo: ActivityRepository
    private lateinit var persisted: CapturingSlot<SalesActivity>

    private val existing = SalesActivity(
        activityId = "A1",
        userId = "U1",
        customerId = "ERP-OLD",
        companyName = "บริษัทเดิม จำกัด",
        activityType = "onsite",
        activityDate = java.time.LocalDate.now().plusDays(1).toString(),
        status = "planned"
    )

    @Before
    fun setUp() {
        repo = ActivityRepository(
            apiService, activityDao, projectDao, localIdMappingDao, customerDao, contactDao,
            planItemDao, resultDao, photoDao, appointmentContactDao, projectRepo, syncManager,
            networkMonitor, context, java.time.Clock.systemUTC(), serverTimeAnchor
        )
        persisted = slot()
        coEvery { localIdMappingDao.resolveExistingId(any(), any()) } answers { secondArg() }
        coEvery { localIdMappingDao.resolveMappedId(any(), any()) } answers { secondArg() }
        coEvery { localIdMappingDao.insertActivityResolvingProject(capture(persisted)) } answers { firstArg() }
        coEvery { activityDao.getActivityById("A1") } returns existing
        coEvery { apiService.updateActivity(any(), any()) } returns Response.success(listOf(existing))
    }

    @Test
    fun `picking a different company stamps its name on the local row`() = runTest {
        repo.updateActivity(
            "A1",
            mapOf("cust_code" to "ERP-NEW"),
            isPlanEdit = false,
            localCompanyName = "บริษัทใหม่ จำกัด"
        )

        assertEquals("ERP-NEW", persisted.captured.customerId)
        assertEquals("บริษัทใหม่ จำกัด", persisted.captured.companyName)
    }

    @Test
    fun `clearing the company clears the stamped name too`() = runTest {
        repo.updateActivity(
            "A1",
            mapOf("cust_code" to "CST-UNKNOWN"),
            isPlanEdit = false,
            localCompanyName = null
        )

        assertNull(persisted.captured.companyName)
    }

    /** แก้ฟิลด์อื่นโดยไม่แตะบริษัท ต้องไม่ไปล้างชื่อที่ประทับไว้ */
    @Test
    fun `an edit that does not touch the company leaves the name alone`() = runTest {
        repo.updateActivity(
            "A1",
            mapOf("topic" to "หัวข้อใหม่"),
            isPlanEdit = false
        )

        assertEquals("บริษัทเดิม จำกัด", persisted.captured.companyName)
    }
}
