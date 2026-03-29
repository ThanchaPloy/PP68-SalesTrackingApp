package com.example.pp68_salestrackingapp.ui.navigation

import androidx.lifecycle.ViewModel
import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SalesTrackingAppViewModel @Inject constructor(
    private val authRepo: AuthRepository
) : ViewModel() {
    fun isLoggedIn(): Boolean = authRepo.isUserLoggedIn()
}
