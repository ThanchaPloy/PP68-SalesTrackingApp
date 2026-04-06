package com.example.pp68_salestrackingapp.ui.viewmodels.project

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import com.example.pp68_salestrackingapp.data.repository.ProductRepository
import com.example.pp68_salestrackingapp.data.repository.ProductSimpleDto
import io.mockk.coEvery
import io.mockk.coVerify
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

@OptIn(ExperimentalCoroutinesApi::class)
class AddProductViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val dispatcher = StandardTestDispatcher()
    private val productRepo = mockk<ProductRepository>(relaxed = true)

    private fun product(
        id: String = "P1",
        name: String = "Product A",
        brand: String = "Brand A",
        category: String = "Cat",
        subgroup: String = "Sub",
        unit: String = "EA"
    ) = ProductSimpleDto(id, name, brand, category, subgroup, unit)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init loadProducts success should populate products and clear loading`() = runTest {
        coEvery { productRepo.getAllProducts() } returns Result.success(
            listOf(product(), product(id = "P2", name = "Product B"))
        )

        val vm = AddProductViewModel(SavedStateHandle(mapOf("projectId" to "PRJ-1")), productRepo)
        advanceUntilIdle()

        assertEquals(2, vm.uiState.value.products.size)
        assertFalse(vm.uiState.value.isLoading)
        assertEquals(null, vm.uiState.value.error)
    }

    @Test
    fun `init loadProducts failure should set error`() = runTest {
        coEvery { productRepo.getAllProducts() } returns Result.failure(Exception("boom"))

        val vm = AddProductViewModel(SavedStateHandle(mapOf("projectId" to "PRJ-1")), productRepo)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isLoading)
        assertTrue(vm.uiState.value.error?.contains("โหลดสินค้าไม่สำเร็จ") == true)
    }

    @Test
    fun `onBrandSelected should filter names and reset selection fields`() = runTest {
        coEvery { productRepo.getAllProducts() } returns Result.success(
            listOf(
                product(id = "P1", name = "A1", brand = "Brand A"),
                product(id = "P2", name = "A2", brand = "Brand A"),
                product(id = "P3", name = "B1", brand = "Brand B")
            )
        )
        val vm = AddProductViewModel(SavedStateHandle(mapOf("projectId" to "PRJ-1")), productRepo)
        advanceUntilIdle()

        vm.onBrandSelected("Brand A")

        assertEquals("Brand A", vm.uiState.value.selectedBrand)
        assertEquals(listOf("A1", "A2"), vm.uiState.value.filteredNames.sorted())
        assertEquals("", vm.uiState.value.selectedProductName)
        assertEquals("", vm.uiState.value.unit)
    }

    @Test
    fun `onNameSelected should fill group subgroup and unit`() = runTest {
        coEvery { productRepo.getAllProducts() } returns Result.success(
            listOf(product(id = "P1", name = "A1", brand = "Brand A", category = "Glass", subgroup = "Tempered", unit = "sqm"))
        )
        val vm = AddProductViewModel(SavedStateHandle(mapOf("projectId" to "PRJ-1")), productRepo)
        advanceUntilIdle()
        vm.onBrandSelected("Brand A")

        vm.onNameSelected("A1")

        assertEquals("A1", vm.uiState.value.selectedProductName)
        assertEquals("Glass", vm.uiState.value.selectedGroup)
        assertEquals("Tempered", vm.uiState.value.selectedSubgroup)
        assertEquals("sqm", vm.uiState.value.unit)
    }

    @Test
    fun `save should fail when projectId missing`() = runTest {
        coEvery { productRepo.getAllProducts() } returns Result.success(listOf(product()))
        val vm = AddProductViewModel(SavedStateHandle(), productRepo)
        advanceUntilIdle()

        vm.save()

        assertEquals("Error: ไม่พบข้อมูล Project ID", vm.uiState.value.error)
    }

    @Test
    fun `save should fail when selected product invalid`() = runTest {
        coEvery { productRepo.getAllProducts() } returns Result.success(listOf(product()))
        val vm = AddProductViewModel(SavedStateHandle(mapOf("projectId" to "PRJ-1")), productRepo)
        advanceUntilIdle()
        vm.onQuantityChange("10")

        vm.save()

        assertEquals("กรุณาเลือกสินค้าให้ถูกต้อง", vm.uiState.value.error)
    }

    @Test
    fun `save should fail when quantity invalid`() = runTest {
        coEvery { productRepo.getAllProducts() } returns Result.success(listOf(product()))
        val vm = AddProductViewModel(SavedStateHandle(mapOf("projectId" to "PRJ-1")), productRepo)
        advanceUntilIdle()
        vm.onBrandSelected("Brand A")
        vm.onNameSelected("Product A")
        vm.onQuantityChange("0")

        vm.save()

        assertEquals("กรุณาระบุจำนวนที่ถูกต้อง", vm.uiState.value.error)
    }

    @Test
    fun `save success should set isSaved true and call repository`() = runTest {
        coEvery { productRepo.getAllProducts() } returns Result.success(listOf(product()))
        coEvery {
            productRepo.addProductToProject("PRJ-1", "P1", 2.0, "2026-01-01")
        } returns Result.success(Unit)
        val vm = AddProductViewModel(SavedStateHandle(mapOf("projectId" to "PRJ-1")), productRepo)
        advanceUntilIdle()
        vm.onBrandSelected("Brand A")
        vm.onNameSelected("Product A")
        vm.onQuantityChange("2")
        vm.onDateSelected("2026-01-01")

        vm.save()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isSaved)
        assertFalse(vm.uiState.value.isSaving)
        coVerify(exactly = 1) { productRepo.addProductToProject("PRJ-1", "P1", 2.0, "2026-01-01") }
    }

    @Test
    fun `save failure should set error`() = runTest {
        coEvery { productRepo.getAllProducts() } returns Result.success(listOf(product()))
        coEvery { productRepo.addProductToProject(any(), any(), any(), any()) } returns Result.failure(Exception("api"))
        val vm = AddProductViewModel(SavedStateHandle(mapOf("projectId" to "PRJ-1")), productRepo)
        advanceUntilIdle()
        vm.onBrandSelected("Brand A")
        vm.onNameSelected("Product A")
        vm.onQuantityChange("2")

        vm.save()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isSaving)
        assertTrue(vm.uiState.value.error?.contains("บันทึกไม่สำเร็จ") == true)
    }
}
