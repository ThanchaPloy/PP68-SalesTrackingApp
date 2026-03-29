package com.example.pp68_salestrackingapp.ui.screen.project

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val quantity: String = "",
    val unit: String = "",
    val wantedDate: String? = null,
    
    val filteredNames: List<String> = emptyList(),
    val filteredGroups: List<String> = emptyList(),
    val filteredSubgroups: List<String> = emptyList(),
    
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val isSaved: Boolean = false
)

@HiltViewModel
class AddProductViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val productRepo: ProductRepository
) : ViewModel() {

    private val projectId: String = checkNotNull(savedStateHandle["projectId"])

    private val _uiState = MutableStateFlow(AddProductUiState())
    val uiState: StateFlow<AddProductUiState> = _uiState

    init {
        loadProducts()
    }

    private fun loadProducts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            productRepo.getAllProducts()
                .onSuccess { list ->
                    _uiState.update { it.copy(products = list, isLoading = false) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message, isLoading = false) }
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
            unit = product?.unit ?: ""
        ) }
    }

    fun onQuantityChange(qty: String) {
        _uiState.update { it.copy(quantity = qty) }
    }

    fun onDateSelected(date: String) {
        _uiState.update { it.copy(wantedDate = date.ifBlank { null }) }
    }

    fun save() {
        val state = _uiState.value
        val product = state.products.find { 
            it.productName == state.selectedProductName && 
            (it.brand == state.selectedBrand || state.selectedBrand.isBlank())
        }
        
        if (product == null) {
            _uiState.update { it.copy(error = "กรุณาเลือกสินค้า") }
            return
        }
        
        val qty = state.quantity.toDoubleOrNull()
        if (qty == null || qty <= 0) {
            _uiState.update { it.copy(error = "กรุณาระบุจำนวนที่ถูกต้อง") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            productRepo.addProductToProject(
                projectId = projectId,
                productId = product.productId,
                quantity = qty,
                wantedDate = state.wantedDate
            ).onSuccess {
                _uiState.update { it.copy(isSaving = false, isSaved = true) }
            }.onFailure { e ->
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }
}
