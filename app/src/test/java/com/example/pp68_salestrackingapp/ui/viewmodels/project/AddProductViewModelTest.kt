package com.example.pp68_salestrackingapp.ui.viewmodels.project

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import com.example.pp68_salestrackingapp.data.repository.BranchRepository
import com.example.pp68_salestrackingapp.data.repository.ProductRepository
import com.example.pp68_salestrackingapp.data.repository.ProductSimpleDto
import com.example.pp68_salestrackingapp.data.remote.ApiService
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

@OptIn(ExperimentalCoroutinesApi::class)
class AddProductViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val dispatcher = StandardTestDispatcher()
    private val productRepo = mockk<ProductRepository>(relaxed = true)
    private val branchRepo = mockk<BranchRepository>(relaxed = true)
    private val apiService = mockk<ApiService>(relaxed = true)
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
    fun givenInit_whenLoadProductsSuccess_thenUpdatesProductsAndLoadingState() = runTest {
        coEvery { productRepo.getAllProducts() } returns Result.success(
            listOf(product(), product(id = "P2", name = "Product B"))
        )

        val vm = AddProductViewModel(SavedStateHandle(mapOf("projectId" to "PRJ-1")), productRepo, branchRepo, apiService)
        advanceUntilIdle()

        assertEquals(2, vm.uiState.value.products.size)
        assertFalse(vm.uiState.value.isLoading)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun givenInit_whenLoadProductsFails_thenSetsErrorAndStopsLoading() = runTest {
        coEvery { productRepo.getAllProducts() } returns Result.failure(Exception("boom"))

        val vm = AddProductViewModel(SavedStateHandle(mapOf("projectId" to "PRJ-1")), productRepo, branchRepo, apiService)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isLoading)
        assertTrue(vm.uiState.value.error?.contains("โหลดสินค้าไม่สำเร็จ") == true)
    }

    @Test
    fun givenBrandSelected_whenCalled_thenFiltersNamesDistinctAndResetsDependentFields() = runTest {
        coEvery { productRepo.getAllProducts() } returns Result.success(
            listOf(
                product(id = "P1", name = "A1", brand = "Brand A"),
                product(id = "P2", name = "A2", brand = "Brand A"),
                product(id = "P3", name = "A1", brand = "Brand A"),
                product(id = "P4", name = "B1", brand = "Brand B")
            )
        )
        val vm = AddProductViewModel(SavedStateHandle(mapOf("projectId" to "PRJ-1")), productRepo, branchRepo, apiService)
        advanceUntilIdle()
        vm.onNameSelected("B1")
        vm.onQuantityChange("9")
        vm.onDateSelected("2026-01-01")

        // Given-When
        vm.onBrandSelected("Brand A")

        // Then
        assertEquals("Brand A", vm.uiState.value.selectedBrand)
        assertEquals(listOf("A1", "A2"), vm.uiState.value.filteredNames.sorted())
        assertEquals("", vm.uiState.value.selectedProductName)
        assertEquals("", vm.uiState.value.selectedGroup)
        assertEquals("", vm.uiState.value.selectedSubgroup)
        assertEquals("", vm.uiState.value.unit)
        assertEquals("9", vm.uiState.value.quantity)
        assertEquals("2026-01-01", vm.uiState.value.wantedDate)
    }

    @Test
    fun givenNameSelected_whenMatchingSelectedBrand_thenFillsGroupSubgroupAndUnit() = runTest {
        coEvery { productRepo.getAllProducts() } returns Result.success(
            listOf(product(id = "P1", name = "A1", brand = "Brand A", category = "Glass", subgroup = "Tempered", unit = "sqm"))
        )
        val vm = AddProductViewModel(SavedStateHandle(mapOf("projectId" to "PRJ-1")), productRepo, branchRepo, apiService)
        advanceUntilIdle()
        vm.onBrandSelected("Brand A")

        vm.onNameSelected("A1")

        assertEquals("A1", vm.uiState.value.selectedProductName)
        assertEquals("Glass", vm.uiState.value.selectedGroup)
        assertEquals("Tempered", vm.uiState.value.selectedSubgroup)
        assertEquals("sqm", vm.uiState.value.unit)
    }

    @Test
    fun givenNameSelected_whenBrandBlank_thenFindsByNameAndFillsFields() = runTest {
        coEvery { productRepo.getAllProducts() } returns Result.success(
            listOf(product(id = "P1", name = "Shared", brand = "Brand A", category = "CatA", subgroup = "SubA", unit = "kg"))
        )
        val vm = AddProductViewModel(SavedStateHandle(mapOf("projectId" to "PRJ-1")), productRepo, branchRepo, apiService)
        advanceUntilIdle()

        vm.onNameSelected("Shared")

        assertEquals("Shared", vm.uiState.value.selectedProductName)
        assertEquals("CatA", vm.uiState.value.selectedGroup)
        assertEquals("SubA", vm.uiState.value.selectedSubgroup)
        assertEquals("kg", vm.uiState.value.unit)
    }

    @Test
    fun givenNameSelected_whenNoMatchingProduct_thenClearsDerivedFields() = runTest {
        coEvery { productRepo.getAllProducts() } returns Result.success(
            listOf(product(id = "P1", name = "A1", brand = "Brand A", category = "CatA", subgroup = "SubA", unit = "kg"))
        )
        val vm = AddProductViewModel(SavedStateHandle(mapOf("projectId" to "PRJ-1")), productRepo, branchRepo, apiService)
        advanceUntilIdle()
        vm.onBrandSelected("Brand B")

        vm.onNameSelected("A1")

        assertEquals("A1", vm.uiState.value.selectedProductName)
        assertEquals("", vm.uiState.value.selectedGroup)
        assertEquals("", vm.uiState.value.selectedSubgroup)
        assertEquals("", vm.uiState.value.unit)
    }

    @Test
    fun givenQuantityAndDateChanges_whenCalled_thenUpdatesFieldsIncludingBlankDateToNull() = runTest {
        coEvery { productRepo.getAllProducts() } returns Result.success(emptyList())
        val vm = AddProductViewModel(SavedStateHandle(mapOf("projectId" to "PRJ-1")), productRepo, branchRepo, apiService )
        advanceUntilIdle()

        vm.onQuantityChange("12.5")
        vm.onDateSelected("2026-01-02")
        assertEquals("12.5", vm.uiState.value.quantity)
        assertEquals("2026-01-02", vm.uiState.value.wantedDate)

        vm.onDateSelected("")
        assertNull(vm.uiState.value.wantedDate)
    }

    @Test
    fun givenMissingProjectId_whenSave_thenSetsProjectIdError() = runTest {
        coEvery { productRepo.getAllProducts() } returns Result.success(listOf(product()))
        val vm = AddProductViewModel(SavedStateHandle(), productRepo, branchRepo, apiService)
        advanceUntilIdle()

        vm.save()

        assertEquals("Error: ไม่พบข้อมูล Project ID", vm.uiState.value.error)
    }

    @Test
    fun givenBlankProjectId_whenSave_thenSetsProjectIdError() = runTest {
        coEvery { productRepo.getAllProducts() } returns Result.success(listOf(product()))
        val vm = AddProductViewModel(SavedStateHandle(mapOf("projectId" to "")), productRepo, branchRepo, apiService)
        advanceUntilIdle()

        vm.save()

        assertEquals("Error: ไม่พบข้อมูล Project ID", vm.uiState.value.error)
    }

    @Test
    fun givenSelectedProductInvalid_whenSave_thenSetsProductSelectionError() = runTest {
        coEvery { productRepo.getAllProducts() } returns Result.success(listOf(product()))
        val vm = AddProductViewModel(SavedStateHandle(mapOf("projectId" to "PRJ-1")), productRepo, branchRepo, apiService)
        advanceUntilIdle()
        vm.onQuantityChange("10")

        vm.save()

        assertEquals("กรุณาเลือกสินค้าให้ถูกต้อง", vm.uiState.value.error)
    }

    @Test
    fun givenQuantityNonNumeric_whenSave_thenSetsQuantityValidationError() = runTest {
        coEvery { productRepo.getAllProducts() } returns Result.success(listOf(product()))
        val vm = AddProductViewModel(SavedStateHandle(mapOf("projectId" to "PRJ-1")), productRepo, branchRepo, apiService)
        advanceUntilIdle()
        vm.onBrandSelected("Brand A")
        vm.onNameSelected("Product A")
        vm.onQuantityChange("abc")

        vm.save()

        assertEquals("กรุณาระบุจำนวนที่ถูกต้อง", vm.uiState.value.error)
    }

    @Test
    fun givenQuantityZeroOrLess_whenSave_thenSetsQuantityValidationError() = runTest {
        coEvery { productRepo.getAllProducts() } returns Result.success(listOf(product()))
        val vm = AddProductViewModel(SavedStateHandle(mapOf("projectId" to "PRJ-1")), productRepo, branchRepo, apiService)
        advanceUntilIdle()
        vm.onBrandSelected("Brand A")
        vm.onNameSelected("Product A")
        vm.onQuantityChange("0")

        vm.save()

        assertEquals("กรุณาระบุจำนวนที่ถูกต้อง", vm.uiState.value.error)
    }

    @Test
    fun givenValidInputs_whenSaveSuccess_thenClearsErrorTogglesSavingAndMarksSaved() = runTest {
        coEvery { productRepo.getAllProducts() } returns Result.success(listOf(product()))
        coEvery {
            productRepo.addProductToProject("PRJ-1", "P1", 2.0, null, "B1")
        } returns Result.success(Unit)
        val vm = AddProductViewModel(SavedStateHandle(mapOf("projectId" to "PRJ-1")), productRepo, branchRepo, apiService)
        advanceUntilIdle()
        vm.onBrandSelected("Brand A")
        vm.onNameSelected("Product A")
        vm.onQuantityChange("2")
        vm.onDateSelected("")
        vm.onQuantityChange("0")
        vm.save()
        assertEquals("กรุณาระบุจำนวนที่ถูกต้อง", vm.uiState.value.error)
        vm.onQuantityChange("2")
        vm.onShippingBranchSelected("B1", "Branch 1")

        vm.save()
        runCurrent()
        assertTrue(vm.uiState.value.isSaving)
        assertNull(vm.uiState.value.error)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isSaved)
        assertFalse(vm.uiState.value.isSaving)
        coVerify(exactly = 1) { productRepo.addProductToProject("PRJ-1", "P1", 2.0, null, "B1") }
    }

    @Test
    fun givenValidInputs_whenSaveFails_thenStopsSavingAndSetsFailureMessage() = runTest {
        coEvery { productRepo.getAllProducts() } returns Result.success(listOf(product()))
        coEvery { productRepo.addProductToProject(any(), any(), any(), any(), any()) } returns Result.failure(Exception("api"))
        val vm = AddProductViewModel(SavedStateHandle(mapOf("projectId" to "PRJ-1")), productRepo, branchRepo, apiService)
        advanceUntilIdle()
        vm.onBrandSelected("Brand A")
        vm.onNameSelected("Product A")
        vm.onQuantityChange("2")
        vm.onShippingBranchSelected("B1", "Branch 1")

        vm.save()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isSaving)
        assertTrue(vm.uiState.value.error?.contains("บันทึกไม่สำเร็จ") == true)
    }
}
