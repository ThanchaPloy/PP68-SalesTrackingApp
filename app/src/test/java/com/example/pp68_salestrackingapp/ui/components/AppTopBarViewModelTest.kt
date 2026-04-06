package com.example.pp68_salestrackingapp.ui.components

import com.example.pp68_salestrackingapp.data.model.AuthUser
import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
        verify(exactly = 1) { authRepository.currentUser() }
    }

    @Test
    fun `refreshUser should update user state from repository`() {
        val oldUser = AuthUser(userId = "U1", email = "u1@test.com", role = "sale")
        val newUser = AuthUser(userId = "U2", email = "u2@test.com", role = "manager")
        every { authRepository.currentUser() } returnsMany listOf(oldUser, newUser)

        val viewModel = AppTopBarViewModel(authRepository)
        viewModel.refreshUser()

        assertEquals(newUser, viewModel.user.value)
        verify(exactly = 2) { authRepository.currentUser() }
    }

    @Test
    fun `refreshUser should allow null user`() {
        val oldUser = AuthUser(userId = "U1", email = "u1@test.com", role = "sale")
        every { authRepository.currentUser() } returnsMany listOf(oldUser, null)

        val viewModel = AppTopBarViewModel(authRepository)
        viewModel.refreshUser()

        assertNull(viewModel.user.value)
        verify(exactly = 2) { authRepository.currentUser() }
    }

    @Test
    fun `refreshUser should update from null to user`() {
        val newUser = AuthUser(userId = "U9", email = "u9@test.com", role = "manager")
        every { authRepository.currentUser() } returnsMany listOf(null, newUser)

        val viewModel = AppTopBarViewModel(authRepository)
        assertNull(viewModel.user.value)

        viewModel.refreshUser()
        assertEquals(newUser, viewModel.user.value)
        verify(exactly = 2) { authRepository.currentUser() }
    }
}
