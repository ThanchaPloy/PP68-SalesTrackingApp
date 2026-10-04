package com.example.pp68_salestrackingapp.utils

import android.content.Context
import com.example.pp68_salestrackingapp.data.local.*
import com.example.pp68_salestrackingapp.data.model.ActivityResult
import com.example.pp68_salestrackingapp.data.model.Customer
import com.example.pp68_salestrackingapp.data.model.Project
import com.example.pp68_salestrackingapp.data.model.AttachmentOutbox
import com.example.pp68_salestrackingapp.data.remote.ApiService
import com.example.pp68_salestrackingapp.data.remote.UploadApiService
import com.example.pp68_salestrackingapp.data.remote.UploadPhotoResponse
import com.example.pp68_salestrackingapp.di.TokenManager
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import retrofit2.Response
import java.io.File
import java.security.MessageDigest

@OptIn(ExperimentalCoroutinesApi::class)
class SyncManagerTest {

    private val context: Context = mockk(relaxed = true)
    private val apiService: ApiService = mockk(relaxed = true)
    private val uploadApiService: UploadApiService = mockk(relaxed = true)
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
    private val attachmentOutboxDao: AttachmentOutboxDao = mockk(relaxed = true)

    private lateinit var sync: SyncManager

    @Before
    fun setUp() {
        sync = SyncManager(
            context, apiService, uploadApiService, tokenManager, customerDao, projectDao, contactDao,
            activityDao, resultDao, photoDao, appointmentContactDao, planItemDao, projectContactDao,
            syncRejectionDao, attachmentOutboxDao
        )
        coEvery { customerDao.getUnsyncedCustomers() } returns emptyList()
        coEvery { contactDao.getUnsyncedContacts() } returns emptyList()
        coEvery { projectDao.getUnsyncedProjects() } returns emptyList()
        coEvery { activityDao.getUnsyncedActivities() } returns emptyList()
        coEvery { resultDao.getUnsyncedResults() } returns emptyList()
        coEvery { planItemDao.getUnsyncedAppointmentIds() } returns emptyList()
        coEvery { syncRejectionDao.getAll() } returns emptyList()
        coEvery { syncRejectionDao.idsOfType(any()) } returns emptyList()
        every { tokenManager.getLocalDataOwner() } returns null
        coEvery { apiService.deleteProjectContacts(any()) } returns Response.success(Unit)
        coEvery { apiService.addProjectContacts(any()) } returns Response.success(Unit)
    }

    // โปรเจคที่สร้างออฟไลน์ผูกกับลูกค้า TEMP- อยู่ ถ้าไม่ชี้ custId ใหม่ตอนลูกค้าได้ id จริง
    // มันจะถูกอัปขึ้น server พร้อมรหัสลูกค้าที่ไม่มีอยู่จริง และ server ไม่มี FK คอยดักให้
    @Test
    fun `a temp customer getting a real id remaps the projects that referenced it`() = runTest {
        val temp = Customer(custId = "TEMP-ABC123", companyName = "ลูกค้าใหม่", isSynced = false)
        coEvery { customerDao.getUnsyncedCustomers() } returns listOf(temp)
        coEvery { apiService.addCustomer(any(), any()) } returns Response.success(
            listOf(Customer(custId = "C00123", companyName = "ลูกค้าใหม่"))
        )

        sync.doSync()

        coVerify(exactly = 1) {
            customerDao.replaceTemporaryCustomer(
                "TEMP-ABC123",
                match { it.custId == "C00123" && it.isSynced }
            )
        }
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

    @Test
    fun `a project remains unsynced when its contact relation fails`() = runTest {
        val project = Project(projectId = "PRJ-1", projectName = "Project A", isSynced = false)
        coEvery { projectDao.getUnsyncedProjects() } returns listOf(project)
        coEvery { apiService.updateProject(any(), any()) } returns Response.success(listOf(project))
        coEvery { projectContactDao.getContactIdsByProject("PRJ-1") } returns emptyList()
        coEvery { apiService.deleteProjectContacts("eq.PRJ-1") } returns
            Response.error(500, "".toResponseBody(null))

        val result = sync.doSync()

        coVerify(exactly = 1) { projectDao.updateSyncStatus("PRJ-1", false) }
        coVerify(exactly = 0) { projectDao.updateSyncStatus("PRJ-1", true) }
        assertEquals(1, result.failureTypes[SyncFailureType.SERVER])
        assertTrue(result.shouldRetry)
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

        val result = sync.doSync()

        coVerify(exactly = 1) { syncRejectionDao.upsert(match { it.entityId == "C-403" && it.httpCode == 403 }) }
        coVerify(exactly = 0) { syncRejectionDao.upsert(match { it.entityId == "C-500" }) }
        assertEquals(2, result.attempted)
        assertEquals(1, result.permanentFailures)
        assertEquals(1, result.temporaryFailures)
        assertEquals(1, result.failureTypes[SyncFailureType.PERMANENT])
        assertEquals(1, result.failureTypes[SyncFailureType.SERVER])
        assertTrue(result.shouldRetry)
    }

    @Test
    fun `one run uploads at most one hundred rows per entity and reports the rest as skipped`() = runTest {
        val customers = (1..101).map {
            Customer(custId = "C-$it", companyName = "Customer $it", isSynced = false)
        }
        coEvery { customerDao.getUnsyncedCustomers() } returns customers
        coEvery { apiService.updateCustomer(any(), any()) } answers {
            Response.success(listOf(customers.first { it.custId == firstArg<String>().removePrefix("eq.") }))
        }

        val result = sync.doSync()

        coVerify(exactly = 100) { apiService.updateCustomer(any(), any()) }
        assertEquals(100, result.attempted)
        assertEquals(1, result.skipped)
        assertTrue(result.shouldRetry)
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

        val result = sync.doSync()

        coVerify(exactly = 1) { activityDao.updateSyncStatus("APT-1", false) }
        coVerify(exactly = 0) { activityDao.updateSyncStatus("APT-1", true) }
        assertEquals(1, result.failureTypes[SyncFailureType.SERVER])
        assertTrue(result.shouldRetry)
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

    @Test
    fun `a successful result response without a real id stays pending and retries`() = runTest {
        val pending = ActivityResult(
            resultId = "TEMP-RESULT-1",
            projectId = "PRJ-1",
            createdBy = "U1",
            isSynced = false,
            operationId = "result-operation-1"
        )
        coEvery { resultDao.getUnsyncedResults() } returns listOf(pending)
        coEvery { apiService.insertActivityResultMap(any(), "result-operation-1") } returns
            Response.success(emptyList())

        val result = sync.doSync()

        coVerify(exactly = 0) { resultDao.updateSyncStatus("TEMP-RESULT-1", true) }
        coVerify(exactly = 0) { resultDao.replaceTemporaryResult(any(), any()) }
        assertEquals(1, result.failureTypes[SyncFailureType.SERVER])
        assertTrue(result.shouldRetry)
    }

    @Test
    fun `uploaded attachment is persisted as pending bind before metadata call`() = runTest {
        val file = kotlin.io.path.createTempFile("phase2d", ".jpg").toFile().apply {
            writeBytes("camera-photo".toByteArray())
        }
        val item = attachment(file)
        every { tokenManager.getLocalDataOwner() } returns "U1"
        coEvery { attachmentOutboxDao.getPending("U1", any()) } returns listOf(item)
        coEvery { uploadApiService.uploadVisitPhoto(any(), any(), any(), any()) } returns
            Response.success(UploadPhotoResponse("/uploads/photo.jpg"))
        coEvery { apiService.addResultPhotos(any()) } returns
            Response.error(500, "".toResponseBody(null))

        sync.doSync()

        coVerifyOrder {
            uploadApiService.uploadVisitPhoto("attachment-op", item.sha256, any(), any())
            attachmentOutboxDao.markUploaded(
                "attachment-op",
                AttachmentOutbox.STATE_PENDING_BIND,
                "/uploads/photo.jpg",
                any()
            )
            apiService.addResultPhotos(any())
        }
        coVerify(exactly = 0) { attachmentOutboxDao.finalizeBinding(any(), any()) }
        assertTrue(file.exists())
        file.delete()
    }

    @Test
    fun `pending bind retry does not upload binary again and removes file only after bind`() = runTest {
        val file = kotlin.io.path.createTempFile("phase2d", ".jpg").toFile().apply {
            writeBytes("camera-photo".toByteArray())
        }
        val item = attachment(file).copy(
            state = AttachmentOutbox.STATE_PENDING_BIND,
            remoteUrl = "/uploads/photo.jpg"
        )
        every { tokenManager.getLocalDataOwner() } returns "U1"
        coEvery { attachmentOutboxDao.getPending("U1", any()) } returns listOf(item)
        coEvery { apiService.addResultPhotos(any()) } returns Response.success(emptyList())
        coEvery { apiService.updateActivityResult(any(), any()) } returns Response.success(Unit)

        sync.doSync()

        coVerify(exactly = 0) { uploadApiService.uploadVisitPhoto(any(), any(), any(), any()) }
        coVerifyOrder {
            apiService.addResultPhotos(match { it.single().photoUrl == "/uploads/photo.jpg" })
            apiService.updateActivityResult("eq.R1", match { it["photo_url"] == "/uploads/photo.jpg" })
            attachmentOutboxDao.finalizeBinding(item, "/uploads/photo.jpg")
        }
        assertFalse(file.exists())
    }

    @Test
    fun `missing pending file is recorded as a local rejection`() = runTest {
        val missing = File(System.getProperty("java.io.tmpdir"), "missing-${System.nanoTime()}.jpg")
        val item = attachment(missing, knownBytes = "missing".toByteArray())
        every { tokenManager.getLocalDataOwner() } returns "U1"
        coEvery { attachmentOutboxDao.getPending("U1", any()) } returns listOf(item)

        sync.doSync()

        coVerify(exactly = 0) { uploadApiService.uploadVisitPhoto(any(), any(), any(), any()) }
        coVerify {
            syncRejectionDao.upsert(match {
                it.entityType == "attachment" && it.entityId == "attachment-op" && it.httpCode == 0
            })
        }
    }

    private fun attachment(file: File, knownBytes: ByteArray = file.readBytes()): AttachmentOutbox {
        val sha = MessageDigest.getInstance("SHA-256").digest(knownBytes)
            .joinToString("") { "%02x".format(it) }
        return AttachmentOutbox(
            operationId = "attachment-op",
            ownerId = "U1",
            resultId = "R1",
            photoOrder = 0,
            localPath = file.absolutePath,
            mimeType = "image/jpeg",
            sha256 = sha,
            sizeBytes = knownBytes.size.toLong(),
            createdAt = "2026-10-05T00:00:00Z",
            updatedAt = "2026-10-05T00:00:00Z"
        )
    }
}
