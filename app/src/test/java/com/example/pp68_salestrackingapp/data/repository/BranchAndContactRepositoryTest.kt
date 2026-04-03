package com.example.pp68_salestrackingapp.data.repository

import com.example.pp68_salestrackingapp.data.local.BranchDao
import com.example.pp68_salestrackingapp.data.local.ContactDao
import com.example.pp68_salestrackingapp.data.model.Branch
import com.example.pp68_salestrackingapp.data.model.ContactPerson
import com.example.pp68_salestrackingapp.data.model.Project
import com.example.pp68_salestrackingapp.data.model.ProjectMemberDto
import com.example.pp68_salestrackingapp.data.remote.ApiService
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class BranchRepositoryTest {

    private lateinit var repository: BranchRepository
    private val dao = mockk<BranchDao>(relaxed = true)
    private val api = mockk<ApiService>(relaxed = true)

    private val sampleBranches = listOf(
        Branch("TS-0001", "สาขาสุขาภิบาล 3 (TS)", "Bangkok"),
        Branch("TP-0001", "ทีมโปรเจค (TP)", "Project Team")
    )

    @Before
    fun setup() {
        repository = BranchRepository(dao, api)
    }

    // TC-UNIT-BRANCH-01
    @Test
    fun `getBranches success should return list`() = runTest {
        coEvery { api.getBranches() } returns Response.success(sampleBranches)

        val result = repository.getBranches()

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull()?.size)
    }

    // TC-UNIT-BRANCH-02
    @Test
    fun `getBranches includes TP-0001 Project Team`() = runTest {
        coEvery { api.getBranches() } returns Response.success(sampleBranches)

        val result = repository.getBranches()
        val branches = result.getOrNull()!!

        assertTrue(branches.any { it.branchId == "TP-0001" })
        assertTrue(branches.any { it.branchName == "ทีมโปรเจค (TP)" })
    }

    // TC-UNIT-BRANCH-03
    @Test
    fun `getBranches API error should return failure`() = runTest {
        coEvery { api.getBranches() } returns Response.error(503, "error".toResponseBody())

        val result = repository.getBranches()

        assertTrue(result.isFailure)
    }

    // TC-UNIT-BRANCH-04
    @Test
    fun `getBranches network exception should return failure`() = runTest {
        coEvery { api.getBranches() } throws Exception("Network error")

        val result = repository.getBranches()

        assertTrue(result.isFailure)
    }

    // TC-UNIT-BRANCH-05
    @Test
    fun `syncFromRemote success should upsert to local DB`() = runTest {
        coEvery { api.getBranches() } returns Response.success(sampleBranches)

        val result = repository.syncFromRemote()

        assertTrue(result.isSuccess)
        coVerify { dao.upsertAll(sampleBranches) }
    }

    // TC-UNIT-BRANCH-06
    @Test
    fun `syncFromRemote API error should return failure`() = runTest {
        coEvery { api.getBranches() } returns Response.error(500, "error".toResponseBody())

        val result = repository.syncFromRemote()

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { dao.upsertAll(any()) }
    }

    // TC-UNIT-BRANCH-07
    @Test
    fun `observeBranches should return from local DB`() = runTest {
        coEvery { dao.getAll() } returns sampleBranches

        val result = repository.observeBranches()

        assertEquals(2, result.size)
        assertEquals("TS-0001", result.first().branchId)
    }

    // TC-UNIT-BRANCH-08
    @Test
    fun `getBranchById should return branch from local DB`() = runTest {
        coEvery { dao.getById("TP-0001") } returns sampleBranches[1]

        val result = repository.getBranchById("TP-0001")

        assertNotNull(result)
        assertEquals("ทีมโปรเจค (TP)", result?.branchName)
    }

    // TC-UNIT-BRANCH-09
    @Test
    fun `getBranchById not found should return null`() = runTest {
        coEvery { dao.getById("UNKNOWN") } returns null

        val result = repository.getBranchById("UNKNOWN")

        assertNull(result)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ContactRepositoryTest {

    private lateinit var repository: ContactRepository
    private val apiService = mockk<ApiService>(relaxed = true)
    private val contactDao = mockk<ContactDao>(relaxed = true)

    private val sampleContact = ContactPerson(
        contactId   = "CON-001",
        custId      = "CST-001",
        fullName    = "คุณวิภาวี ดีพร้อม",
        nickname    = "วิ",
        position    = "ผู้จัดการ",
        phoneNumber = "0812345678",
        email       = "vipa@example.com",
        line        = "vipa_line"
    )

    @Before
    fun setup() {
        repository = ContactRepository(apiService, contactDao)
    }

    // TC-UNIT-CONT-01
    @Test
    fun `refreshContacts success should clear and insert`() = runTest {
        val members  = listOf(ProjectMemberDto("PJ-001", "USR-001", "owner", "TS-26-S001-001"))
        val projects = listOf(Project(projectId = "PJ-001", custId = "CST-001", projectName = "P1"))
        val contacts = listOf(sampleContact)

        coEvery { apiService.getMyProjectIds("eq.USR-001") } returns Response.success(members)
        coEvery { apiService.getProjectsByIds(any()) } returns Response.success(projects)
        coEvery { apiService.getContactsByCustomerIds(any()) } returns Response.success(contacts)

        val result = repository.refreshContacts("USR-001")

        assertTrue(result.isSuccess)
        coVerify { contactDao.clearAndInsert(contacts) }
    }

    // TC-UNIT-CONT-02
    @Test
    fun `refreshContacts no projects should return success without inserting`() = runTest {
        coEvery { apiService.getMyProjectIds(any()) } returns Response.success(emptyList())

        val result = repository.refreshContacts("USR-001")

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { contactDao.clearAndInsert(any()) }
    }

    // TC-UNIT-CONT-03
    @Test
    fun `refreshContacts network error should return failure`() = runTest {
        coEvery { apiService.getMyProjectIds(any()) } throws Exception("Network error")

        val result = repository.refreshContacts("USR-001")

        assertTrue(result.isFailure)
    }

    // TC-UNIT-CONT-04
    @Test
    fun `addContact success should insert to local DB`() = runTest {
        coEvery { apiService.addContact(any()) } returns Response.success(listOf(sampleContact))

        val result = repository.addContact(sampleContact)

        assertTrue(result.isSuccess)
        coVerify { contactDao.insertAll(listOf(sampleContact)) }
    }

    // TC-UNIT-CONT-05
    @Test
    fun `addContact API error should return failure`() = runTest {
        coEvery { apiService.addContact(any()) } returns Response.error(400, "error".toResponseBody())

        val result = repository.addContact(sampleContact)

        assertTrue(result.isFailure)
    }

    // TC-UNIT-CONT-06
    @Test
    fun `addContact network exception should return failure`() = runTest {
        coEvery { apiService.addContact(any()) } throws Exception("Network error")

        val result = repository.addContact(sampleContact)

        assertTrue(result.isFailure)
    }
}
