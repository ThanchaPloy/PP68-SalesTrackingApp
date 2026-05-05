package com.example.pp68_salestrackingapp.ui.viewmodels.project

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pp68_salestrackingapp.data.model.Project
import com.example.pp68_salestrackingapp.data.remote.ApiService
import com.example.pp68_salestrackingapp.data.repository.BranchRepository
import com.example.pp68_salestrackingapp.data.repository.CustomerRepository
import com.example.pp68_salestrackingapp.data.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InventoryItem(
    val productId:          String,
    val productName:        String,
    val category:           String,
    val quantity:           Double,
    val unit:               String,
    val color:              String? = null,
    val thickness:          String? = null,
    val width:              String? = null,
    val length:             String? = null,
    val dimensionUnit:      String? = null,
    val desiredDate:        String? = null,
    val shippingBranchId:   String? = null,   // ✅ เก็บไว้เพื่อส่งไป EditProduct
    val shippingBranchName: String? = null
)

data class ProjectInventoryUiState(
    val project:       Project? = null,
    val companyName:   String   = "",
    val items:         List<InventoryItem> = emptyList(),
    val isLoading:     Boolean  = false,
    val error:         String?  = null,
    // ── Delete dialog ─────────────────────────────────────────
    val deletingItem:  InventoryItem? = null,
    val isDeletingItem: Boolean = false
)

@HiltViewModel
class ProjectInventoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val projectRepo:  ProjectRepository,
    private val customerRepo: CustomerRepository,
    private val branchRepo:   BranchRepository,
    private val apiService:   ApiService
) : ViewModel() {

    private val projectId: String? = savedStateHandle["projectId"]

    private val _uiState = MutableStateFlow(ProjectInventoryUiState())
    val uiState: StateFlow<ProjectInventoryUiState> = _uiState

    init { load() }

    fun load() {
        val currentId = projectId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val project     = projectRepo.getProjectById(currentId).getOrNull()
                val companyName = project?.let {
                    customerRepo.getCustomerById(it.custId).getOrNull()?.companyName
                } ?: ""
                val items = fetchProjectProducts(currentId)
                _uiState.update {
                    it.copy(project = project, companyName = companyName, items = items, isLoading = false)
                }
            } catch (e: Exception) {
                Log.e("ProjectInventoryVM", "Failed to load", e)
                _uiState.update { it.copy(isLoading = false, error = e.localizedMessage) }
            }
        }
    }

    private suspend fun fetchProjectProducts(id: String): List<InventoryItem> {
        return try {
            val ppResp = apiService.getProjectProducts("eq.$id")
            if (!ppResp.isSuccessful || ppResp.body().isNullOrEmpty()) return emptyList()
            val projectProducts = ppResp.body()!!

            val productsMap = apiService.getProductMaster().body()
                ?.associateBy { it.productId } ?: emptyMap()

            branchRepo.syncFromRemote()
            val branchesMap = branchRepo.observeBranches().associateBy { it.branchId }

            projectProducts.mapNotNull { pp ->
                val product = productsMap[pp.productId] ?: return@mapNotNull null
                InventoryItem(
                    productId          = pp.productId,
                    productName        = product.productGroup ?: pp.productId,
                    category           = product.productType  ?: "ทั่วไป",
                    quantity           = pp.quantity          ?: 0.0,
                    unit               = product.unit         ?: "ชิ้น",
                    color              = product.color,
                    thickness          = product.thickness,
                    width              = product.width,
                    length             = product.length,
                    dimensionUnit      = product.dimensionUnit,
                    desiredDate        = pp.desiredDate,
                    shippingBranchId   = pp.shippingBranchId,  // ✅ เก็บ id ไว้
                    shippingBranchName = branchesMap[pp.shippingBranchId]?.branchName
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    // ── Delete ────────────────────────────────────────────────

    fun requestDelete(item: InventoryItem) {
        _uiState.update { it.copy(deletingItem = item) }
    }

    fun cancelDelete() {
        _uiState.update { it.copy(deletingItem = null) }
    }

    fun confirmDelete() {
        val item      = _uiState.value.deletingItem ?: return
        val currentId = projectId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isDeletingItem = true) }
            try {
                val resp = apiService.deleteProjectProduct(
                    projectId = "eq.$currentId",
                    productId = "eq.${item.productId}"
                )
                if (resp.isSuccessful) {
                    _uiState.update {
                        it.copy(
                            isDeletingItem = false,
                            deletingItem   = null,
                            items          = it.items.filter { i -> i.productId != item.productId }
                        )
                    }
                } else {
                    _uiState.update { it.copy(
                        isDeletingItem = false,
                        error          = "ลบไม่สำเร็จ: HTTP ${resp.code()}"
                    )}
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isDeletingItem = false, error = e.message) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}