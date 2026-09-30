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
    private val networkMonitor: com.example.pp68_salestrackingapp.utils.NetworkMonitor = mockk(relaxed = true)
    private val context: android.content.Context = mockk(relaxed = true)

    private lateinit var repo: ActivityRepository

    private val activity = SalesActivity(
        activityId = "A1",
        userId = "U1",
        customerId = "C1",
        projectId = "PRJ-1",
        activityType = "onsite",
        activityDate = java.time.LocalDate.now().toString(),
        status = "planned"
    )

    @Before
    fun setUp() {
        repo = ActivityRepository(
            apiService, uploadApiService, activityDao, projectDao, customerDao, contactDao,
            planItemDao, resultDao, photoDao, appointmentContactDao, projectRepo, syncManager,
            networkMonitor, context
        )
        coEvery { activityDao.getActivityById("A1") } returns activity
    }

    // เขียนลงเครื่องเป็น is_synced = false ก่อนเสมอ แล้วค่อยให้ผลจาก server มาปลดเป็น true
    // (เดิมเขียนทีเดียวหลังยิง API — ถ้าเขียนพังตรงนั้นจะไม่เหลืออะไรในเครื่องเลย)
    @Test
    fun `a check-in the server accepted is stored as synced`() = runTest {
        coEvery { apiService.updateActivity(any(), any()) } returns
            Response.success(listOf(activity))

        repo.checkIn("A1", 13.7563, 100.5018, isVerified = true, distanceDeviation = 12.0)

        val saved = slot<SalesActivity>()
        coVerify { activityDao.insertActivity(capture(saved)) }
        assertFalse(saved.captured.isSynced)
        coVerify(exactly = 1) { activityDao.updateSyncStatus("A1", true) }
    }

    // ── การโกหกว่าสำเร็จ: catch ครอบทั้งฟังก์ชันแล้วคืน success ทั้งที่ยังไม่ได้เขียนอะไรลงเครื่อง ──
    // เป็นบั๊กที่ร้ายที่สุดในกลุ่มนี้ เพราะผู้ใช้เห็นว่าบันทึกแล้ว แต่ไม่มีทั้งในเครื่องและบน server
    // และ outbox ก็ไม่มีแถวอะไรให้ตามส่ง = หายถาวรโดยไม่มีใครรู้

    @Test
    fun `a check-in that cannot be written locally is reported as failure`() = runTest {
        coEvery { activityDao.insertActivity(any()) } throws RuntimeException("disk full")

        val result = repo.checkIn("A1", 13.7563, 100.5018, isVerified = true, distanceDeviation = 12.0)

        assertTrue(result.isFailure)
        // ยังไม่ทันเขียนลงเครื่อง ห้ามยิงขึ้น server ให้ข้อมูลสองฝั่งต่างกัน
        coVerify(exactly = 0) { apiService.updateActivity(any(), any()) }
    }

    // `updates["plan_status"] as String` ไม่มีอะไรการันตีชนิด — ส่ง Int มาคือ ClassCastException
    @Test
    fun `an update carrying a wrongly typed value is reported as failure`() = runTest {
        val result = repo.updateActivity("A1", mapOf("plan_status" to 42))

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { apiService.updateActivity(any(), any()) }
    }

    @Test
    fun `finishing an appointment whose checklist write fails is reported as failure`() = runTest {
        coEvery { planItemDao.insertPlanItems(any()) } throws RuntimeException("db locked")

        val result = repo.finishActivity("A1", listOf(1, 2), "สรุปการเข้าพบ")

        assertTrue(result.isFailure)
        // เดิม catch จะเขียนสถานะ completed ทับให้ทั้งที่ติ๊ก checklist หายไปแล้ว แล้วคืน success
        coVerify(exactly = 0) { activityDao.insertActivity(any()) }
        coVerify(exactly = 0) { apiService.updateActivity(any(), any()) }
    }

    // ── "กดเซฟแล้วต้องแน่ใจว่าขึ้น server จริง" ────────────────────────────────────────────
    // HTTP 500 ตัวเดียวกัน แต่ผลต่างกันตามว่ามีเน็ตหรือไม่ นี่คือสิ่งที่แยกสองเคสที่เคยถูกกลบรวมกัน:
    // "ออฟไลน์ ค่อยส่งทีหลัง" (ถูกต้อง) กับ "เน็ตดีแต่ส่งไม่ขึ้น" (ต้องบอก ไม่ใช่เงียบ)

    @Test
    fun `a check-in the server rejected while offline stays queued for retry`() = runTest {
        every { networkMonitor.isOnline() } returns false
        coEvery { apiService.updateActivity(any(), any()) } returns
            Response.error(500, "boom".toResponseBody("text/plain".toMediaType()))

        val result = repo.checkIn("A1", 13.7563, 100.5018, isVerified = true, distanceDeviation = 12.0)

        assertTrue(result.isSuccess)
        val saved = slot<SalesActivity>()
        coVerify { activityDao.insertActivity(capture(saved)) }
        assertFalse(saved.captured.isSynced)
        verify { syncManager.scheduleSync() }
    }

    @Test
    fun `a check-in the server rejected while online is reported as failure`() = runTest {
        every { networkMonitor.isOnline() } returns true
        coEvery { apiService.updateActivity(any(), any()) } returns
            Response.error(500, "boom".toResponseBody("text/plain".toMediaType()))

        val result = repo.checkIn("A1", 13.7563, 100.5018, isVerified = true, distanceDeviation = 12.0)

        assertTrue(result.isFailure)
        // ยังต้องเก็บไว้ในเครื่องแบบ unsynced เหมือนเดิม ผู้ใช้จะได้ไม่เสียข้อมูลที่กรอกไป
        val saved = slot<SalesActivity>()
        coVerify { activityDao.insertActivity(capture(saved)) }
        assertFalse(saved.captured.isSynced)
    }

    // เน็ตหลุดกลางทาง (IOException) ตอนที่ระบบยังเห็นว่าออนไลน์ ก็ต้องถือว่าเซฟไม่สำเร็จ
    @Test
    fun `a dropped connection while online is reported as failure`() = runTest {
        every { networkMonitor.isOnline() } returns true
        coEvery { apiService.updateActivity(any(), any()) } throws java.io.IOException("timeout")

        val result = repo.checkIn("A1", 13.7563, 100.5018, isVerified = true, distanceDeviation = 12.0)

        assertTrue(result.isFailure)
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

    // ✅ 403 คือ server ปฏิเสธถาวร ไม่ใช่ error ชั่วคราวแบบ 500 — ต้องบอกผู้ใช้ตรง ๆ (Result.failure)
    // ไม่ใช่ปักธง success ปลอมแล้วปล่อยให้ outbox ลองซ้ำเงียบ ๆ ไปตลอดกาลเหมือน error อื่น
    @Test
    fun `a 403 check-in is reported as failure and blocked from retry, not queued`() = runTest {
        coEvery { apiService.updateActivity(any(), any()) } returns
            Response.error(403, "forbidden".toResponseBody("text/plain".toMediaType()))

        val result = repo.checkIn("A1", 13.7563, 100.5018, isVerified = true, distanceDeviation = 12.0)

        assertTrue(result.isFailure)
        val saved = slot<SalesActivity>()
        coVerify { activityDao.insertActivity(capture(saved)) }
        assertFalse(saved.captured.isSynced)
        verify { syncManager.markBlocked("activity", "A1") }
        verify(exactly = 0) { syncManager.scheduleSync() }
    }

    // activity_plan_item / appointment_contact ไม่มี FK CASCADE ฝั่ง Room — ถ้าไม่ลบตามเอง
    // แถวลูกจะค้างเป็นขยะกำพร้า และ checklist ที่ยัง is_synced = 0 จะบล็อก logout ถาวรแม้เน็ตดี
    @Test
    fun `deleting an appointment also clears its checklist and attendee rows`() = runTest {
        val future = java.time.LocalDate.now().plusDays(30).toString()
        coEvery { activityDao.getActivityById("A2") } returns
            activity.copy(activityId = "A2", activityDate = future)
        coEvery { apiService.deleteActivity(any()) } returns Response.success(Unit)

        val result = repo.deleteActivity("A2")

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { activityDao.deleteActivityById("A2") }
        coVerify(exactly = 1) { planItemDao.deletePlanItemsByAppointmentId("A2") }
        coVerify(exactly = 1) { appointmentContactDao.deleteContactsByAppointmentId("A2") }
    }

    // นัดที่สร้างตอนออฟไลน์ (TEMP-) ลบในเครื่องล้วน ต้องเก็บกวาดแถวลูกเหมือนกัน
    @Test
    fun `deleting a never-synced appointment clears its child rows too`() = runTest {
        coEvery { activityDao.getActivityById("TEMP-X") } returns null

        val result = repo.deleteActivity("TEMP-X")

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { planItemDao.deletePlanItemsByAppointmentId("TEMP-X") }
        coVerify(exactly = 1) { appointmentContactDao.deleteContactsByAppointmentId("TEMP-X") }
        // ห้ามยิง API ลบสำหรับ id ที่ server ไม่เคยรู้จัก
        coVerify(exactly = 0) { apiService.deleteActivity(any()) }
    }

    // แผนที่ขาดนัดคือหลักฐานว่าเกิดอะไรขึ้น — เดิมลบไม่ได้แต่แก้ได้ จึงเลี่ยงลบด้วยการ
    // เลื่อนวันนัดไปอนาคต สถานะก็กลับเป็น planned เหมือนไม่เคยขาด
    @Test
    fun `editing an appointment that was already missed is refused`() = runTest {
        val yesterday = java.time.LocalDate.now().minusDays(1).toString()
        coEvery { activityDao.getActivityById("A-MISSED") } returns
            activity.copy(activityId = "A-MISSED", activityDate = yesterday, status = "planned")

        val result = repo.updateActivity("A-MISSED", mapOf("planned_date" to "2030-01-01"))

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { apiService.updateActivity(any(), any()) }
    }

    // การบันทึกผลต้องทำได้เสมอ — การผูกโครงการตอนบันทึกผลไม่ใช่การแก้แผน
    // ถ้าข้อนี้พัง = พนักงานบันทึกผลนัดที่ขาดไปไม่ได้เลย ซึ่งตรงข้ามกับกติกาที่ตั้งไว้
    @Test
    fun `linking a project from the result flow is allowed on a missed appointment`() = runTest {
        val yesterday = java.time.LocalDate.now().minusDays(1).toString()
        coEvery { activityDao.getActivityById("A-MISSED") } returns
            activity.copy(activityId = "A-MISSED", activityDate = yesterday, status = "planned")
        coEvery { apiService.updateActivity(any(), any()) } returns
            Response.success(listOf(activity))

        val result = repo.updateActivity(
            "A-MISSED", mapOf("project_code" to "PRJ-9"), isPlanEdit = false
        )

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { apiService.updateActivity(any(), any()) }
    }

    // online/call ไม่มีสถานะขาดนัด จึงแก้ย้อนหลังได้ตามเดิม
    @Test
    fun `editing a past online appointment is still allowed`() = runTest {
        val yesterday = java.time.LocalDate.now().minusDays(1).toString()
        coEvery { activityDao.getActivityById("A-ONLINE") } returns
            activity.copy(activityId = "A-ONLINE", activityDate = yesterday, activityType = "online")
        coEvery { apiService.updateActivity(any(), any()) } returns
            Response.success(listOf(activity))

        val result = repo.updateActivity("A-ONLINE", mapOf("topic" to "แก้หัวข้อ"))

        assertTrue(result.isSuccess)
    }
}
