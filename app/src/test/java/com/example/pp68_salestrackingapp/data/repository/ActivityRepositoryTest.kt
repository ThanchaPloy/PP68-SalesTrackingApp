package com.example.pp68_salestrackingapp.data.repository

import com.example.pp68_salestrackingapp.data.local.*
import com.example.pp68_salestrackingapp.data.model.*
import com.example.pp68_salestrackingapp.data.remote.ApiService
import com.example.pp68_salestrackingapp.data.remote.UploadApiService
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class ActivityRepositoryTest {

    private lateinit var repository: ActivityRepository
    private val apiService  = mockk<ApiService>(relaxed = true)
    private val uploadApiService = mockk<UploadApiService>(relaxed = true)
    private val activityDao = mockk<ActivityDao>(relaxed = true)
    private val projectDao  = mockk<ProjectDao>(relaxed = true)
    private val customerDao = mockk<CustomerDao>(relaxed = true)
    private val contactDao  = mockk<ContactDao>(relaxed = true)
    private val planItemDao = mockk<ActivityPlanItemDao>(relaxed = true)
    private val resultDao   = mockk<ActivityResultDao>(relaxed = true)

    private val sampleActivity = SalesActivity(
        activityId   = "APT-001",
        userId       = "USR-001",
        customerId   = "CST-001",
        activityType = "onsite",
        activityDate = "2026-03-28",
        status       = "planned"
    )

    private val samplePlanItems by lazy {
        listOf(
            ActivityPlanItem(appointmentId = "APT-001", masterId = 1,
                actName = "ระบุลูกค้า", isDone = false),
            ActivityPlanItem(appointmentId = "APT-001", masterId = 2,
                actName = "สำรวจความต้องการ", isDone = true)
        )
    }

    private val sampleActivities = listOf(
        SalesActivity(activityId = "APT-001", userId = "USR-001",
            customerId = "CST-001", projectId = "PJ-001",
            activityType = "onsite", activityDate = "2026-03-28", status = "planned")
    )

    private val sampleProjects = listOf(
        Project(projectId = "PJ-001", custId = "CST-001", projectName = "คอนโด XT")
    )
    private val sampleCustomers = listOf(
        Customer(
            "CST-001", "บริษัท แสนสิริ", null, "Developer",
            null, null, null, "customer", null
        )
    )

    @Before
    fun setup() {
        repository = ActivityRepository(
            apiService, uploadApiService, activityDao, projectDao, customerDao, contactDao, planItemDao, resultDao
        )
    }

    // TC-UNIT-ACT-01
    @Test
    fun `refreshActivities success should clear and insert activities`() = runTest {
        coEvery { apiService.getMyAppointments("eq.USR-001") } returns
                Response.success(listOf(sampleActivity))

        val result = repository.refreshActivities("USR-001")

        assertTrue(result.isSuccess)
        coVerify { activityDao.clearAndInsert(listOf(sampleActivity)) }
    }

    // TC-UNIT-ACT-02
    @Test
    fun `refreshActivities API error should return failure`() = runTest {
        coEvery { apiService.getMyAppointments(any()) } returns
                Response.error(500, "error".toResponseBody())

        val result = repository.refreshActivities("USR-001")

        assertTrue(result.isFailure)
    }

    // TC-UNIT-ACT-03
    @Test
    fun `addActivity success should insert to local DB`() = runTest {
        coEvery { apiService.addActivity(any()) } returns
                Response.success(listOf(sampleActivity))

        val result = repository.addActivity(sampleActivity)

        assertTrue(result.isSuccess)
        coVerify { activityDao.insertActivity(any()) }
    }

    // TC-UNIT-ACT-04
    @Test
    fun `addActivity offline should still save to local DB and return success`() = runTest {
        coEvery { apiService.addActivity(any()) } throws Exception("Network error")

        val result = repository.addActivity(sampleActivity)

        assertTrue(result.isSuccess)
        coVerify { activityDao.insertActivity(sampleActivity) }
    }

    // TC-UNIT-ACT-05
    @Test
    fun `updateActivity success should return success`() = runTest {
        val updates = mapOf<String, Any>("plan_status" to "checked_in")
        coEvery { apiService.updateActivity(any(), any()) } returns
                Response.success(listOf(sampleActivity))

        val result = repository.updateActivity("APT-001", updates)

        assertTrue(result.isSuccess)
    }

    // TC-UNIT-ACT-06
    @Test
    fun `updateActivity API error should return failure`() = runTest {
        coEvery { apiService.updateActivity(any(), any()) } returns
                Response.error(400, "error".toResponseBody())

        val result = repository.updateActivity("APT-001", emptyMap())

        assertTrue(result.isFailure)
    }

    // TC-UNIT-ACT-07
    @Test
    fun `getActivityById should return local data first without calling API`() = runTest {
        coEvery { activityDao.getActivityById("APT-001") } returns sampleActivity

        val result = repository.getActivityById("APT-001")

        assertTrue(result.isSuccess)
        assertEquals("APT-001", result.getOrNull()?.first()?.activityId)
        coVerify(exactly = 0) { apiService.getAppointmentById(any()) }
    }

    // TC-UNIT-ACT-08
    @Test
    fun `getActivityById local null should fetch from API`() = runTest {
        coEvery { activityDao.getActivityById("APT-001") } returns null
        coEvery { apiService.getAppointmentById("eq.APT-001") } returns
                Response.success(listOf(sampleActivity))

        val result = repository.getActivityById("APT-001")

        assertTrue(result.isSuccess)
        coVerify { apiService.getAppointmentById("eq.APT-001") }
    }

    // TC-UNIT-ACT-09
    @Test
    fun `checkIn success should return success`() = runTest {
        coEvery { activityDao.getActivityById("APT-001") } returns sampleActivity
        coEvery { apiService.updateActivity(any(), any()) } returns
                Response.success(listOf(sampleActivity))

        val result = repository.checkIn("APT-001", 13.7, 100.5, true)

        assertTrue(result.isSuccess)
    }

    // TC-UNIT-ACT-10
    @Test
    fun `checkIn offline should still return success`() = runTest {
        coEvery { activityDao.getActivityById("APT-001") } returns sampleActivity
        coEvery { apiService.updateActivity(any(), any()) } throws Exception("Network error")

        val result = repository.checkIn("APT-001", 13.7, 100.5, false)

        assertTrue(result.isSuccess)
    }

    // TC-UNIT-ACT-11
    @Test
    fun `finishActivity success should return success`() = runTest {
        coEvery { planItemDao.getPlanItemsByAppointmentId("APT-001") } returns emptyList()
        coEvery { apiService.updateActivity(any(), any()) } returns
                Response.success(listOf(sampleActivity))
        coEvery { activityDao.getActivityById("APT-001") } returns sampleActivity

        val result = repository.finishActivity("APT-001", listOf(1, 2), "note")

        assertTrue(result.isSuccess)
    }

    // TC-UNIT-ACT-12
    @Test
    fun `deleteActivity should call deleteActivityById on local DB`() = runTest {
        coEvery { apiService.deleteActivity(any()) } returns Response.success(Unit)

        val result = repository.deleteActivity("APT-001")

        assertTrue(result.isSuccess)
        coVerify { activityDao.deleteActivityById("APT-001") }
    }

    // TC-UNIT-ACT-13
    @Test
    fun `getMasterActivities success should return mapped list`() = runTest {
        val dtos = listOf(
            ActivityMasterDto(1, "Lead", "ระบุลูกค้า"),
            ActivityMasterDto(2, "Quotation", "ส่ง Quotation")
        )
        coEvery { apiService.getMasterActivities() } returns Response.success(dtos)

        val result = repository.getMasterActivities()

        assertEquals(2, result.size)
        assertEquals(1, result.first().masterId)
    }

    // TC-UNIT-ACT-14
    @Test
    fun `getMasterActivities network error should return empty list`() = runTest {
        coEvery { apiService.getMasterActivities() } throws Exception("Network error")

        val result = repository.getMasterActivities()

        assertTrue(result.isEmpty())
    }

    // TC-UNIT-ACT-PLAN-01
    @Test
    fun `getPlanItems should return local items first`() = runTest {
        coEvery { planItemDao.getPlanItemsByAppointmentId("APT-001") } returns samplePlanItems

        val result = repository.getPlanItems("APT-001")

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull()?.size)
        coVerify(exactly = 0) { apiService.getChecklistByAppointment(any()) }
    }

    // TC-UNIT-ACT-PLAN-02
    @Test
    fun `getPlanItems local empty should fetch from API`() = runTest {
        coEvery { planItemDao.getPlanItemsByAppointmentId("APT-001") } returns emptyList()
        coEvery { apiService.getChecklistByAppointment("eq.APT-001") } returns
                Response.success(listOf(
                    ChecklistItemDto(masterId = 1, isDone = false),
                    ChecklistItemDto(masterId = 2, isDone = true)
                ))
        coEvery { apiService.getMasterActivities() } returns
                Response.success(listOf(
                    ActivityMasterDto(1, "Lead", "ระบุลูกค้า"),
                    ActivityMasterDto(2, "Lead", "สำรวจความต้องการ")
                ))

        val result = repository.getPlanItems("APT-001")

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull()?.size)
        coVerify { apiService.getChecklistByAppointment("eq.APT-001") }
    }

    // TC-UNIT-ACT-PLAN-03
    @Test
    fun `getPlanItems API empty should return empty list`() = runTest {
        coEvery { planItemDao.getPlanItemsByAppointmentId("APT-001") } returns emptyList()
        coEvery { apiService.getChecklistByAppointment(any()) } returns
                Response.success(emptyList())

        val result = repository.getPlanItems("APT-001")

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()?.isEmpty() == true)
    }

    // TC-UNIT-ACT-PLAN-04
    @Test
    fun `savePlanItems should delete old items and insert new ones`() = runTest {
        repository.savePlanItems("APT-001", samplePlanItems)

        coVerify { planItemDao.deletePlanItemsByAppointmentId("APT-001") }
        coVerify { planItemDao.insertPlanItems(samplePlanItems) }
    }

    // TC-UNIT-ACT-PLAN-05
    @Test
    fun `updatePlanItemStatus should call planItemDao`() = runTest {
        repository.updatePlanItemStatus("APT-001", 1, true)

        coVerify { planItemDao.updateItemStatus("APT-001", 1, true) }
    }

    // TC-UNIT-ACT-PLAN-06
    @Test
    fun `updateChecklistItem should update local and call API`() = runTest {
        coEvery { apiService.updateChecklist(any(), any(), any()) } returns
                Response.success(emptyList())

        repository.updateChecklistItem("APT-001", 1, true)

        coVerify { planItemDao.updateItemStatus("APT-001", 1, true) }
        coVerify { apiService.updateChecklist("eq.APT-001", "eq.1", any()) }
    }

    // TC-UNIT-ACT-DETAILS-01
    @Test
    fun `getMyActivitiesWithDetails should return ActivityCards with project and customer name`() = runTest {
        every { activityDao.getAllActivities() } returns flowOf(sampleActivities)
        every { projectDao.getAllProjects()    } returns flowOf(sampleProjects)
        every { customerDao.getAllCustomers()  } returns flowOf(sampleCustomers)

        val result = repository.getMyActivitiesWithDetails()

        assertTrue(result.isSuccess)
        val cards = result.getOrNull()!!
        assertEquals(1, cards.size)
        assertEquals("คอนโด XT", cards.first().projectName)
        assertEquals("บริษัท แสนสิริ", cards.first().companyName)
    }

    // TC-UNIT-ACT-DETAILS-02
    @Test
    fun `getMyActivitiesWithDetails no project match should use activity projectName`() = runTest {
        val activityWithName = sampleActivities.first().copy(
            projectId   = "PJ-UNKNOWN",
            projectName = "Fallback Project"
        )
        every { activityDao.getAllActivities() } returns flowOf(listOf(activityWithName))
        every { projectDao.getAllProjects()    } returns flowOf(emptyList())
        every { customerDao.getAllCustomers()  } returns flowOf(emptyList())

        val result = repository.getMyActivitiesWithDetails()

        assertTrue(result.isSuccess)
        assertEquals("Fallback Project", result.getOrNull()?.first()?.projectName)
    }

    // TC-UNIT-ACT-DETAILS-03
    @Test
    fun `getMyActivitiesWithDetails empty activities should return empty list`() = runTest {
        every { activityDao.getAllActivities() } returns flowOf(emptyList())
        every { projectDao.getAllProjects()    } returns flowOf(emptyList())
        every { customerDao.getAllCustomers()  } returns flowOf(emptyList())

        val result = repository.getMyActivitiesWithDetails()

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()?.isEmpty() == true)
    }

    // TC-UNIT-ACT-DETAILS-04
    @Test
    fun `getMyActivitiesWithDetails should map activityId correctly`() = runTest {
        every { activityDao.getAllActivities() } returns flowOf(sampleActivities)
        every { projectDao.getAllProjects()    } returns flowOf(sampleProjects)
        every { customerDao.getAllCustomers()  } returns flowOf(sampleCustomers)

        val result = repository.getMyActivitiesWithDetails()
        val card = result.getOrNull()!!.first()

        assertEquals("APT-001", card.activityId)
        assertEquals("onsite", card.activityType)
        assertEquals("planned", card.planStatus)
    }
}
