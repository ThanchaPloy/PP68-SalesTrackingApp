package com.example.pp68_salestrackingapp.ui.navigation

import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SalesTrackingAppViewModelTest {

    private val authRepository = mockk<AuthRepository>()

    @Test
    fun `isLoggedIn returns true when repository says logged in`() {
        every { authRepository.isUserLoggedIn() } returns true

        val viewModel = SalesTrackingAppViewModel(authRepository)

        assertTrue(viewModel.isLoggedIn())
    }

    @Test
    fun `isLoggedIn returns false when repository says not logged in`() {
        every { authRepository.isUserLoggedIn() } returns false

        val viewModel = SalesTrackingAppViewModel(authRepository)

        assertFalse(viewModel.isLoggedIn())
    }
}
