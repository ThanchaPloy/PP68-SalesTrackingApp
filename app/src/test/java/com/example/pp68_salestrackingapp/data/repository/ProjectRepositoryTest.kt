package com.example.pp68_salestrackingapp.data.repository

import com.example.pp68_salestrackingapp.data.local.ProjectDao
import com.example.pp68_salestrackingapp.data.model.Project
import com.example.pp68_salestrackingapp.data.model.UserDto
import com.example.pp68_salestrackingapp.data.remote.ApiService
import com.example.pp68_salestrackingapp.data.remote.FirebaseRealtimeService
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectRepositoryExtendedTest {

    private lateinit var repository: ProjectRepository
    private val apiService      = mockk<ApiService>(relaxed = true)
    private val projectDao      = mockk<ProjectDao>(relaxed = true)
    private val firebaseService = mockk<FirebaseRealtimeService>(relaxed = true)

    private val sampleProject = Project(
        projectId     = "BK6705001",
        custId        = "CST-001",
        projectName   = "คอนโด XT",
        projectStatus = "Quotation",
        progressPct   = 40
    )

    @Before
    fun setup() {
        repository = ProjectRepository(apiService, projectDao, firebaseService)
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
            UserDto("USR-001", "สมศรี เซลล์", "BK-0001", "sale", "somsri@company.com", null),
            UserDto("USR-002", "สมชาย ผจก", "BK-0001", "manager", "somchai@company.com", null)
        )
        coEvery { apiService.getUsersByBranch("eq.BK-0001") } returns Response.success(users)

        val result = repository.getMembersByBranch("BK-0001")

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull()?.size)
    }

    @Test
    fun `countProjectsByPrefix should call DAO with correct prefix`() = runTest {
        val prefix = "BK6705"
        coEvery { projectDao.getProjectCountByPrefix(prefix) } returns 5

        val result = repository.countProjectsByPrefix(prefix)

        assertEquals(5, result)
        coVerify { projectDao.getProjectCountByPrefix(prefix) }
    }

    @Test
    fun `createProject should generate ID and insert to local DB`() = runTest {
        val userId = "USR-001"
        val branchId = "BK-001"
        // Mock prefix check for ID generation
        coEvery { projectDao.getProjectCountByPrefix(any()) } returns 0
        coEvery { apiService.addProject(any()) } returns Response.success(listOf(sampleProject))

        val result = repository.createProject(sampleProject.copy(branchId = branchId), userId)

        assertTrue(result.isSuccess)
        val created = result.getOrNull()
        assertNotNull(created)
        // Format: BB YY MM XXX -> BK 67 05 001 (example)
        assertTrue(created!!.projectId.startsWith("BK"))
        assertEquals(9, created.projectId.length) // BK + YY(2) + MM(2) + XXX(3) = 9
        coVerify { projectDao.insertProject(any()) }
    }
}
