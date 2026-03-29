package com.example.pp68_salestrackingapp.ui.components

import androidx.lifecycle.ViewModel
import com.example.pp68_salestrackingapp.data.model.AuthUser
import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class AppTopBarViewModel @Inject constructor(
    private val authRepo: AuthRepository
) : ViewModel() {

    private val _user = MutableStateFlow<AuthUser?>(authRepo.currentUser())
    val user: StateFlow<AuthUser?> = _user.asStateFlow()

    fun refreshUser() {
        _user.value = authRepo.currentUser()
    }
}
