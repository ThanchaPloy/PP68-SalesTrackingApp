package com.example.pp68_salestrackingapp.utils

import android.content.Context
import com.example.pp68_salestrackingapp.data.local.*
import com.example.pp68_salestrackingapp.data.model.Customer
import com.example.pp68_salestrackingapp.data.model.Project
import com.example.pp68_salestrackingapp.data.remote.ApiService
import com.example.pp68_salestrackingapp.di.TokenManager
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class SyncManagerTest {

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
    }

    // โปรเจคที่สร้างออฟไลน์ผูกกับลูกค้า TEMP- อยู่ ถ้าไม่ชี้ custId ใหม่ตอนลูกค้าได้ id จริง
    // มันจะถูกอัปขึ้น server พร้อมรหัสลูกค้าที่ไม่มีอยู่จริง และ server ไม่มี FK คอยดักให้
    @Test
    fun `a temp customer getting a real id remaps the projects that referenced it`() = runTest {
        val temp = Customer(custId = "TEMP-ABC123", companyName = "ลูกค้าใหม่", isSynced = false)
        coEvery { customerDao.getUnsyncedCustomers() } returns listOf(temp)
        coEvery { apiService.addCustomer(any()) } returns Response.success(
            listOf(Customer(custId = "C00123", companyName = "ลูกค้าใหม่"))
        )

        sync.doSync()

        coVerify(exactly = 1) { projectDao.updateCustIdForProjects("TEMP-ABC123", "C00123") }
        coVerify(exactly = 1) { contactDao.updateCustIdForContacts("TEMP-ABC123", "C00123") }
        coVerify(exactly = 1) { activityDao.updateCustIdForActivities("TEMP-ABC123", "C00123") }
    }

    // ลบผู้ติดต่อออกจนหมดคือการเปลี่ยนแปลงที่ต้องส่งขึ้น server เหมือนกัน — ถ้าข้ามคำสั่งลบ
    // เพราะรายการว่าง ของเก่าจะค้างบน server แล้วถูกดึงกลับลงมาในรอบถัดไป
    @Test
    fun `clearing every contact still issues the server-side delete`() = runTest {
        val project = Project(projectId = "PRJ-1", projectName = "โครงการ A", isSynced = false)
        coEvery { projectDao.getUnsyncedProjects() } returns listOf(project)
        coEvery { apiService.updateProject(any(), any()) } returns Response.success(listOf(project))
        coEvery { projectContactDao.getContactIdsByProject("PRJ-1") } returns emptyList()

        sync.doSync()

        coVerify(exactly = 1) { apiService.deleteProjectContacts("eq.PRJ-1") }
        coVerify(exactly = 0) { apiService.addProjectContacts(any()) }
    }

    @Test
    fun `a project that still has contacts replaces them on the server`() = runTest {
        val project = Project(projectId = "PRJ-1", projectName = "โครงการ A", isSynced = false)
        coEvery { projectDao.getUnsyncedProjects() } returns listOf(project)
        coEvery { apiService.updateProject(any(), any()) } returns Response.success(listOf(project))
        coEvery { projectContactDao.getContactIdsByProject("PRJ-1") } returns listOf("CT-1", "CT-2")

        sync.doSync()

        coVerifyOrder {
            apiService.deleteProjectContacts("eq.PRJ-1")
            apiService.addProjectContacts(match { it.size == 2 })
        }
    }

    // หัวใจของการแยก "ปฏิเสธถาวร" ออกจาก "เน็ตไม่ดี" — ถ้าแยกผิดทางใดทางหนึ่งจะพังคนละแบบ:
    // นับ 5xx เป็นถาวร = ข้อมูลผู้ใช้ถูกทิ้งเงียบ ๆ, ไม่นับ 4xx = logout ค้างตลอดกาลเหมือนเดิม
    @Test
    fun `a 403 is recorded as permanent while a 500 is left to retry`() = runTest {
        val rejected = Customer(custId = "C-403", companyName = "ห้ามแก้", isSynced = false)
        val flaky    = Customer(custId = "C-500", companyName = "เซิร์ฟเวอร์ล่ม", isSynced = false)
        coEvery { customerDao.getUnsyncedCustomers() } returns listOf(rejected, flaky)
        coEvery { apiService.updateCustomer("eq.C-403", any()) } returns
            Response.error(403, "".toResponseBody(null))
        coEvery { apiService.updateCustomer("eq.C-500", any()) } returns
            Response.error(500, "".toResponseBody(null))

        sync.doSync()

        coVerify(exactly = 1) { syncRejectionDao.upsert(match { it.entityId == "C-403" && it.httpCode == 403 }) }
        coVerify(exactly = 0) { syncRejectionDao.upsert(match { it.entityId == "C-500" }) }
    }

    private fun appointment(id: String) = com.example.pp68_salestrackingapp.data.model.SalesActivity(
        activityId = id, userId = "U1", activityType = "onsite",
        activityDate = "2026-04-01", status = "planned", isSynced = false
    )

    // appointment_contact ไม่มี is_synced ของตัวเอง ตัวที่พาให้ส่งซ้ำได้คือธง is_synced ของนัดหมายแม่
    // ถ้า mark synced ทั้งที่รายชื่อยังไม่ขึ้น จะไม่มีใครมาเก็บให้อีกเลย แล้วหายตอน login รอบหน้า
    @Test
    fun `an appointment is not marked synced while its attendee list still fails to upload`() = runTest {
        coEvery { activityDao.getUnsyncedActivities() } returns listOf(appointment("APT-1"))
        coEvery { apiService.updateActivity("eq.APT-1", any()) } returns
            Response.success(listOf(appointment("APT-1")))
        coEvery { appointmentContactDao.getContactsByAppointmentId("APT-1") } returns emptyList()
        coEvery { apiService.deleteAppointmentContacts("eq.APT-1") } returns
            Response.error(500, "".toResponseBody(null))

        sync.doSync()

        coVerify(exactly = 1) { activityDao.updateSyncStatus("APT-1", false) }
        coVerify(exactly = 0) { activityDao.updateSyncStatus("APT-1", true) }
    }

    // แก้รายชื่อผู้เข้าร่วมตอนออฟไลน์ เดิมเส้น PATCH ไม่ส่ง appointment_contact เลยสักครั้ง
    @Test
    fun `patching an appointment pushes its attendee list too`() = runTest {
        val contacts = listOf(com.example.pp68_salestrackingapp.data.model.AppointmentContact("APT-1", "CT-9"))
        coEvery { activityDao.getUnsyncedActivities() } returns listOf(appointment("APT-1"))
        coEvery { apiService.updateActivity("eq.APT-1", any()) } returns
            Response.success(listOf(appointment("APT-1")))
        coEvery { appointmentContactDao.getContactsByAppointmentId("APT-1") } returns contacts
        coEvery { apiService.deleteAppointmentContacts("eq.APT-1") } returns Response.success(Unit)
        coEvery { apiService.addAppointmentContacts(any()) } returns Response.success(Unit)

        sync.doSync()

        coVerifyOrder {
            apiService.deleteAppointmentContacts("eq.APT-1")
            apiService.addAppointmentContacts(match { it.size == 1 && it.first().contactId == "CT-9" })
        }
        coVerify(exactly = 1) { activityDao.updateSyncStatus("APT-1", true) }
    }

    // ลบผู้เข้าร่วมออกจนหมดก็ต้องส่งคำสั่งลบขึ้นไป เหมือนฝั่งผู้ติดต่อโครงการ
    @Test
    fun `clearing every attendee still issues the server-side delete`() = runTest {
        coEvery { activityDao.getUnsyncedActivities() } returns listOf(appointment("APT-1"))
        coEvery { apiService.updateActivity("eq.APT-1", any()) } returns
            Response.success(listOf(appointment("APT-1")))
        coEvery { appointmentContactDao.getContactsByAppointmentId("APT-1") } returns emptyList()
        coEvery { apiService.deleteAppointmentContacts("eq.APT-1") } returns Response.success(Unit)

        sync.doSync()

        coVerify(exactly = 1) { apiService.deleteAppointmentContacts("eq.APT-1") }
        coVerify(exactly = 0) { apiService.addAppointmentContacts(any()) }
    }

    // ต้นเหตุถูกแก้แล้ว (แอดมินให้สิทธิ์เพิ่ม) แถวต้องหลุดจากรายการที่จะเตือนตอน logout
    @Test
    fun `a successful sync clears an earlier rejection`() = runTest {
        val customer = Customer(custId = "C-1", companyName = "ลูกค้า", isSynced = false)
        coEvery { customerDao.getUnsyncedCustomers() } returns listOf(customer)
        coEvery { apiService.updateCustomer("eq.C-1", any()) } returns Response.success(listOf(customer))

        sync.doSync()

        coVerify(exactly = 1) { syncRejectionDao.clear("customer", "C-1") }
    }
}
