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

    fun load(custId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // 1. ดึงข้อมูล Customer
                customerRepo.getCustomerById(custId).onSuccess { customer ->
                    _customer.value = customer
                }

                // 2. ดึง Contact
                customerRepo.getContactPersons(custId).onSuccess { contacts ->
                    _contacts.value = contacts
                }

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

    fun logout() {
        viewModelScope.launch {
            authRepo.logout()
        }
    }
}
