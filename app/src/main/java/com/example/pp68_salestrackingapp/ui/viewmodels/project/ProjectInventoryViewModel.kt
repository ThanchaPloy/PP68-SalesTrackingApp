package com.example.pp68_salestrackingapp.ui.viewmodels.project

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pp68_salestrackingapp.data.repository.CustomerRepository
import com.example.pp68_salestrackingapp.data.repository.ProjectRepository
import com.example.pp68_salestrackingapp.data.model.Project
import com.example.pp68_salestrackingapp.data.remote.ApiService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InventoryItem(
    val productId: String,
    val productName: String,
    val category: String,
    val quantity: Double,
    val unit: String
)

data class ProjectInventoryUiState(
    val project: Project? = null,
    val companyName: String = "",
    val items: List<InventoryItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ProjectInventoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val projectRepo: ProjectRepository,
    private val customerRepo: CustomerRepository,
    private val apiService: ApiService
) : ViewModel() {

    private val projectId: String? = savedStateHandle["projectId"]

    private val _uiState = MutableStateFlow(ProjectInventoryUiState())
    val uiState: StateFlow<ProjectInventoryUiState> = _uiState

    init {
        load()
    }

    fun load() {
        val currentId = projectId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                // 1. Load Project Details
                val project = projectRepo.getProjectById(currentId).getOrNull()
                val companyName = project?.let { 
                    customerRepo.getCustomerById(it.custId).getOrNull()?.companyName
                } ?: ""
                
                // 2. Load Products for this Project via ApiService
                val items = fetchProjectProducts(currentId)

                _uiState.update {
                    it.copy(
                        project = project,
                        companyName = companyName,
                        items = items,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                Log.e("ProjectInventoryVM", "Failed to load", e)
                _uiState.update { it.copy(isLoading = false, error = e.localizedMessage) }
            }
        }
    }

    private suspend fun fetchProjectProducts(id: String): List<InventoryItem> {
        return try {
            // ✅ ดึง project_product
            val ppResp = apiService.getProjectProducts("eq.$id")
            if (!ppResp.isSuccessful || ppResp.body().isNullOrEmpty()) {
                return emptyList()
            }
            val projectProducts = ppResp.body()!!

            // ✅ ดึง product master ทั้งหมด
            val productsResp = apiService.getProductMaster()
            val productsMap  = productsResp.body()
                ?.associateBy { it.productId }
                ?: emptyMap()

            // ✅ Map รวมกัน
            projectProducts.mapNotNull { pp ->
                val product = productsMap[pp.productId] ?: return@mapNotNull null
                InventoryItem(
                    productId   = pp.productId,
                    productName = product.productGroup ?: pp.productId,
                    category    = product.productType ?: "ทั่วไป",
                    quantity    = pp.quantity ?: 0.0,
                    unit        = product.unit ?: "ชิ้น"
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
