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
    val products:                   List<ProductSimpleDto> = emptyList(),
    val selectedBrand:              String  = "",
    val selectedProductName:        String  = "",
    val selectedGroup:              String  = "",
    val selectedSubgroup:           String  = "",
    val color:                      String? = null,
    val thickness:                  String? = null,
    val width:                      String? = null,
    val length:                     String? = null,
    val dimensionUnit:              String? = null,
    val quantity:                   String  = "",
    val unit:                       String  = "",
    val wantedDate:                 String? = null,
    val shippingBranchOptions:      List<Pair<String, String>> = emptyList(),
    val selectedShippingBranchId:   String? = null,
    val selectedShippingBranchName: String? = null,
    val isLoadingBranches:          Boolean = false,
    val filteredNames:              List<String> = emptyList(),
    val isLoading:                  Boolean = false,
    val isSaving:                   Boolean = false,
    val error:                      String? = null,
    val isSaved:                    Boolean = false,
    val isEditMode:                 Boolean = false
)

@HiltViewModel
class AddProductViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val productRepo:      ProductRepository,
    private val branchRepo:       BranchRepository
) : ViewModel() {

    val projectId: String?     = savedStateHandle["projectId"]
    private val editProductId: String? = savedStateHandle["productId"]

    private val _uiState = MutableStateFlow(AddProductUiState(isEditMode = editProductId != null))
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
                    if (editProductId != null) prefillForEdit(list)
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = "โหลดสินค้าไม่สำเร็จ: ${e.message}", isLoading = false) }
                }
        }
    }

    private fun loadBranches() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingBranches = true) }
            branchRepo.getBranches()
                .onSuccess { branches ->
                    _uiState.update {
                        it.copy(
                            shippingBranchOptions = branches.map { b -> b.branchId to b.branchName },
                            isLoadingBranches     = false
                        )
                    }
                    syncShippingBranchName()
                }
                .onFailure {
                    try {
                        branchRepo.syncFromRemote()
                        val cached = branchRepo.observeBranches()
                        _uiState.update {
                            it.copy(
                                shippingBranchOptions = cached.map { b -> b.branchId to b.branchName },
                                isLoadingBranches     = false
                            )
                        }
                        syncShippingBranchName()
                    } catch (e: Exception) {
                        _uiState.update { it.copy(isLoadingBranches = false) }
                    }
                }
        }
    }

    private fun prefillForEdit(products: List<ProductSimpleDto>) {
        val pid      = editProductId ?: return
        val product  = products.find { it.productId == pid } ?: return
        val brand    = product.brand
        val filtered = products.filter { it.brand == brand }.map { it.productName }.distinct()

        val qty          = savedStateHandle.get<String>("quantity") ?: ""
        val wantedDate   = savedStateHandle.get<String>("wantedDate")
        val shipBranchId = savedStateHandle.get<String>("shippingBranchId")

        _uiState.update {
            it.copy(
                selectedBrand            = brand,
                selectedProductName      = product.productName,
                selectedGroup            = product.category    ?: "",
                selectedSubgroup         = product.subCategory ?: "",
                color                    = product.color,
                thickness                = product.thickness,
                width                    = product.width,
                length                   = product.length,
                dimensionUnit            = product.dimensionUnit,
                unit                     = product.unit ?: "",
                filteredNames            = filtered,
                quantity                 = qty,
                wantedDate               = wantedDate,
                selectedShippingBranchId = shipBranchId
            )
        }
        syncShippingBranchName()
    }

    private fun syncShippingBranchName() {
        val id   = _uiState.value.selectedShippingBranchId ?: return
        val name = _uiState.value.shippingBranchOptions.find { it.first == id }?.second ?: return
        _uiState.update { it.copy(selectedShippingBranchName = name) }
    }

    fun onBrandSelected(brand: String) {
        if (_uiState.value.isEditMode) return  // ✅ ล็อคใน edit mode
        val filtered = _uiState.value.products.filter { it.brand == brand }
        _uiState.update {
            it.copy(
                selectedBrand       = brand,
                selectedProductName = "",
                selectedGroup       = "",
                selectedSubgroup    = "",
                color               = null,
                thickness           = null,
                width               = null,
                length              = null,
                dimensionUnit       = null,
                unit                = "",
                filteredNames       = filtered.map { p -> p.productName }.distinct()
            )
        }
    }

    fun onNameSelected(name: String) {
        if (_uiState.value.isEditMode) return  // ✅ ล็อคใน edit mode
        val product = _uiState.value.products.find {
            it.productName == name &&
                    (it.brand == _uiState.value.selectedBrand || _uiState.value.selectedBrand.isBlank())
        }
        _uiState.update {
            it.copy(
                selectedProductName = name,
                selectedGroup       = product?.category    ?: "",
                selectedSubgroup    = product?.subCategory ?: "",
                color               = product?.color,
                thickness           = product?.thickness,
                width               = product?.width,
                length              = product?.length,
                dimensionUnit       = product?.dimensionUnit,
                unit                = product?.unit ?: ""
            )
        }
    }

    fun onQuantityChange(qty: String)  { _uiState.update { it.copy(quantity = qty) } }
    fun onDateSelected(date: String)   { _uiState.update { it.copy(wantedDate = date.ifBlank { null }) } }

    fun onShippingBranchSelected(id: String, name: String) {
        _uiState.update { it.copy(selectedShippingBranchId = id, selectedShippingBranchName = name) }
    }

    fun save() {
        val state = _uiState.value

        if (projectId.isNullOrBlank()) {
            _uiState.update { it.copy(error = "Error: ไม่พบข้อมูล Project ID") }
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

            if (state.isEditMode && editProductId != null) {
                // ✅ PATCH
                productRepo.updateProductInProject(
                    projectId        = projectId,
                    productId        = editProductId,
                    quantity         = qty,
                    wantedDate       = state.wantedDate,
                    shippingBranchId = state.selectedShippingBranchId
                ).fold(
                    onSuccess = { _uiState.update { it.copy(isSaving = false, isSaved = true) } },
                    onFailure = { e -> _uiState.update { it.copy(isSaving = false, error = "แก้ไขไม่สำเร็จ: ${e.message}") } }
                )
            } else {
                // ✅ POST
                val product = state.products.find {
                    it.productName == state.selectedProductName &&
                            (it.brand == state.selectedBrand || state.selectedBrand.isBlank())
                }
                if (product == null) {
                    _uiState.update { it.copy(isSaving = false, error = "กรุณาเลือกสินค้าให้ถูกต้อง") }
                    return@launch
                }
                productRepo.addProductToProject(
                    projectId        = projectId,
                    productId        = product.productId,
                    quantity         = qty,
                    wantedDate       = state.wantedDate,
                    shippingBranchId = state.selectedShippingBranchId
                ).fold(
                    onSuccess = { _uiState.update { it.copy(isSaving = false, isSaved = true) } },
                    onFailure = { e -> _uiState.update { it.copy(isSaving = false, error = "บันทึกไม่สำเร็จ: ${e.message}") } }
                )
            }
        }
    }
}