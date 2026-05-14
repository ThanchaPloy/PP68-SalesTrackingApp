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
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CustomerDetailViewModel @Inject constructor(
    private val customerRepo: CustomerRepository,
    private val projectRepo:  ProjectRepository,
    private val authRepo:     AuthRepository
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

    private var currentCustId: String? = null

    // ✅ Offline-first: observe Flow + refresh background
    fun load(custId: String) {
        currentCustId = custId
        viewModelScope.launch {
            _isLoading.value = true

            // 1. โหลด customer
            customerRepo.getCustomerById(custId).onSuccess { _customer.value = it }

            // 2. Observe contacts เป็น Flow — อัปเดตอัตโนมัติ
            launch {
                customerRepo.getContactsForCustomerFlow(custId).collect {
                    _contacts.value = it
                }
            }

            // 3. Background fetch จาก Server → เขียน Local → Flow emit เอง
            launch {
                customerRepo.refreshContactsForCustomer(custId)
            }

            // 4. Projects
            launch {
                projectRepo.getAllProjectsFlow()
                    .map { it.filter { p -> p.custId == custId } }
                    .collect { allProjects ->
                        val closed = setOf("Completed", "Lost", "Failed")
                        _activeProjects.value = allProjects.filter { it.projectStatus !in closed }
                        _closedProjects.value = allProjects.filter { it.projectStatus in closed }
                    }
            }

            _isLoading.value = false
        }
    }

    fun deleteContact(contactId: String) {
        viewModelScope.launch {
            customerRepo.deleteContact(contactId).onSuccess {
                // ไม่ต้องสั่ง refreshContacts เองแล้ว เพราะ Flow ใน load() จะจัดการให้เมื่อ Local DB เปลี่ยน
            }
        }
    }

    fun deleteCustomer() {
        val custId = currentCustId ?: return
        viewModelScope.launch {
            _isLoading.value = true
            customerRepo.deleteCustomer(custId).fold(
                onSuccess = {
                    _deleteSuccess.value = true
                },
                onFailure = { e ->
                    // Handle error
                }
            )
            _isLoading.value = false
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepo.logout()
        }
    }
}
