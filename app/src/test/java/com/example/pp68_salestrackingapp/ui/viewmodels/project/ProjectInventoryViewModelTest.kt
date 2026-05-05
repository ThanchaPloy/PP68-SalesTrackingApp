package com.example.pp68_salestrackingapp.ui.viewmodels.project

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import com.example.pp68_salestrackingapp.data.model.Customer
import com.example.pp68_salestrackingapp.data.model.Project
import com.example.pp68_salestrackingapp.data.model.ProjectProductDto
import com.example.pp68_salestrackingapp.data.remote.ApiService
import com.example.pp68_salestrackingapp.data.remote.ProductMasterDto
import com.example.pp68_salestrackingapp.data.repository.BranchRepository
import com.example.pp68_salestrackingapp.data.repository.CustomerRepository
import com.example.pp68_salestrackingapp.data.repository.ProjectRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectInventoryViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val dispatcher = StandardTestDispatcher()
    private val projectRepo  = mockk<ProjectRepository>(relaxed = true)
    private val customerRepo = mockk<CustomerRepository>(relaxed = true)
    private val branchRepo   = mockk<BranchRepository>(relaxed = true)   // ✅ เพิ่ม
    private val apiService   = mockk<ApiService>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── helper สร้าง VM ให้ส่ง argument ครบทุกครั้ง ────────────────────
    private fun buildVm(projectId: String? = null): ProjectInventoryViewModel {
        val handle = if (projectId != null)
            SavedStateHandle(mapOf("projectId" to projectId))
        else
            SavedStateHandle()
        return ProjectInventoryViewModel(handle, projectRepo, customerRepo, branchRepo, apiService)
    }

    // ── helper สร้าง ProductMasterDto พร้อม default ─────────────────────
    private fun productMasterDto(
        productId: String,
        productGroup: String? = null,
        productType: String? = null,
        productSubgroup: String? = null,
        brand: String? = null,
        unit: String? = null,
        color: String? = null,
        thickness: String? = null,
        width: String? = null,
        length: String? = null,
        dimensionUnit: String? = null
    ) = ProductMasterDto(
        productId      = productId,
        productGroup   = productGroup,
        productType    = productType,
        productSubgroup = productSubgroup,
        brand          = brand,
        unit           = unit,
        color          = color,
        thickness      = thickness,
        width          = width,
        length         = length,
        dimensionUnit  = dimensionUnit
    )

    // ── helper สร้าง ProjectProductDto พร้อม default ─────────────────────
    private fun projectProductDto(
        projectId: String,
        productId: String,
        quantity: Double? = null,
        desiredDate: String? = null,
        shippingBranchId: String? = null
    ) = ProjectProductDto(
        projectId        = projectId,
        productId        = productId,
        quantity         = quantity,
        desiredDate      = desiredDate,
        shippingBranchId = shippingBranchId
    )

    @Test
    fun givenInit_whenLoadSuccess_thenPopulatesProjectCompanyAndMappedItems() = runTest {
        coEvery { projectRepo.getProjectById("PRJ-1") } returns Result.success(
            Project(projectId = "PRJ-1", custId = "C1", projectName = "Project A")
        )
        coEvery { customerRepo.getCustomerById("C1") } returns Result.success(
            Customer("C1", "Company A", null, null, null, null, null, null, null)
        )
        coEvery { apiService.getProjectProducts("eq.PRJ-1") } returns Response.success(
            listOf(projectProductDto("PRJ-1", "P1", quantity = 10.0))
        )
        coEvery { apiService.getProductMaster() } returns Response.success(
            listOf(productMasterDto("P1", productGroup = "Glass 10mm", productType = "Glass", unit = "sqm"))
        )

        val vm = buildVm("PRJ-1")
        runCurrent()
        assertTrue(vm.uiState.value.isLoading)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isLoading)
        assertEquals("Company A", vm.uiState.value.companyName)
        assertEquals(1, vm.uiState.value.items.size)
        assertEquals("Glass 10mm", vm.uiState.value.items.first().productName)
        assertEquals(10.0, vm.uiState.value.items.first().quantity, 0.0)
    }

    @Test
    fun givenProjectProductsEmpty_whenLoad_thenItemsRemainEmpty() = runTest {
        coEvery { projectRepo.getProjectById("PRJ-1") } returns Result.success(
            Project(projectId = "PRJ-1", custId = "C1", projectName = "Project A")
        )
        coEvery { customerRepo.getCustomerById("C1") } returns Result.success(
            Customer("C1", "Company A", null, null, null, null, null, null, null)
        )
        coEvery { apiService.getProjectProducts("eq.PRJ-1") } returns Response.success(emptyList())

        val vm = buildVm("PRJ-1")
        advanceUntilIdle()

        assertTrue(vm.uiState.value.items.isEmpty())
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun givenMissingProjectId_whenInitThenLoad_thenNoRepositoryCallsAndDefaultState() = runTest {
        val vm = buildVm()
        advanceUntilIdle()

        assertEquals(null, vm.uiState.value.project)
        assertTrue(vm.uiState.value.items.isEmpty())
        assertFalse(vm.uiState.value.isLoading)
        assertNull(vm.uiState.value.error)
        coVerify(exactly = 0) { projectRepo.getProjectById(any()) }
        coVerify(exactly = 0) { customerRepo.getCustomerById(any()) }
        coVerify(exactly = 0) { apiService.getProjectProducts(any()) }
    }

    @Test
    fun givenProjectRepositoryThrows_whenLoad_thenSetsErrorAndStopsLoading() = runTest {
        coEvery { projectRepo.getProjectById("PRJ-1") } throws RuntimeException("boom")

        val vm = buildVm("PRJ-1")
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isLoading)
        assertEquals("boom", vm.uiState.value.error)
    }

    @Test
    fun givenProjectLookupFailureResult_whenLoad_thenProjectAndCompanyDefaultAndNoError() = runTest {
        coEvery { projectRepo.getProjectById("PRJ-1") } returns Result.failure(Exception("project missing"))
        coEvery { apiService.getProjectProducts("eq.PRJ-1") } returns Response.success(emptyList())

        val vm = buildVm("PRJ-1")
        advanceUntilIdle()

        assertNull(vm.uiState.value.project)
        assertEquals("", vm.uiState.value.companyName)
        assertTrue(vm.uiState.value.items.isEmpty())
        assertFalse(vm.uiState.value.isLoading)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun givenCustomerLookupFailure_whenLoad_thenCompanyNameDefaultsToBlank() = runTest {
        coEvery { projectRepo.getProjectById("PRJ-1") } returns Result.success(
            Project(projectId = "PRJ-1", custId = "C404", projectName = "Project A")
        )
        coEvery { customerRepo.getCustomerById("C404") } returns Result.failure(Exception("not found"))
        coEvery { apiService.getProjectProducts("eq.PRJ-1") } returns Response.success(emptyList())

        val vm = buildVm("PRJ-1")
        advanceUntilIdle()

        assertEquals("", vm.uiState.value.companyName)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun givenProjectProductsApiUnsuccessful_whenLoad_thenReturnsEmptyItems() = runTest {
        coEvery { projectRepo.getProjectById("PRJ-1") } returns Result.success(
            Project(projectId = "PRJ-1", custId = "C1", projectName = "Project A")
        )
        coEvery { customerRepo.getCustomerById("C1") } returns Result.success(
            Customer("C1", "Company A", null, null, null, null, null, null, null)
        )
        coEvery { apiService.getProjectProducts("eq.PRJ-1") } returns Response.error(
            500, "err".toResponseBody("text/plain".toMediaType())
        )

        val vm = buildVm("PRJ-1")
        advanceUntilIdle()

        assertTrue(vm.uiState.value.items.isEmpty())
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun givenProductMasterMissingAndNullFields_whenLoad_thenIgnoresUnknownAndAppliesDefaults() = runTest {
        coEvery { projectRepo.getProjectById("PRJ-1") } returns Result.success(
            Project(projectId = "PRJ-1", custId = "C1", projectName = "Project A")
        )
        coEvery { customerRepo.getCustomerById("C1") } returns Result.success(
            Customer("C1", "Company A", null, null, null, null, null, null, null)
        )
        coEvery { apiService.getProjectProducts("eq.PRJ-1") } returns Response.success(
            listOf(
                projectProductDto("PRJ-1", "P1", quantity = null),
                projectProductDto("PRJ-1", "UNKNOWN", quantity = 5.0)
            )
        )
        coEvery { apiService.getProductMaster() } returns Response.success(
            listOf(productMasterDto("P1"))
        )

        val vm = buildVm("PRJ-1")
        advanceUntilIdle()

        val items = vm.uiState.value.items
        assertEquals(1, items.size)
        assertEquals("P1", items.first().productName)
        assertEquals("ทั่วไป", items.first().category)
        assertEquals(0.0, items.first().quantity, 0.0)
        assertEquals("ชิ้น", items.first().unit)
    }

    @Test
    fun givenGetProductMasterResponseBodyNull_whenLoad_thenMapsToEmptyItems() = runTest {
        coEvery { projectRepo.getProjectById("PRJ-1") } returns Result.success(
            Project(projectId = "PRJ-1", custId = "C1", projectName = "Project A")
        )
        coEvery { customerRepo.getCustomerById("C1") } returns Result.success(
            Customer("C1", "Company A", null, null, null, null, null, null, null)
        )
        coEvery { apiService.getProjectProducts("eq.PRJ-1") } returns Response.success(
            listOf(projectProductDto("PRJ-1", "P1", quantity = 1.0))
        )
        coEvery { apiService.getProductMaster() } returns Response.success(null)

        val vm = buildVm("PRJ-1")
        advanceUntilIdle()

        assertTrue(vm.uiState.value.items.isEmpty())
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun givenFetchProjectProductsThrows_whenLoad_thenReturnsEmptyItemsWithoutCrash() = runTest {
        coEvery { projectRepo.getProjectById("PRJ-1") } returns Result.success(
            Project(projectId = "PRJ-1", custId = "C1", projectName = "Project A")
        )
        coEvery { customerRepo.getCustomerById("C1") } returns Result.success(
            Customer("C1", "Company A", null, null, null, null, null, null, null)
        )
        coEvery { apiService.getProjectProducts("eq.PRJ-1") } throws RuntimeException("network down")

        val vm = buildVm("PRJ-1")
        advanceUntilIdle()

        assertTrue(vm.uiState.value.items.isEmpty())
        assertFalse(vm.uiState.value.isLoading)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun givenRetryAfterInitialFailure_whenLoadAgain_thenClearsErrorAndLoadsData() = runTest {
        coEvery { projectRepo.getProjectById("PRJ-1") } throws RuntimeException("first boom") andThen Result.success(
            Project(projectId = "PRJ-1", custId = "C1", projectName = "Project A")
        )
        coEvery { customerRepo.getCustomerById("C1") } returns Result.success(
            Customer("C1", "Company A", null, null, null, null, null, null, null)
        )
        coEvery { apiService.getProjectProducts("eq.PRJ-1") } returns Response.success(emptyList())

        val vm = buildVm("PRJ-1")
        advanceUntilIdle()
        assertEquals("first boom", vm.uiState.value.error)

        vm.load()
        advanceUntilIdle()

        assertNull(vm.uiState.value.error)
        assertEquals("Project A", vm.uiState.value.project?.projectName)
        coVerify(atLeast = 2) { projectRepo.getProjectById("PRJ-1") }
    }
}