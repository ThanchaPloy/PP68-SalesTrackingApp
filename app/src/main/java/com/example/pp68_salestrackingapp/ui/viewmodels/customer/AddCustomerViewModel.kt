package com.example.pp68_salestrackingapp.ui.viewmodels.customer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pp68_salestrackingapp.data.model.Customer
import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import com.example.pp68_salestrackingapp.data.repository.CustomerRepository
import com.example.pp68_salestrackingapp.data.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

// ─── UI State ─────────────────────────────────────────────────
data class AddCustomerUiState(
    val custId:              String? = null,
    val companyName:         String  = "",
    val branch:              String  = "",
    val address:             String  = "",
    val selectedLat:         Double? = null,
    val selectedLng:         Double? = null,
    val custType:            String? = null,
    val companyStatus:       String  = "customer",
    val selectedProjectId:   String? = null,
    val selectedProjectName: String? = null,
    val firstCustomerDate:   String? = null,
    val projectOptions:      List<Pair<String, String>> = emptyList(),

    // validation
    val companyNameError: String? = null,
    val custTypeError:    String? = null,

    // ui
    val isLoading:    Boolean = false,
    val isSaved:      Boolean = false,
    val saveError:    String? = null,
    val projectNumber: String = ""
)

// ─── Events ───────────────────────────────────────────────────
sealed class AddCustomerEvent {
    data class LoadCustomer(val id: String) : AddCustomerEvent()
    data class CompanyNameChanged(val value: String) : AddCustomerEvent()
    data class BranchChanged(val value: String)      : AddCustomerEvent()
    data class AddressChanged(val value: String)     : AddCustomerEvent()
    data class LocationPicked(val lat: Double, val lng: Double) : AddCustomerEvent()
    data class CustTypeChanged(val value: String)    : AddCustomerEvent()
    data class StatusChanged(val value: String)      : AddCustomerEvent()
    data class ProjectSelected(val id: String, val name: String) : AddCustomerEvent()
    data class FirstCustomerDateChanged(val value: String) : AddCustomerEvent()
    object UseCurrentLocation : AddCustomerEvent()
    object Save               : AddCustomerEvent()
}

// ─── ViewModel ────────────────────────────────────────────────
@HiltViewModel
class AddCustomerViewModel @Inject constructor(
    private val customerRepo: CustomerRepository,
    private val projectRepo:  ProjectRepository,
    private val authRepo:     AuthRepository          // ✅ เพิ่ม AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddCustomerUiState())
    val uiState: StateFlow<AddCustomerUiState> = _uiState

    init { loadProjectOptions() }

    private fun loadProjectOptions() {
        viewModelScope.launch {
            try {
                val projects = projectRepo.getAllProjectsFlow().first()
                _uiState.update {
                    it.copy(projectOptions = projects.map { p -> p.projectId to p.projectName })
                }
            } catch (_: Exception) {}
        }
    }

    private fun loadCustomer(id: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            customerRepo.getCustomerById(id).fold(
                onSuccess = { customer ->
                    _uiState.update {
                        it.copy(
                            custId            = customer.custId,
                            companyName       = customer.companyName,
                            branch            = customer.branch ?: "",
                            address           = customer.companyAddr ?: "",
                            selectedLat       = customer.companyLat,
                            selectedLng       = customer.companyLong,
                            custType          = customer.custType,
                            companyStatus     = customer.companyStatus ?: "customer",
                            firstCustomerDate = customer.firstCustomerDate,
                            isLoading         = false
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isLoading = false, saveError = e.message) }
                }
            )
        }
    }

    fun onEvent(event: AddCustomerEvent) {
        when (event) {
            is AddCustomerEvent.LoadCustomer ->
                loadCustomer(event.id)

            is AddCustomerEvent.CompanyNameChanged ->
                _uiState.update { it.copy(companyName = event.value, companyNameError = null) }

            is AddCustomerEvent.BranchChanged ->
                _uiState.update { it.copy(branch = event.value) }

            is AddCustomerEvent.AddressChanged ->
                _uiState.update { it.copy(address = event.value) }

            is AddCustomerEvent.LocationPicked ->
                _uiState.update { it.copy(selectedLat = event.lat, selectedLng = event.lng) }

            is AddCustomerEvent.CustTypeChanged ->
                _uiState.update { it.copy(custType = event.value, custTypeError = null) }

            is AddCustomerEvent.StatusChanged ->
                _uiState.update { it.copy(companyStatus = event.value) }

            is AddCustomerEvent.ProjectSelected ->
                _uiState.update { it.copy(selectedProjectId = event.id, selectedProjectName = event.name) }

            is AddCustomerEvent.FirstCustomerDateChanged ->
                _uiState.update { it.copy(firstCustomerDate = event.value.ifBlank { null }) }

            is AddCustomerEvent.UseCurrentLocation ->
                _uiState.update { it.copy(selectedLat = 13.7563, selectedLng = 100.5018) }

            is AddCustomerEvent.Save -> save()
        }
    }

    private fun validate(): Boolean {
        var valid = true
        val s = _uiState.value
        if (s.companyName.isBlank()) {
            _uiState.update { it.copy(companyNameError = "กรุณากรอกชื่อบริษัท") }
            valid = false
        }
        if (s.custType.isNullOrBlank()) {
            _uiState.update { it.copy(custTypeError = "กรุณาเลือกประเภทลูกค้า") }
            valid = false
        }
        return valid
    }

    private fun save() {
        if (!validate()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, saveError = null) }
            val s = _uiState.value

            // ✅ ดึง branchId จาก user ที่ล็อกอินอยู่
            val userBranchId = authRepo.currentUser()?.teamId

            val customer = Customer(
                custId            = s.custId ?: "CUST-${UUID.randomUUID().toString().take(8).uppercase()}",
                companyName       = s.companyName,
                branchId          = userBranchId,        // ✅ set branchId เพื่อให้คนในสาขาเดียวกันเห็น
                branch            = s.branch.ifBlank { null },
                custType          = s.custType,
                companyAddr       = s.address.ifBlank { null },
                companyLat        = s.selectedLat,
                companyLong       = s.selectedLng,
                companyStatus     = s.companyStatus,
                firstCustomerDate = s.firstCustomerDate
            )

            // ✅ Create = POST, Edit = PATCH
            val result = if (s.custId != null) {
                customerRepo.updateCustomer(s.custId, customer)
            } else {
                customerRepo.addCustomer(customer)
            }

            result.fold(
                onSuccess = { _uiState.update { it.copy(isLoading = false, isSaved = true) } },
                onFailure = { e ->
                    _uiState.update { it.copy(isLoading = false, saveError = e.message) }
                }
            )
        }
    }
}