package com.example.pp68_salestrackingapp.ui.viewmodels.project

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import com.example.pp68_salestrackingapp.data.model.Customer
import com.example.pp68_salestrackingapp.data.model.Project
import com.example.pp68_salestrackingapp.data.model.ProjectProductDto
import com.example.pp68_salestrackingapp.data.remote.ApiService
import com.example.pp68_salestrackingapp.data.remote.ProductMasterDto
import com.example.pp68_salestrackingapp.data.repository.CustomerRepository
import com.example.pp68_salestrackingapp.data.repository.ProjectRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectInventoryViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val dispatcher = StandardTestDispatcher()
    private val projectRepo = mockk<ProjectRepository>(relaxed = true)
    private val customerRepo = mockk<CustomerRepository>(relaxed = true)
    private val apiService = mockk<ApiService>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init load should populate project company and mapped items`() = runTest {
        coEvery { projectRepo.getProjectById("PRJ-1") } returns Result.success(
            Project(projectId = "PRJ-1", custId = "C1", projectName = "Project A")
        )
        coEvery { customerRepo.getCustomerById("C1") } returns Result.success(
            Customer("C1", "Company A", null, null, null, null, null, null, null)
        )
        coEvery { apiService.getProjectProducts("eq.PRJ-1") } returns Response.success(
            listOf(ProjectProductDto("PRJ-1", "P1", 10.0, null))
        )
        coEvery { apiService.getProductMaster() } returns Response.success(
            listOf(
                ProductMasterDto(
                    productId = "P1",
                    productGroup = "Glass 10mm",
                    productType = "Glass",
                    productSubgroup = null,
                    brand = "Brand",
                    unit = "sqm"
                )
            )
        )

        val vm = ProjectInventoryViewModel(
            SavedStateHandle(mapOf("projectId" to "PRJ-1")),
            projectRepo,
            customerRepo,
            apiService
        )
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isLoading)
        assertEquals("Company A", vm.uiState.value.companyName)
        assertEquals(1, vm.uiState.value.items.size)
        assertEquals("Glass 10mm", vm.uiState.value.items.first().productName)
        assertEquals(10.0, vm.uiState.value.items.first().quantity, 0.0)
    }

    @Test
    fun `init load should handle empty project products`() = runTest {
        coEvery { projectRepo.getProjectById("PRJ-1") } returns Result.success(
            Project(projectId = "PRJ-1", custId = "C1", projectName = "Project A")
        )
        coEvery { customerRepo.getCustomerById("C1") } returns Result.success(
            Customer("C1", "Company A", null, null, null, null, null, null, null)
        )
        coEvery { apiService.getProjectProducts("eq.PRJ-1") } returns Response.success(emptyList())

        val vm = ProjectInventoryViewModel(
            SavedStateHandle(mapOf("projectId" to "PRJ-1")),
            projectRepo,
            customerRepo,
            apiService
        )
        advanceUntilIdle()

        assertTrue(vm.uiState.value.items.isEmpty())
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `load should not run when projectId is missing`() = runTest {
        val vm = ProjectInventoryViewModel(
            SavedStateHandle(),
            projectRepo,
            customerRepo,
            apiService
        )
        advanceUntilIdle()

        assertEquals(null, vm.uiState.value.project)
        assertTrue(vm.uiState.value.items.isEmpty())
    }
}
