package com.example.pp68_salestrackingapp.ui.viewmodels.activity

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.pp68_salestrackingapp.data.model.AuthUser
import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private val authRepository = mockk<AuthRepository>(relaxed = true)
    private lateinit var viewModel: SettingsViewModel

    private val mockUser = AuthUser(
        userId = "U01",
        email = "user@example.com",
        role = "sale",
        teamId = "T01",
        fullName = "John Doe",
        branchName = "Bangkok Branch"
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // TC-UNIT-VM-SETTINGS-01
    @Test
    fun `init should load current user into state`() {
        every { authRepository.currentUser() } returns mockUser
        viewModel = SettingsViewModel(authRepository)

        assertEquals(mockUser, viewModel.uiState.value.user)
    }

    // TC-UNIT-VM-SETTINGS-02
    @Test
    fun `init with no logged in user should set user to null`() {
        every { authRepository.currentUser() } returns null
        viewModel = SettingsViewModel(authRepository)

        assertNull(viewModel.uiState.value.user)
    }

    // TC-UNIT-VM-SETTINGS-03
    @Test
    fun `init should load user fullName correctly`() {
        every { authRepository.currentUser() } returns mockUser
        viewModel = SettingsViewModel(authRepository)

        assertEquals("John Doe", viewModel.uiState.value.user?.fullName)
    }

    // TC-UNIT-VM-SETTINGS-04
    @Test
    fun `init should load user email correctly`() {
        every { authRepository.currentUser() } returns mockUser
        viewModel = SettingsViewModel(authRepository)

        assertEquals("user@example.com", viewModel.uiState.value.user?.email)
    }

    // TC-UNIT-VM-SETTINGS-05
    @Test
    fun `init should load user role correctly`() {
        every { authRepository.currentUser() } returns mockUser
        viewModel = SettingsViewModel(authRepository)

        assertEquals("sale", viewModel.uiState.value.user?.role)
    }

    // TC-UNIT-VM-SETTINGS-06
    @Test
    fun `logout should call authRepository logout`() = runTest {
        every { authRepository.currentUser() } returns mockUser
        viewModel = SettingsViewModel(authRepository)

        viewModel.logout()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { authRepository.logout() }
    }
}
