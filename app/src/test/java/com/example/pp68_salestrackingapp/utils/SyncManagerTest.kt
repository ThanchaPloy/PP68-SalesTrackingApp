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

    private lateinit var sync: SyncManager

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
}
