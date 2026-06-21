package com.example.pp68_salestrackingapp.ui.viewmodels.project

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pp68_salestrackingapp.data.repository.BranchRepository
import com.example.pp68_salestrackingapp.data.repository.ProductRepository
import com.example.pp68_salestrackingapp.data.repository.ProductSimpleDto
import com.example.pp68_salestrackingapp.data.remote.ApiService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddProductUiState(
    val products: List<ProductSimpleDto> = emptyList(),
    val selectedBrand: String = "",
    val selectedBrandNo: String? = null,
    val selectedProductName: String = "",
    val selectedGroup: String = "",
    val selectedSubgroup: String = "",
    val color: String? = null,
    val thickness: String? = null,
    val width: String? = null,
    val length: String? = null,
    val dimensionUnit: String? = null,
    val quantity: String = "",
    val unit: String = "",
    val wantedDate: String? = null,

    val shippingBranchOptions: List<Pair<String, String>> = emptyList(),
    val selectedShippingBranchId: String? = null,
    val selectedShippingBranchName: String? = null,
    val isLoadingBranches: Boolean = false,

    val filteredNames: List<String> = emptyList(),
    val filteredBrands: List<String> = emptyList(),
    // (brandNo, displayName) from silver_productbrand_dx — reliable source of truth
    val allBrandPairs: List<Pair<String, String>> = emptyList(),
    val allUnits: List<String> = emptyList(),

    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val isSaved: Boolean = false,
    val isEditMode: Boolean = false
)

@HiltViewModel
class AddProductViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val productRepo: ProductRepository,
    private val branchRepo: BranchRepository,
    private val apiService: ApiService
) : ViewModel() {

    val projectId: String? = savedStateHandle["projectId"]
    val editProductId: String? = savedStateHandle["productId"]

    private val _uiState = MutableStateFlow(AddProductUiState(isEditMode = editProductId != null))
    val uiState: StateFlow<AddProductUiState> = _uiState

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            loadBranches()

            productRepo.getBrands().onSuccess { pairs ->
                _uiState.update { it.copy(allBrandPairs = pairs) }
            }

            productRepo.getUnits().onSuccess { units ->
                _uiState.update { it.copy(allUnits = units) }
            }

            productRepo.getAllProducts().onSuccess { list ->
                _uiState.update { it.copy(products = list) }
                updateFilters("", null, "")

                if (editProductId != null && projectId != null) {
                    loadExistingProjectProduct(projectId, editProductId)
                }
            }.onFailure { e ->
                _uiState.update { it.copy(error = "โหลดสินค้าไม่สำเร็จ: ${e.message}") }
            }

            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private suspend fun loadExistingProjectProduct(pId: String, prodId: String) {
        try {
            val resp = apiService.getProjectProducts("eq.$pId")
            if (resp.isSuccessful) {
                val item = resp.body()?.find { it.productId == prodId }
                if (item != null) {
                    val productMaster = _uiState.value.products.find { it.productId == prodId }
                    if (productMaster != null) {
                        val brandName = _uiState.value.allBrandPairs
                            .find { it.first == productMaster.brandNo }?.second ?: productMaster.brand
                        onBrandSelected(brandName)
                        onNameSelected(productMaster.productName)
                    }
                    _uiState.update {
                        it.copy(
                            quantity = item.quantity?.toString() ?: "",
                            wantedDate = item.desiredDate,
                            selectedShippingBranchId = item.shippingBranchId,
                            selectedShippingBranchName = it.shippingBranchOptions
                                .find { b -> b.first == item.shippingBranchId }?.second
                        )
                    }
                }
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(error = "โหลดข้อมูลสินค้าเดิมไม่สำเร็จ") }
        }
    }

    private suspend fun loadBranches() {
        try {
            branchRepo.syncFromRemote()
            val branches = branchRepo.observeBranches()
            _uiState.update { it.copy(shippingBranchOptions = branches.map { b -> b.branchId to b.branchName }) }
        } catch (e: Exception) {}
    }

    // currentBrandNo = null means no brand filter
    private fun updateFilters(currentBrand: String, currentBrandNo: String?, currentName: String) {
        val allProducts = _uiState.value.products
        val masterPairs = _uiState.value.allBrandPairs

        val brands: List<String> = if (currentName.isNotBlank()) {
            // Narrow to brands that carry this product — use brandNo for reliable match
            val brandNosWithProduct = allProducts
                .filter { it.productName == currentName }
                .mapNotNull { it.brandNo }
                .toSet()
            masterPairs.filter { it.first in brandNosWithProduct }.map { it.second }
                .ifEmpty {
                    // Fallback: product exists but brand not in master table
                    allProducts.filter { it.productName == currentName }
                        .map { it.brand }.distinct().filter { it.isNotBlank() }.sorted()
                }
        } else {
            // All brands from master; fallback to product-derived if master not loaded yet
            masterPairs.map { it.second }.ifEmpty {
                allProducts.map { it.brand }.distinct().filter { it.isNotBlank() }.sorted()
            }
        }

        val names: List<String> = when {
            currentBrandNo != null ->
                allProducts.filter { it.brandNo == currentBrandNo }
                    .map { it.productName }.distinct().filter { it.isNotBlank() }.sorted()
            currentBrand.isNotBlank() ->
                // Brand name selected but no matching code (shouldn't normally happen)
                allProducts.filter { it.brand.trim() == currentBrand.trim() }
                    .map { it.productName }.distinct().filter { it.isNotBlank() }.sorted()
            else ->
                allProducts.map { it.productName }.distinct().filter { it.isNotBlank() }.sorted()
        }

        _uiState.update { it.copy(filteredBrands = brands, filteredNames = names) }
    }

    fun onBrandSelected(brand: String) {
        val brandNo = if (brand.isBlank()) null
                      else _uiState.value.allBrandPairs.find { it.second == brand }?.first
        val currentName = _uiState.value.selectedProductName

        _uiState.update { it.copy(selectedBrand = brand, selectedBrandNo = brandNo) }
        updateFilters(brand, brandNo, currentName)

        val stillValid = _uiState.value.filteredNames.contains(currentName)
        if (!stillValid && currentName.isNotBlank()) {
            onNameSelected("")
        }
    }

    fun onNameSelected(name: String) {
        val currentState = _uiState.value
        val product = currentState.products.find {
            it.productName == name &&
            (currentState.selectedBrandNo?.let { no -> it.brandNo == no }
                ?: (currentState.selectedBrand.isBlank() || it.brand == currentState.selectedBrand))
        }

        _uiState.update {
            it.copy(
                selectedProductName = name,
                selectedGroup       = product?.category ?: "",
                selectedSubgroup    = product?.subCategory ?: "",
                color               = product?.color,
                thickness           = product?.thickness,
                width               = product?.width,
                length              = product?.length,
                dimensionUnit       = product?.dimensionUnit,
                unit                = product?.unit ?: if (name.isBlank()) "" else it.unit
            )
        }

        updateFilters(currentState.selectedBrand, currentState.selectedBrandNo, name)

        // Auto-fill brand if only one brand carries this product and no brand is selected
        if (name.isNotBlank() && currentState.selectedBrand.isBlank()) {
            val uniqueBrandNos = currentState.products
                .filter { it.productName == name }
                .mapNotNull { it.brandNo }
                .distinct()
            if (uniqueBrandNos.size == 1) {
                val brandName = currentState.allBrandPairs
                    .find { it.first == uniqueBrandNos[0] }?.second ?: ""
                if (brandName.isNotBlank()) onBrandSelected(brandName)
            }
        }
    }

    fun onUnitSelected(unit: String) {
        _uiState.update { it.copy(unit = unit) }
    }

    fun onQuantityChange(qty: String) {
        _uiState.update { it.copy(quantity = qty) }
    }

    fun onDateSelected(date: String) {
        _uiState.update { it.copy(wantedDate = date.ifBlank { null }) }
    }

    fun onShippingBranchSelected(id: String, name: String) {
        _uiState.update { it.copy(selectedShippingBranchId = id, selectedShippingBranchName = name) }
    }

    fun save() {
        val state = _uiState.value
        if (projectId.isNullOrBlank()) return

        val qty = state.quantity.toDoubleOrNull()
        if (qty == null || qty <= 0) {
            _uiState.update { it.copy(error = "กรุณาระบุจำนวนที่ถูกต้อง") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }

            if (state.isEditMode && editProductId != null) {
                productRepo.updateProjectProduct(
                    projectId, editProductId,
                    mapOf("quantity" to qty, "desired_date" to state.wantedDate,
                          "shipping_branch_id" to state.selectedShippingBranchId)
                ).fold(
                    onSuccess = { _uiState.update { it.copy(isSaving = false, isSaved = true) } },
                    onFailure = { e -> _uiState.update { it.copy(isSaving = false, error = "แก้ไขไม่สำเร็จ: ${e.message}") } }
                )
            } else {
                val product = state.products.find {
                    it.productName == state.selectedProductName &&
                    (state.selectedBrandNo?.let { no -> it.brandNo == no } ?: (it.brand == state.selectedBrand))
                }
                if (product == null) {
                    _uiState.update { it.copy(isSaving = false, error = "กรุณาเลือกสินค้า") }
                    return@launch
                }
                productRepo.addProductToProject(
                    projectId, product.productId, qty, state.wantedDate, state.selectedShippingBranchId
                ).fold(
                    onSuccess = { _uiState.update { it.copy(isSaving = false, isSaved = true) } },
                    onFailure = { e -> _uiState.update { it.copy(isSaving = false, error = "เพิ่มไม่สำเร็จ: ${e.message}") } }
                )
            }
        }
    }
}
