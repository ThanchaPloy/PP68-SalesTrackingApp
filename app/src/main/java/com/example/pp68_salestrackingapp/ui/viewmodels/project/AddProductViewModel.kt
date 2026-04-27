package com.example.pp68_salestrackingapp.ui.viewmodels.project

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pp68_salestrackingapp.data.repository.BranchRepository
import com.example.pp68_salestrackingapp.data.repository.ProductRepository
import com.example.pp68_salestrackingapp.data.repository.ProductSimpleDto
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
    val isSaved: Boolean = false
)

@HiltViewModel
class AddProductViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val productRepo: ProductRepository,
    private val branchRepo: BranchRepository
) : ViewModel() {

    // ✅ ใช้ Key "projectId" ให้ตรงกับ NavGraph
    // และดึงค่าออกมาเก็บไว้เพื่อให้มั่นใจว่าไม่หาย
    val projectId: String? = savedStateHandle["projectId"]

    private val _uiState = MutableStateFlow(AddProductUiState())
    val uiState: StateFlow<AddProductUiState> = _uiState

    init {
        loadProducts()
        loadBranches()
    }

    private fun loadProducts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            productRepo.getAllProducts()
                .onSuccess { list ->
                    _uiState.update { it.copy(products = list, isLoading = false) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = "โหลดสินค้าไม่สำเร็จ: ${e.message}", isLoading = false) }
                }
        }
    }

    private fun loadBranches() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingBranches = true) }
            try {
                branchRepo.syncFromRemote()
                val branches = branchRepo.observeBranches()
                _uiState.update { it.copy(
                    shippingBranchOptions = branches.map { b -> b.branchId to b.branchName },
                    isLoadingBranches = false
                ) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingBranches = false) }
            }
        }
    }

    fun onBrandSelected(brand: String) {
        val filtered = _uiState.value.products.filter { it.brand == brand }
        _uiState.update { it.copy(
            selectedBrand = brand,
            selectedProductName = "",
            selectedGroup = "",
            selectedSubgroup = "",
            color = null,
            thickness = null,
            width = null,
            length = null,
            dimensionUnit = null,
            unit = "",
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
        
        if (projectId.isNullOrBlank()) {
            _uiState.update { it.copy(error = "Error: ไม่พบข้อมูล Project ID") }
            return
        }

        val product = state.products.find { 
            it.productName == state.selectedProductName && 
            (it.brand == state.selectedBrand || state.selectedBrand.isBlank())
        }
        
        if (product == null) {
            _uiState.update { it.copy(error = "กรุณาเลือกสินค้าให้ถูกต้อง") }
            return
        }
        
        val qty = state.quantity.toDoubleOrNull()
        if (qty == null || qty <= 0) {
            _uiState.update { it.copy(error = "กรุณาระบุจำนวนที่ถูกต้อง") }
            return
        }

        if (state.selectedShippingBranchId == null) {
            _uiState.update { it.copy(error = "กรุณาเลือกสาขาที่ออกของ") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            
            productRepo.addProductToProject(
                projectId = projectId,
                productId = product.productId,
                quantity = qty,
                wantedDate = state.wantedDate,
                shippingBranchId = state.selectedShippingBranchId
            ).fold(
                onSuccess = {
                    // ✅ เมื่อบันทึกสำเร็จ ต้องเปลี่ยนสถานะเพื่อให้ UI กลับหน้าเดิม
                    _uiState.update { it.copy(isSaving = false, isSaved = true) }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isSaving = false, error = "บันทึกไม่สำเร็จ: ${e.message}") }
                }
            )
        }
    }
}
