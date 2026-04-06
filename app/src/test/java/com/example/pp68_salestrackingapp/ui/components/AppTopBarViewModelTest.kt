package com.example.pp68_salestrackingapp.ui.components

import com.example.pp68_salestrackingapp.data.model.AuthUser
import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppTopBarViewModelTest {

    private val authRepository = mockk<AuthRepository>()

    @Test
    fun `init user should come from currentUser`() {
        val user = AuthUser(userId = "U1", email = "u@test.com", role = "sale")
        every { authRepository.currentUser() } returns user

        val viewModel = AppTopBarViewModel(authRepository)

        assertEquals(user, viewModel.user.value)
    }

    @Test
    fun `refreshUser should update user state from repository`() {
        val oldUser = AuthUser(userId = "U1", email = "u1@test.com", role = "sale")
        val newUser = AuthUser(userId = "U2", email = "u2@test.com", role = "manager")
        every { authRepository.currentUser() } returnsMany listOf(oldUser, newUser)

        val viewModel = AppTopBarViewModel(authRepository)
        viewModel.refreshUser()

        assertEquals(newUser, viewModel.user.value)
    }

    @Test
    fun `refreshUser should allow null user`() {
        val oldUser = AuthUser(userId = "U1", email = "u1@test.com", role = "sale")
        every { authRepository.currentUser() } returnsMany listOf(oldUser, null)

        val viewModel = AppTopBarViewModel(authRepository)
        viewModel.refreshUser()

        assertNull(viewModel.user.value)
    }
}
