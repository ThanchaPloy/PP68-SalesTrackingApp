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
            
            // 1. Load Branches
            loadBranches()
            
            // 2. Load Product Master
            productRepo.getAllProducts().onSuccess { list ->
                _uiState.update { it.copy(products = list) }
                
                // 3. If Edit Mode, Load existing data
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
                        onBrandSelected(productMaster.brand)
                        onNameSelected(productMaster.productName)
                    }

                    _uiState.update { it.copy(
                        quantity = item.quantity?.toString() ?: "",
                        wantedDate = item.desiredDate,
                        selectedShippingBranchId = item.shippingBranchId,
                        selectedShippingBranchName = _uiState.value.shippingBranchOptions.find { it.first == item.shippingBranchId }?.second
                    ) }
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
            _uiState.update { it.copy(
                shippingBranchOptions = branches.map { b -> b.branchId to b.branchName }
            ) }
        } catch (e: Exception) {}
    }

    fun onBrandSelected(brand: String) {
        val filtered = _uiState.value.products.filter { it.brand == brand }
        _uiState.update { it.copy(
            selectedBrand = brand,
            selectedProductName = "",
            filteredNames = filtered.map { it.productName }.distinct()
        ) }
    }

    fun onNameSelected(name: String) {
        val product = _uiState.value.products.find { 
            it.productName == name && (it.brand == _uiState.value.selectedBrand || _uiState.value.selectedBrand.isBlank())
        }
        _uiState.update { it.copy(
            selectedProductName = name,
            selectedGroup = product?.category ?: "",
            selectedSubgroup = product?.subCategory ?: "",
            color = product?.color,
            thickness = product?.thickness,
            width = product?.width,
            length = product?.length,
            dimensionUnit = product?.dimensionUnit,
            unit = product?.unit ?: ""
        ) }
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
                // UPDATE
                val updates = mapOf(
                    "quantity" to qty,
                    "desired_date" to state.wantedDate,
                    "shipping_branch_id" to state.selectedShippingBranchId
                )
                productRepo.updateProjectProduct(projectId, editProductId, updates).fold(
                    onSuccess = { _uiState.update { it.copy(isSaving = false, isSaved = true) } },
                    onFailure = { e -> _uiState.update { it.copy(isSaving = false, error = "แก้ไขไม่สำเร็จ: ${e.message}") } }
                )
            } else {
                // INSERT
                val product = state.products.find { it.productName == state.selectedProductName && it.brand == state.selectedBrand }
                if (product == null) {
                    _uiState.update { it.copy(isSaving = false, error = "กรุณาเลือกสินค้า") }
                    return@launch
                }
                productRepo.addProductToProject(projectId, product.productId, qty, state.wantedDate, state.selectedShippingBranchId).fold(
                    onSuccess = { _uiState.update { it.copy(isSaving = false, isSaved = true) } },
                    onFailure = { e -> _uiState.update { it.copy(isSaving = false, error = "เพิ่มไม่สำเร็จ: ${e.message}") } }
                )
            }
        }
    }
}
