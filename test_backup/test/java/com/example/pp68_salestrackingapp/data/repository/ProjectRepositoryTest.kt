package com.example.pp68_salestrackingapp.data.repository

import com.example.pp68_salestrackingapp.data.local.ProjectDao
import com.example.pp68_salestrackingapp.data.local.ProjectContactDao
import com.example.pp68_salestrackingapp.data.local.ProjectSalesMemberDao
import com.example.pp68_salestrackingapp.data.local.ContactDao
import com.example.pp68_salestrackingapp.data.model.Project
import com.example.pp68_salestrackingapp.data.model.UserDto
import com.example.pp68_salestrackingapp.data.remote.ApiService
import com.example.pp68_salestrackingapp.data.remote.FirebaseRealtimeService
import com.example.pp68_salestrackingapp.utils.SyncManager
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectRepositoryTest {

    private lateinit var repository: ProjectRepository
    private val apiService      = mockk<ApiService>(relaxed = true)
    private val projectDao      = mockk<ProjectDao>(relaxed = true)
    private val projectContactDao = mockk<ProjectContactDao>(relaxed = true)
    private val projectSalesMemberDao = mockk<ProjectSalesMemberDao>(relaxed = true)
    private val contactDao = mockk<ContactDao>(relaxed = true)
    private val firebaseService = mockk<FirebaseRealtimeService>(relaxed = true)
    private val syncManager = mockk<SyncManager>(relaxed = true)

    private val sampleProject = Project(
        projectId     = "BK6705001",
        custId        = "CST-001",
        projectName   = "คอนโด XT",
        projectStatus = "Quotation",
        progressPct   = 40
    )

    @Before
    fun setup() {
        repository = ProjectRepository(
            apiService,
            projectDao,
            projectContactDao,
            projectSalesMemberDao,
            contactDao,
            firebaseService,
            syncManager
        )
    }

    @Test
    fun `updateProject should calculate correct progressPct for each status`() = runTest {
        val statuses = mapOf(
            "Lead"            to 10,
            "New Project"     to 20,
            "Quotation"       to 40,
            "Bidding"         to 50,
            "Make a Decision" to 70,
            "Assured"         to 80,
            "PO"              to 100
        )
        coEvery { apiService.updateProject(any(), any()) } returns Response.success(listOf(sampleProject))
        
        statuses.forEach { (status, expectedPct) ->
            val project = sampleProject.copy(projectStatus = status)
            repository.updateProject(project)
            coVerify { projectDao.insertProject(match { it.progressPct == expectedPct }) }
        }
    }

    @Test
    fun `getProjectById local hit should not call API`() = runTest {
        coEvery { projectDao.getProjectById("BK6705001") } returns sampleProject

        val result = repository.getProjectById("BK6705001")

        assertTrue(result.isSuccess)
        assertEquals("BK6705001", result.getOrNull()?.projectId)
        coVerify(exactly = 0) { apiService.getProjectById(any()) }
    }

    @Test
    fun `getMembersByBranch success should return list of pairs`() = runTest {
        val users = listOf(
            UserDto(userId = "USR-001", fullName = "สมศรี เซลล์", branchId = "BK-0001", role = "sale"),
            UserDto(userId = "USR-002", fullName = "สมชาย ผจก", branchId = "BK-0001", role = "manager")
        )
        coEvery { apiService.getUsersByBranch("eq.BK-0001") } returns Response.success(users)

        val result = repository.getMembersByBranch("BK-0001")

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull()?.size)
    }

    @Test
    fun `getMembersByBranch error should return failure`() = runTest {
        coEvery { apiService.getUsersByBranch(any()) } returns Response.error(500, "err".toResponseBody())

        val result = repository.getMembersByBranch("BK-0001")

        assertTrue(result.isFailure)
    }
}
