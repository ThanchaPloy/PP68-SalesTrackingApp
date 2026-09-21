package com.example.pp68_salestrackingapp.ui.viewmodels.customer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pp68_salestrackingapp.data.model.ContactPerson
import com.example.pp68_salestrackingapp.data.model.Customer
import com.example.pp68_salestrackingapp.data.model.Project
import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import com.example.pp68_salestrackingapp.data.repository.CustomerRepository
import com.example.pp68_salestrackingapp.data.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CustomerDetailViewModel @Inject constructor(
    private val customerRepo: CustomerRepository,
    private val projectRepo: ProjectRepository,
    private val authRepo: AuthRepository
) : ViewModel() {

    private val _customer = MutableStateFlow<Customer?>(null)
    val customer: StateFlow<Customer?> = _customer.asStateFlow()

    private val _contacts = MutableStateFlow<List<ContactPerson>>(emptyList())
    val contacts: StateFlow<List<ContactPerson>> = _contacts.asStateFlow()

    private val _activeProjects = MutableStateFlow<List<Project>>(emptyList())
    val activeProjects: StateFlow<List<Project>> = _activeProjects.asStateFlow()

    private val _closedProjects = MutableStateFlow<List<Project>>(emptyList())
    val closedProjects: StateFlow<List<Project>> = _closedProjects.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _deleteSuccess = MutableStateFlow(false)
    val deleteSuccess: StateFlow<Boolean> = _deleteSuccess.asStateFlow()

    private val _deleteError = MutableStateFlow<String?>(null)
    val deleteError: StateFlow<String?> = _deleteError.asStateFlow()

    fun clearDeleteError() { _deleteError.value = null }

    private var currentCustId: String? = null

    fun load(custId: String) {
        currentCustId = custId
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // 1. ดึงข้อมูล Customer
                customerRepo.getCustomerById(custId).onSuccess { customer ->
                    _customer.value = customer
                }

                // 2. ดึง Contact (เฉพาะที่ตนเองสร้าง)
                refreshContacts(custId)

                // 3. ดึง Project แยก active/closed
                val closedStatuses = setOf("Completed", "Lost", "Failed")
                val allProjects = projectRepo.getAllProjectsFlow().first()
                    .filter { it.custId == custId }

                _activeProjects.value = allProjects.filter { it.projectStatus !in closedStatuses }
                _closedProjects.value = allProjects.filter { it.projectStatus in closedStatuses }

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun refreshContacts(custId: String) {
        val currentUserId = authRepo.currentUser()?.userId
        customerRepo.getContactPersons(custId, currentUserId).onSuccess { contacts ->
            _contacts.value = contacts
        }
    }

    fun deleteContact(contactId: String) {
        viewModelScope.launch {
            customerRepo.deleteContact(contactId).fold(
                onSuccess = { currentCustId?.let { refreshContacts(it) } },
                // เดิมมีแต่ onSuccess — ลบไม่สำเร็จก็ไม่มีอะไรเกิดขึ้น รายชื่อยังอยู่เหมือนเดิม
                // และผู้ใช้ไม่รู้ว่าเพราะอะไร
                onFailure = { e -> _deleteError.value = e.message ?: "ลบผู้ติดต่อไม่สำเร็จ" }
            )
        }
    }

    fun deleteCustomer() {
        val custId = currentCustId ?: return
        viewModelScope.launch {
            _isLoading.value = true
            _deleteError.value = null
            customerRepo.deleteCustomer(custId).fold(
                onSuccess = {
                    _deleteSuccess.value = true
                },
                // เดิมตรงนี้เป็น lambda ว่างพร้อมคอมเมนต์ "Handle error" — กดลบแล้วเงียบสนิท
                // ผู้ใช้ไม่รู้ว่าเกิดอะไรขึ้น และกดซ้ำได้เรื่อย ๆ โดยไม่มีอะไรเปลี่ยน
                onFailure = { e ->
                    _deleteError.value = e.message ?: "ลบไม่สำเร็จ"
                }
            )
            _isLoading.value = false
        }
    }

}
