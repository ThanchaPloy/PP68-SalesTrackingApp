package com.example.pp68_salestrackingapp.data.repository

import com.example.pp68_salestrackingapp.data.local.ProjectDao
import com.example.pp68_salestrackingapp.data.model.Branch
import com.example.pp68_salestrackingapp.data.model.Project
import com.example.pp68_salestrackingapp.data.model.ProjectMemberDto
import com.example.pp68_salestrackingapp.data.model.UserDto
import com.example.pp68_salestrackingapp.data.remote.ApiService
import com.example.pp68_salestrackingapp.data.remote.FirebaseRealtimeService
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
class ProjectRepositoryExtendedTest {

    private lateinit var repository: ProjectRepository
    private val apiService      = mockk<ApiService>(relaxed = true)
    private val projectDao      = mockk<ProjectDao>(relaxed = true)
    private val firebaseService = mockk<FirebaseRealtimeService>(relaxed = true)

    private val sampleProject = Project(
        projectId     = "PJ-001",
        custId        = "CST-001",
        projectName   = "คอนโด XT",
        projectStatus = "Quotation",
        progressPct   = 40
    )

    @Before
    fun setup() {
        repository = ProjectRepository(apiService, projectDao, firebaseService)
    }

    // TC-UNIT-PROJ-EXT-01
    @Test
    fun `updateProject should calculate correct progressPct for each status`() = runTest {
        val statuses = mapOf(
            "Lead"            to 10,
            "New Project"     to 20,
            "Quotation"       to 40,
            "Bidding"         to 50,
            "Make a Decision" to 70,
            "Assured"         to 80,
            "PO"              to 100,
            "Lost"            to 0,
            "Failed"          to 0
        )
        coEvery { apiService.updateProject(any(), any()) } returns Response.success(listOf(sampleProject))
        coEvery { firebaseService.updateProjectStatus(any(), any(), any(), any()) } just Runs

        statuses.forEach { (status, expectedPct) ->
            val project = sampleProject.copy(projectStatus = status)
            repository.updateProject(project)
            coVerify { projectDao.insertProject(match { it.progressPct == expectedPct }) }
            clearMocks(projectDao, answers = false)
        }
    }

    // TC-UNIT-PROJ-EXT-02
    @Test
    fun `updateProject API error should return failure`() = runTest {
        coEvery { apiService.updateProject(any(), any()) } returns
                Response.error(400, "error".toResponseBody())

        val result = repository.updateProject(sampleProject)

        assertTrue(result.isFailure)
    }

    // TC-UNIT-PROJ-EXT-03
    @Test
    fun `updateProject success should call Firebase`() = runTest {
        coEvery { apiService.updateProject(any(), any()) } returns Response.success(listOf(sampleProject))
        coEvery { firebaseService.updateProjectStatus(any(), any(), any(), any()) } just Runs

        repository.updateProject(sampleProject)

        coVerify { firebaseService.updateProjectStatus("PJ-001", "Quotation", "คอนโด XT", "") }
    }

    // TC-UNIT-PROJ-EXT-04
    @Test
    fun `getProjectById local hit should not call API`() = runTest {
        coEvery { projectDao.getProjectById("PJ-001") } returns sampleProject

        val result = repository.getProjectById("PJ-001")

        assertTrue(result.isSuccess)
        assertEquals("PJ-001", result.getOrNull()?.projectId)
        coVerify(exactly = 0) { apiService.getProjectById(any()) }
    }

    // TC-UNIT-PROJ-EXT-05
    @Test
    fun `getProjectById local miss should fetch from API`() = runTest {
        coEvery { projectDao.getProjectById("PJ-001") } returns null
        coEvery { apiService.getProjectById("eq.PJ-001") } returns
                Response.success(listOf(sampleProject))

        val result = repository.getProjectById("PJ-001")

        assertTrue(result.isSuccess)
        coVerify { apiService.getProjectById("eq.PJ-001") }
        coVerify { projectDao.insertProject(sampleProject) }
    }

    // TC-UNIT-PROJ-EXT-06
    @Test
    fun `getProjectById not found should return failure`() = runTest {
        coEvery { projectDao.getProjectById("PJ-999") } returns null
        coEvery { apiService.getProjectById(any()) } returns Response.success(emptyList())

        val result = repository.getProjectById("PJ-999")

        assertTrue(result.isFailure)
    }

    // TC-UNIT-PROJ-EXT-07
    @Test
    fun `getBranches success should return list of pairs`() = runTest {
        val branches = listOf(
            Branch("TS-0001", "สาขาสุขาภิบาล 3", "Bangkok"),
            Branch("TP-0001", "ทีมโปรเจค", "Project Team")
        )
        coEvery { apiService.getBranches() } returns Response.success(branches)

        val result = repository.getBranches()

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull()?.size)
        assertEquals("TS-0001" to "สาขาสุขาภิบาล 3", result.getOrNull()?.first())
    }

    // TC-UNIT-PROJ-EXT-08
    @Test
    fun `getBranches API error should return empty list`() = runTest {
        coEvery { apiService.getBranches() } returns Response.error(500, "error".toResponseBody())

        val result = repository.getBranches()

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()?.isEmpty() == true)
    }

    // TC-UNIT-PROJ-EXT-09
    @Test
    fun `getMembersByBranch success should return list of pairs`() = runTest {
        val users = listOf(
            UserDto("USR-001", "สมศรี เซลล์", "TS-0001", "sale", "somsri@company.com", null),
            UserDto("USR-002", "สมชาย ผจก", "TS-0001", "manager", "somchai@company.com", null)
        )
        coEvery { apiService.getUsersByBranch("eq.TS-0001") } returns Response.success(users)

        val result = repository.getMembersByBranch("TS-0001")

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull()?.size)
    }

    // TC-UNIT-PROJ-EXT-10
    @Test
    fun `updateProjectFields success should call Firebase when status changes`() = runTest {
        val fields = mapOf("project_status" to "Bidding")
        coEvery { apiService.updateProject(any(), any()) } returns Response.success(listOf(sampleProject))
        coEvery { projectDao.getProjectById("PJ-001") } returns sampleProject
        coEvery { firebaseService.updateProjectStatus(any(), any(), any(), any()) } just Runs

        val result = repository.updateProjectFields("PJ-001", fields)

        assertTrue(result.isSuccess)
        coVerify { firebaseService.updateProjectStatus("PJ-001", "Bidding", any(), "") }
    }

    // TC-UNIT-PROJ-EXT-11
    @Test
    fun `countProjectsByPrefix should count matching project numbers`() = runTest {
        val projects = listOf(
            sampleProject.copy(projectId = "PJ-001", projectNumber = "TS-26-S002-001"),
            sampleProject.copy(projectId = "PJ-002", projectNumber = "TS-26-S002-002"),
            sampleProject.copy(projectId = "PJ-003", projectNumber = "CM-26-S001-001")
        )
        every { projectDao.getAllProjects() } returns flowOf(projects)

        val result = repository.countProjectsByPrefix("TS-26-S002")

        assertEquals(2, result)
    }

    // TC-UNIT-PROJ-EXT-12
    @Test
    fun `addProjectMembers success should return success`() = runTest {
        coEvery { apiService.addProjectMembers(any()) } returns Response.success(emptyList())

        val result = repository.addProjectMembers("PJ-001", listOf("USR-001", "USR-002"), "support")

        assertTrue(result.isSuccess)
        coVerify { apiService.addProjectMembers(match { it.size == 2 }) }
    }


    // TC-UNIT-PROJ-CREATE-01
    @Test
    fun `createProject success should insert to local DB`() = runTest {
        coEvery { apiService.addProject(any()) } returns Response.success(listOf(sampleProject))
        coEvery { apiService.addProjectMembers(any()) } returns Response.success(emptyList())

        val result = repository.createProject(sampleProject, "USR-001")

        assertTrue(result.isSuccess)
        coVerify { projectDao.insertProject(sampleProject) }
    }

    // TC-UNIT-PROJ-CREATE-02
    @Test
    fun `createProject should send project without projectNumber to API`() = runTest {
        coEvery { apiService.addProject(any()) } returns Response.success(listOf(sampleProject))
        coEvery { apiService.addProjectMembers(any()) } returns Response.success(emptyList())

        repository.createProject(sampleProject, "USR-001")

        coVerify { apiService.addProject(match { it.projectNumber == null }) }
    }

    // TC-UNIT-PROJ-CREATE-03
    @Test
    fun `createProject should add user as owner in project_sales_member`() = runTest {
        coEvery { apiService.addProject(any()) } returns Response.success(listOf(sampleProject))
        coEvery { apiService.addProjectMembers(any()) } returns Response.success(emptyList())

        repository.createProject(sampleProject, "USR-001")

        coVerify {
            apiService.addProjectMembers(match { members ->
                members.any { it.userId == "USR-001" && it.saleRole == "owner" }
            })
        }
    }

    // TC-UNIT-PROJ-CREATE-04
    @Test
    fun `createProject addProject API error should return failure`() = runTest {
        coEvery { apiService.addProject(any()) } returns
                Response.error(400, "error".toResponseBody())

        val result = repository.createProject(sampleProject, "USR-001")

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { projectDao.insertProject(any()) }
    }

    // TC-UNIT-PROJ-CREATE-05
    @Test
    fun `createProject addMembers API error should return failure`() = runTest {
        coEvery { apiService.addProject(any()) } returns Response.success(listOf(sampleProject))
        coEvery { apiService.addProjectMembers(any()) } returns
                Response.error(400, "error".toResponseBody())

        val result = repository.createProject(sampleProject, "USR-001")

        assertTrue(result.isFailure)
    }

    // TC-UNIT-PROJ-CREATE-06
    @Test
    fun `refreshProjects should map projectNumber from memberData`() = runTest {
        val memberData = listOf(
            ProjectMemberDto("PJ-001", "USR-001", "owner", "TS-26-S002-001")
        )
        val projectData = listOf(sampleProject.copy(projectNumber = null))

        coEvery { apiService.getMyProjectIds("eq.USR-001") } returns Response.success(memberData)
        coEvery { apiService.getProjectsByIds(any()) } returns Response.success(projectData)

        val result = repository.refreshProjects("USR-001")

        assertTrue(result.isSuccess)
        coVerify {
            projectDao.clearAndInsert(match { projects ->
                projects.any { it.projectNumber == "TS-26-S002-001" }
            })
        }
    }
}
