package com.example.pp68_salestrackingapp.ui.viewmodels.project

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pp68_salestrackingapp.data.repository.BranchRepository
import com.example.pp68_salestrackingapp.data.repository.ProductRepository
import com.example.pp68_salestrackingapp.data.repository.ProductSimpleDto
import com.example.pp68_salestrackingapp.data.remote.ApiService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val filteredGroups: List<String> = emptyList(),
    val filteredSubgroups: List<String> = emptyList(),
    val allBrandPairs: List<Pair<String, String>> = emptyList(),
    val allUnits: List<String> = emptyList(),

    val isLoading: Boolean = false,
    val isSearching: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val isSaved: Boolean = false,
    val isEditMode: Boolean = false,
    
    val searchQuery: String = "",
    val currentPage: Int = 0,
    val hasMore: Boolean = true
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

    private var searchJob: Job? = null
    private val pageSize = 2000

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            loadBranches()

            productRepo.getBrands().onSuccess { pairs ->
                _uiState.update { it.copy(allBrandPairs = pairs, filteredBrands = pairs.map { it.second }) }
            }

            productRepo.getUnits().onSuccess { units ->
                _uiState.update { it.copy(allUnits = units) }
            }

            // เริ่มต้นโหลดสินค้าหน้าแรก
            performSearch(reset = true)

            if (editProductId != null && projectId != null) {
                loadExistingProjectProduct(projectId, editProductId)
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
                    productRepo.getProductById(prodId).onSuccess { productMaster ->
                        val brandName = _uiState.value.allBrandPairs
                            .find { it.first == productMaster.brandNo }?.second ?: productMaster.brand
                        
                        _uiState.update {
                            it.copy(
                                selectedBrand = brandName,
                                selectedBrandNo = productMaster.brandNo,
                                selectedProductName = productMaster.productName,
                                selectedGroup = productMaster.category ?: "",
                                selectedSubgroup = productMaster.subCategory ?: "",
                                color = productMaster.color,
                                thickness = productMaster.thickness,
                                width = productMaster.width,
                                length = productMaster.length,
                                dimensionUnit = productMaster.dimensionUnit,
                                unit = productMaster.unit ?: "",
                                quantity = item.quantity?.toString() ?: "",
                                wantedDate = item.desiredDate,
                                selectedShippingBranchId = item.shippingBranchId,
                                selectedShippingBranchName = it.shippingBranchOptions
                                    .find { b -> b.first == item.shippingBranchId }?.second
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(error = "โหลดข้อมูลสินค้าเดิมไม่สำเร็จ") }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(500)
            performSearch(reset = true)
        }
    }

    fun loadMore() {
        if (_uiState.value.isSearching || !_uiState.value.hasMore) return
        viewModelScope.launch {
            performSearch(reset = false)
        }
    }

    private suspend fun performSearch(reset: Boolean) {
        val currentState = _uiState.value
        val nextPage = if (reset) 0 else currentState.currentPage + 1
        
        _uiState.update { it.copy(isSearching = true) }
        
        productRepo.searchProducts(
            query = currentState.searchQuery,
            brandNo = currentState.selectedBrandNo,
            limit = pageSize,
            offset = nextPage * pageSize
        ).onSuccess { list ->
            _uiState.update { state ->
                val newList = if (reset) list else state.products + list
                val byBrand = if (state.selectedBrandNo != null) newList.filter { it.brandNo == state.selectedBrandNo } else newList
                val byGroup = if (state.selectedGroup.isNotBlank()) byBrand.filter { it.category == state.selectedGroup } else byBrand
                val bySubgroup = if (state.selectedSubgroup.isNotBlank()) byGroup.filter { it.subCategory == state.selectedSubgroup } else byGroup
                state.copy(
                    products = newList,
                    currentPage = nextPage,
                    hasMore = list.size >= pageSize,
                    isSearching = false,
                    filteredBrands = state.allBrandPairs.map { it.second }.ifEmpty { newList.map { it.brand }.distinct().sorted() },
                    filteredGroups = byBrand.mapNotNull { it.category }.distinct().filter { it.isNotBlank() }.sorted(),
                    filteredSubgroups = byGroup.mapNotNull { it.subCategory }.distinct().filter { it.isNotBlank() }.sorted(),
                    filteredNames = bySubgroup.mapNotNull { it.productName }.distinct().filter { it.isNotBlank() }.sorted()
                )
            }
        }.onFailure { e ->
            _uiState.update { it.copy(isSearching = false, error = "ค้นหาล้มเหลว: ${e.message}") }
        }
    }

    private suspend fun loadBranches() {
        try {
            branchRepo.syncFromRemote()
            val branches = branchRepo.observeBranches()
            _uiState.update { it.copy(shippingBranchOptions = branches.map { b -> b.branchId to b.branchName }) }
        } catch (e: Exception) {}
    }

    fun onBrandSelected(brand: String) {
        val brandNo = _uiState.value.allBrandPairs.find { it.second == brand }?.first
        _uiState.update { it.copy(selectedBrand = brand, selectedBrandNo = brandNo) }
        viewModelScope.launch {
            performSearch(reset = true)
        }
    }

    fun onGroupSelected(group: String) {
        _uiState.update { it.copy(selectedGroup = group, selectedSubgroup = "") }
        recomputeFilteredLists()
    }

    fun onSubgroupSelected(subgroup: String) {
        _uiState.update { it.copy(selectedSubgroup = subgroup) }
        recomputeFilteredLists()
    }

    private fun recomputeFilteredLists() {
        _uiState.update { s ->
            val byBrand = if (s.selectedBrandNo != null) s.products.filter { it.brandNo == s.selectedBrandNo } else s.products
            val byGroup = if (s.selectedGroup.isNotBlank()) byBrand.filter { it.category == s.selectedGroup } else byBrand
            val bySubgroup = if (s.selectedSubgroup.isNotBlank()) byGroup.filter { it.subCategory == s.selectedSubgroup } else byGroup
            s.copy(
                filteredBrands = s.allBrandPairs.map { it.second }.ifEmpty { s.products.map { it.brand }.distinct().sorted() },
                filteredGroups = byBrand.mapNotNull { it.category }.distinct().filter { it.isNotBlank() }.sorted(),
                filteredSubgroups = byGroup.mapNotNull { it.subCategory }.distinct().filter { it.isNotBlank() }.sorted(),
                filteredNames = bySubgroup.mapNotNull { it.productName }.distinct().filter { it.isNotBlank() }.sorted()
            )
        }
    }

    fun onNameSelected(name: String) {
        val state = _uiState.value
        val product = state.products.find { p -> p.productName == name }
        
        if (product != null) {
            _uiState.update {
                it.copy(
                    selectedProductName = name,
                    selectedGroup       = product.category ?: "",
                    selectedSubgroup    = product.subCategory ?: "",
                    color               = product.color,
                    thickness           = product.thickness,
                    width               = product.width,
                    length              = product.length,
                    dimensionUnit       = product.dimensionUnit,
                    unit                = product.unit ?: it.unit
                )
            }
        } else {
             _uiState.update { it.copy(selectedProductName = name) }
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
            
            var product = state.products.find {
                it.productName == state.selectedProductName &&
                (state.selectedBrandNo?.let { no -> it.brandNo == no } ?: (it.brand == state.selectedBrand))
            }
            
            if (product == null && state.isEditMode && editProductId != null) {
                 productRepo.getProductById(editProductId).onSuccess { product = it }
            }

            if (product == null) {
                _uiState.update { it.copy(isSaving = false, error = "กรุณาเลือกสินค้า") }
                return@launch
            }

            if (state.isEditMode && editProductId != null) {
                productRepo.updateProjectProduct(projectId, editProductId, mapOf("quantity" to qty, "desired_date" to state.wantedDate, "shipping_branch_id" to state.selectedShippingBranchId))
                    .fold(
                        onSuccess = { _uiState.update { it.copy(isSaving = false, isSaved = true) } },
                        onFailure = { e -> _uiState.update { it.copy(isSaving = false, error = "แก้ไขไม่สำเร็จ: ${e.message}") } }
                    )
            } else {
                productRepo.addProductToProject(
                    projectId        = projectId,
                    productId        = product!!.productId,
                    quantity         = qty,
                    wantedDate       = state.wantedDate,
                    shippingBranchId = state.selectedShippingBranchId,
                    brandName        = state.selectedBrand.ifBlank { null },
                    categoryName     = state.selectedGroup.ifBlank { null },
                    subcategoryName  = state.selectedSubgroup.ifBlank { null },
                    productName      = state.selectedProductName.ifBlank { null },
                    color            = state.color,
                    thickness        = state.thickness,
                    width            = state.width,
                    length           = state.length,
                    dimensionUnit    = state.dimensionUnit,
                    uom              = state.unit.ifBlank { null }
                )
                    .fold(
                        onSuccess = { _uiState.update { it.copy(isSaving = false, isSaved = true) } },
                        onFailure = { e -> _uiState.update { it.copy(isSaving = false, error = "เพิ่มไม่สำเร็จ: ${e.message}") } }
                    )
            }
        }
    }
}
