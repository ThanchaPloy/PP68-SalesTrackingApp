package com.example.pp68_salestrackingapp.ui.viewmodels.contact

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.pp68_salestrackingapp.data.model.AuthUser
import com.example.pp68_salestrackingapp.data.model.ContactPerson
import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import com.example.pp68_salestrackingapp.data.repository.ContactRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ContactListUiState(
    val isLoading: Boolean = false,
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
    private val _searchQuery = MutableStateFlow("")
    private val _selectedInitial = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ContactListUiState> = _uiState.asStateFlow()
    val selectedInitial: StateFlow<String?> = _selectedInitial.asStateFlow()

    val contacts: Flow<PagingData<ContactPerson>> = combine(
        _searchQuery.debounce(300),
        _selectedInitial
    ) { query, initial -> query to initial }
        .distinctUntilChanged()
        .flatMapLatest { (query, initial) -> repo.getContactsPagingFlow(query, initial) }
        .catch { e ->
            _uiState.update { it.copy(error = e.message) }
            emit(PagingData.empty())
        }
        .cachedIn(viewModelScope)

    init {
        refreshData()
    }

    fun refreshData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repo.refreshContacts().onFailure { e ->
                _uiState.update { it.copy(error = e.message) }
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun onSearchChange(query: String) {
        _searchQuery.value = query
        if (query.isNotBlank()) _selectedInitial.value = null
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onInitialSelected(initial: String?) {
        _selectedInitial.value = initial
    }
}
