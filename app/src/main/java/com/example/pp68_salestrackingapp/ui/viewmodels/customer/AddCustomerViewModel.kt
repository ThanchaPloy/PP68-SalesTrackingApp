package com.example.pp68_salestrackingapp.ui.viewmodels.customer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pp68_salestrackingapp.data.model.Customer
import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import com.example.pp68_salestrackingapp.data.repository.CustomerRepository
import com.example.pp68_salestrackingapp.data.repository.ProjectRepository
import com.example.pp68_salestrackingapp.utils.DraftStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

// ─── UI State ─────────────────────────────────────────────────
data class AddCustomerUiState(
    val custId:              String? = null,
    val companyName:         String  = "",
    val vatRegistrationNo:   String  = "",
    val address:             String  = "",
    val selectedLat:         Double? = null,
    val selectedLng:         Double? = null,
    val custType:            String? = null,
    val companyStatus:       String  = "customer",

    // validation
    val companyNameError: String? = null,
    val custTypeError:    String? = null,

    // ui
    val isLoading:    Boolean = false,
    val isSaved:      Boolean = false,
    val saveError:    String? = null,
    // บันทึกสำเร็จแต่ยังไม่ถึง server — ไม่ใช่ error ผู้ใช้ทำงานต่อได้ แค่ควรรู้ว่ายังไม่จบ
    val saveNotice:   String? = null,

    // ฉบับร่างที่เคยบันทึกไว้ (ยังไม่หมดอายุ) — true เมื่อเจอ ให้หน้าจอถามว่าจะกู้คืนไหม
    val draftAvailable: Boolean = false
)

// ฟิลด์ที่มีความหมายพอจะเก็บเป็นฉบับร่าง — ไม่รวม flag ชั่วคราวของ UI (isLoading/isSaved/error ฯลฯ)
data class AddCustomerDraft(
    val companyName: String = "",
    val vatRegistrationNo: String = "",
    val address: String = "",
    val selectedLat: Double? = null,
    val selectedLng: Double? = null,
    val custType: String? = null,
    val companyStatus: String = "customer"
)

// ─── Events ───────────────────────────────────────────────────
sealed class AddCustomerEvent {
    data class LoadCustomer(val id: String) : AddCustomerEvent()
    data class CompanyNameChanged(val value: String) : AddCustomerEvent()
    data class VatRegistrationNoChanged(val value: String) : AddCustomerEvent()
    data class AddressChanged(val value: String)     : AddCustomerEvent()
    data class LocationPicked(val lat: Double, val lng: Double) : AddCustomerEvent()
    data class CustTypeChanged(val value: String)    : AddCustomerEvent()
    data class StatusChanged(val value: String)      : AddCustomerEvent()
    object UseCurrentLocation : AddCustomerEvent()
    object Save               : AddCustomerEvent()
    // เรียกตอนเปิดหน้านี้แบบสร้างใหม่ (ไม่มี custId) เพื่อเช็คว่ามีฉบับร่างเก่าค้างอยู่ไหม
    object CheckDraft         : AddCustomerEvent()
    object RestoreDraft       : AddCustomerEvent()
    object DismissDraftPrompt : AddCustomerEvent()
}

// ─── ViewModel ────────────────────────────────────────────────
@HiltViewModel
class AddCustomerViewModel @Inject constructor(
    private val customerRepo: CustomerRepository,
    private val authRepo:     AuthRepository,
    private val draftStore:   DraftStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddCustomerUiState())
    val uiState: StateFlow<AddCustomerUiState> = _uiState

    private var baseline: AddCustomerDraft = AddCustomerDraft()
    private var pendingDraft: AddCustomerDraft? = null

    private fun AddCustomerUiState.toDraft() = AddCustomerDraft(
        companyName, vatRegistrationNo, address, selectedLat, selectedLng, custType, companyStatus
    )

    private fun draftKey() = "add_customer:${_uiState.value.custId ?: "new"}"

    private fun checkForDraft() {
        val draft = draftStore.load(draftKey(), AddCustomerDraft::class.java) ?: return
        pendingDraft = draft
        _uiState.update { it.copy(draftAvailable = true) }
    }

    fun isDirty(): Boolean = _uiState.value.toDraft() != baseline

    fun saveDraft() { draftStore.save(draftKey(), _uiState.value.toDraft()) }

    fun discardDraft() { draftStore.clear(draftKey()) }

    private fun restoreDraft() {
        val d = pendingDraft ?: return
        _uiState.update {
            it.copy(
                companyName = d.companyName,
                vatRegistrationNo = d.vatRegistrationNo,
                address = d.address,
                selectedLat = d.selectedLat,
                selectedLng = d.selectedLng,
                custType = d.custType,
                companyStatus = d.companyStatus,
                draftAvailable = false
            )
        }
        pendingDraft = null
    }

    private fun dismissDraftPrompt() {
        pendingDraft = null
        _uiState.update { it.copy(draftAvailable = false) }
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
                            vatRegistrationNo = customer.vatRegistrationNo ?: "",
                            address           = customer.companyAddr ?: "",
                            selectedLat       = customer.companyLat,
                            selectedLng       = customer.companyLong,
                            custType          = customer.custType,
                            companyStatus     = when (customer.companyStatus) {
                                0    -> "new lead"
                                2    -> "inactive"
                                else -> "customer"
                            },
                            isLoading         = false
                        )
                    }
                    baseline = _uiState.value.toDraft()
                    checkForDraft()
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

            is AddCustomerEvent.VatRegistrationNoChanged ->
                _uiState.update { it.copy(vatRegistrationNo = event.value) }

            is AddCustomerEvent.AddressChanged ->
                _uiState.update { it.copy(address = event.value) }

            is AddCustomerEvent.LocationPicked ->
                _uiState.update { it.copy(selectedLat = event.lat, selectedLng = event.lng) }

            is AddCustomerEvent.CustTypeChanged ->
                _uiState.update { it.copy(custType = event.value, custTypeError = null) }

            is AddCustomerEvent.StatusChanged ->
                _uiState.update { it.copy(companyStatus = event.value) }



            is AddCustomerEvent.UseCurrentLocation ->
                _uiState.update { it.copy(selectedLat = 13.7563, selectedLng = 100.5018) }

            is AddCustomerEvent.Save -> save()

            is AddCustomerEvent.CheckDraft -> checkForDraft()
            is AddCustomerEvent.RestoreDraft -> restoreDraft()
            is AddCustomerEvent.DismissDraftPrompt -> dismissDraftPrompt()
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

            val currentUser   = authRepo.currentUser()
            val userBranchId  = currentUser?.teamId
            val userEmpCode   = currentUser?.userId

            val customer = Customer(
                custId        = s.custId ?: "TEMP-${UUID.randomUUID().toString().take(8).uppercase()}",
                companyName   = s.companyName,
                branchId          = userBranchId,
                vatRegistrationNo = s.vatRegistrationNo.ifBlank { null },
                custType      = s.custType,
                companyAddr   = s.address.ifBlank { null },
                companyLat    = s.selectedLat,
                companyLong   = s.selectedLng,
                companyStatus = when (s.companyStatus) {
                    "new lead" -> 0
                    "inactive" -> 2
                    else       -> 1
                },
                createdBy     = userEmpCode,
                createdAt     = s.custId?.let { null } ?: LocalDate.now().toString(),
                isLead        = true
            )

            // ✅ Create = POST, Edit = PATCH
            val result = if (s.custId != null) {
                customerRepo.updateCustomer(s.custId, customer).map { s.custId }
            } else {
                customerRepo.addCustomer(customer)
            }

            result.fold(
                onSuccess = { savedId ->
                    // id ที่ยังขึ้นต้นด้วย TEMP- แปลว่า server ยังไม่รับ (ออฟไลน์หรือตอบ error)
                    // ข้อมูลอยู่ในเครื่องแล้วและ outbox จะลองใหม่ให้ จึงบอกตามจริงแทนที่จะขึ้น error
                    val pending = savedId.startsWith("TEMP-")
                    discardDraft()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isSaved = true,
                            saveNotice = if (pending) "บันทึกลงเครื่องแล้ว กำลังรอส่งขึ้นเซิร์ฟเวอร์" else null
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isLoading = false, saveError = e.message) }
                }
            )
        }
    }
}
