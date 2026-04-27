package com.example.pp68_salestrackingapp.data.repository

import com.example.pp68_salestrackingapp.data.local.ActivityDao
import com.example.pp68_salestrackingapp.data.local.CustomerDao
import com.example.pp68_salestrackingapp.data.local.ProjectDao
import com.example.pp68_salestrackingapp.data.model.Customer
import com.example.pp68_salestrackingapp.data.model.Project
import com.example.pp68_salestrackingapp.data.model.SalesActivity
import com.example.pp68_salestrackingapp.data.remote.ApiService
import com.example.pp68_salestrackingapp.data.remote.ProductMasterDto
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
class ProductRepositoryTest {

    private lateinit var repository: ProductRepository
    private val apiService = mockk<ApiService>(relaxed = true)

    private val sampleProducts = listOf(
        ProductMasterDto("PRD-001", "Glass", "Clear Glass", null, "BrandA", "ตร.ม."),
        ProductMasterDto("PRD-002", "Aluminum", "Frame", null, "BrandB", "เส้น"),
        ProductMasterDto("PRD-003", "Hardware", "Hinge", null, null, "ชิ้น")
    )

    @Before
    fun setup() {
        repository = ProductRepository(apiService)
    }

    // TC-UNIT-PROD-01
    @Test
    fun `getAllProducts success should return mapped list`() = runTest {
        coEvery { apiService.getProductMaster() } returns Response.success(sampleProducts)

        val result = repository.getAllProducts()

        assertTrue(result.isSuccess)
        assertEquals(3, result.getOrNull()?.size)
        assertEquals("PRD-001", result.getOrNull()?.first()?.productId)
    }

    // TC-UNIT-PROD-02
    @Test
    fun `getAllProducts should use productGroup as productName`() = runTest {
        coEvery { apiService.getProductMaster() } returns Response.success(sampleProducts)

        val result = repository.getAllProducts()
        val products = result.getOrNull()!!

        assertEquals("Glass", products[0].productName)
    }

    // TC-UNIT-PROD-03
    @Test
    fun `getAllProducts null brand should return default text`() = runTest {
        coEvery { apiService.getProductMaster() } returns Response.success(sampleProducts)

        val result = repository.getAllProducts()
        val products = result.getOrNull()!!

        assertEquals("ไม่ระบุแบรนด์", products[2].brand)
    }

    // TC-UNIT-PROD-04
    @Test
    fun `getAllProducts API error should return failure`() = runTest {
        coEvery { apiService.getProductMaster() } returns Response.error(503, "error".toResponseBody())

        val result = repository.getAllProducts()

        assertTrue(result.isFailure)
    }

    // TC-UNIT-PROD-05
    @Test
    fun `getAllProducts network exception should return failure`() = runTest {
        coEvery { apiService.getProductMaster() } throws Exception("Network error")

        val result = repository.getAllProducts()

        assertTrue(result.isFailure)
    }

    // TC-UNIT-PROD-06
    @Test
    fun `addProductToProject success should return success`() = runTest {
        coEvery { apiService.addProductToProject(any()) } returns Response.success(emptyList())

        val result = repository.addProductToProject("PJ-001", "PRD-001", 10.0, "2026-04-01", "BR-001")

        assertTrue(result.isSuccess)
    }

    // TC-UNIT-PROD-07
    @Test
    fun `addProductToProject API error should return failure`() = runTest {
        coEvery { apiService.addProductToProject(any()) } returns Response.error(400, "error".toResponseBody())

        val result = repository.addProductToProject("PJ-001", "PRD-001", 10.0, null, "BR-001")

        assertTrue(result.isFailure)
    }

    // TC-UNIT-PROD-08
    @Test
    fun `addProductToProject null wantedDate should still call API`() = runTest {
        coEvery { apiService.addProductToProject(any()) } returns Response.success(emptyList())

        val result = repository.addProductToProject("PJ-001", "PRD-001", 5.0, null, "BR-001")

        assertTrue(result.isSuccess)
        coVerify { apiService.addProductToProject(any()) }
    }

    // TC-UNIT-PROD-09
    @Test
    fun `addProductToProject blank wantedDate should treat as null`() = runTest {
        coEvery { apiService.addProductToProject(any()) } returns Response.success(emptyList())

        val result = repository.addProductToProject("PJ-001", "PRD-001", 5.0, "", "BR-001")

        assertTrue(result.isSuccess)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ExportRepositoryTest {

    private lateinit var repository: ExportRepository
    private val activityDao = mockk<ActivityDao>(relaxed = true)
    private val customerDao = mockk<CustomerDao>(relaxed = true)
    private val projectDao  = mockk<ProjectDao>(relaxed = true)

    private val sampleActivities = listOf(
        SalesActivity(
            activityId   = "APT-001",
            userId       = "USR-001",
            customerId   = "CST-001",
            activityType = "onsite",
            activityDate = "2026-03-28",
            status       = "completed",
            detail       = "นำเสนอ Quotation"
        )
    )
    private val sampleCustomers = listOf(
        Customer("CST-001", "บริษัท แสนสิริ", null, "Developer", null, null, null, "customer", null)
    )
    private val sampleProjects = listOf(
        Project(projectId = "PJ-001", custId = "CST-001", projectName = "คอนโด XT")
    )

    @Before
    fun setup() {
        repository = ExportRepository(activityDao, customerDao, projectDao)
    }

    // TC-UNIT-EXPORT-01
    @Test
    fun `generateActivityReportCsv should return CSV with header row`() = runTest {
        every { activityDao.getAllActivities() } returns flowOf(sampleActivities)
        every { customerDao.getAllCustomers() } returns flowOf(sampleCustomers)
        every { projectDao.getAllProjects() } returns flowOf(sampleProjects)

        val result = repository.generateActivityReportCsv()

        assertTrue(result.isSuccess)
        val csv = result.getOrNull()!!
        assertTrue(csv.startsWith("Activity ID,Date,Customer Name"))
    }

    // TC-UNIT-EXPORT-02
    @Test
    fun `generateActivityReportCsv should contain activity data`() = runTest {
        every { activityDao.getAllActivities() } returns flowOf(sampleActivities)
        every { customerDao.getAllCustomers() } returns flowOf(sampleCustomers)
        every { projectDao.getAllProjects() } returns flowOf(sampleProjects)

        val result = repository.generateActivityReportCsv()
        val csv = result.getOrNull()!!

        assertTrue(csv.contains("APT-001"))
        assertTrue(csv.contains("2026-03-28"))
    }

    // TC-UNIT-EXPORT-03
    @Test
    fun `generateActivityReportCsv should map customer name correctly`() = runTest {
        every { activityDao.getAllActivities() } returns flowOf(sampleActivities)
        every { customerDao.getAllCustomers() } returns flowOf(sampleCustomers)
        every { projectDao.getAllProjects() } returns flowOf(sampleProjects)

        val result = repository.generateActivityReportCsv()
        val csv = result.getOrNull()!!

        assertTrue(csv.contains("บริษัท แสนสิริ"))
    }

    // TC-UNIT-EXPORT-04
    @Test
    fun `generateActivityReportCsv unknown customer should show Unknown Customer`() = runTest {
        val activitiesUnknownCust = listOf(
            SalesActivity(
                activityId = "APT-002", userId = "USR-001",
                customerId = "CST-UNKNOWN", activityType = "call",
                activityDate = "2026-03-28", status = "completed"
            )
        )
        every { activityDao.getAllActivities() } returns flowOf(activitiesUnknownCust)
        every { customerDao.getAllCustomers() } returns flowOf(emptyList())
        every { projectDao.getAllProjects() } returns flowOf(emptyList())

        val result = repository.generateActivityReportCsv()
        val csv = result.getOrNull()!!

        assertTrue(csv.contains("Unknown Customer"))
    }

    // TC-UNIT-EXPORT-05
    @Test
    fun `generateActivityReportCsv with empty activities should return header only`() = runTest {
        every { activityDao.getAllActivities() } returns flowOf(emptyList())
        every { customerDao.getAllCustomers() } returns flowOf(emptyList())
        every { projectDao.getAllProjects() } returns flowOf(emptyList())

        val result = repository.generateActivityReportCsv()

        assertTrue(result.isSuccess)
        val lines = result.getOrNull()!!.trim().lines()
        assertEquals(1, lines.size) // header only
    }

    // TC-UNIT-EXPORT-06
    @Test
    fun `generateActivityReportCsv should escape commas in description`() = runTest {
        val activitiesWithComma = listOf(
            SalesActivity(
                activityId = "APT-003", userId = "USR-001",
                customerId = "CST-001", activityType = "onsite",
                activityDate = "2026-03-28", status = "completed",
                detail = "นำเสนอ, ราคา, สินค้า"
            )
        )
        every { activityDao.getAllActivities() } returns flowOf(activitiesWithComma)
        every { customerDao.getAllCustomers() } returns flowOf(sampleCustomers)
        every { projectDao.getAllProjects() } returns flowOf(sampleProjects)

        val result = repository.generateActivityReportCsv()
        val csv = result.getOrNull()!!

        assertTrue(csv.contains("\"นำเสนอ, ราคา, สินค้า\""))
    }
}