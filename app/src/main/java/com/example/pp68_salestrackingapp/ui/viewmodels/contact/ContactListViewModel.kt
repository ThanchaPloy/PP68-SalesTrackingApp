package com.example.pp68_salestrackingapp.ui.viewmodels.contact

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pp68_salestrackingapp.data.model.AuthUser
import com.example.pp68_salestrackingapp.data.model.ContactPerson
import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import com.example.pp68_salestrackingapp.data.repository.ContactRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ContactListUiState(
    val isLoading: Boolean = false,
    val contacts: List<ContactPerson> = emptyList(),
    val error: String? = null,
    val searchQuery: String = "",
    val authUser: AuthUser? = null
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class ContactListViewModel @Inject constructor(
    private val repo: ContactRepository,
    private val authRepo: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ContactListUiState(authUser = authRepo.currentUser()))
    val uiState: StateFlow<ContactListUiState> = _uiState.asStateFlow()

    init {
        refreshData()
        observeContacts()
    }

    // ContactListViewModel.kt — แก้ observeContacts()
    private fun observeContacts() {
        viewModelScope.launch {
            val userId = authRepo.currentUser()?.userId ?: return@launch  // ✅ ดึง userId ก่อน
            _uiState.map { it.searchQuery }
                .distinctUntilChanged()
                .debounce(300)
                .flatMapLatest { query ->
                    if (query.isBlank()) repo.getContactsByUserFlow(userId)       // ✅
                    else repo.searchContactsByUserFlow(query, userId)              // ✅
                }
                .catch { e -> _uiState.update { it.copy(error = e.message) } }
                .collect { resultList -> _uiState.update { it.copy(contacts = resultList) } }
        }
    }

    fun refreshData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val userId = authRepo.currentUser()?.userId ?: return@launch
            repo.refreshContacts(userId)  // ✅ ส่ง userId
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun onSearchChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun logout() {
        viewModelScope.launch {
            authRepo.logout()
        }
    }
}
