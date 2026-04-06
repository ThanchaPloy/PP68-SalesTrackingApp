package com.example.pp68_salestrackingapp.ui.viewmodels.activity

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.pp68_salestrackingapp.data.model.AuthUser
import com.example.pp68_salestrackingapp.data.model.UserDto
import com.example.pp68_salestrackingapp.data.remote.ApiService
import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class EditProfileViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val dispatcher = StandardTestDispatcher()
    private val authRepo = mockk<AuthRepository>(relaxed = true)
    private val apiService = mockk<ApiService>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init should load profile and phone from api`() = runTest {
        every { authRepo.currentUser() } returns AuthUser(
            userId = "U1",
            email = "u@test.com",
            role = "sale",
            fullName = "Old Name",
            branchName = "Bangkok"
        )
        coEvery { apiService.getUserById("eq.U1") } returns Response.success(
            listOf(UserDto(userId = "U1", phoneNumber = "0811111111"))
        )

        val vm = EditProfileViewModel(authRepo, apiService)
        advanceUntilIdle()

        assertEquals("Old Name", vm.uiState.value.fullName)
        assertEquals("u@test.com", vm.uiState.value.email)
        assertEquals("Bangkok", vm.uiState.value.branchName)
        assertEquals("0811111111", vm.uiState.value.phoneNumber)
    }

    @Test
    fun `onFullNameChange and onPhoneChange should update state`() = runTest {
        every { authRepo.currentUser() } returns null
        val vm = EditProfileViewModel(authRepo, apiService)
        advanceUntilIdle()

        vm.onFullNameChange("New Name")
        vm.onPhoneChange("0899999999")

        assertEquals("New Name", vm.uiState.value.fullName)
        assertEquals("0899999999", vm.uiState.value.phoneNumber)
    }

    @Test
    fun `save success should update local user and mark saved`() = runTest {
        every { authRepo.currentUser() } returns AuthUser(
            userId = "U1",
            email = "u@test.com",
            role = "sale",
            fullName = "Old Name"
        )
        coEvery { apiService.getUserById(any()) } returns Response.success(emptyList())
        coEvery { apiService.updateUserProfile(any(), any()) } returns Response.success(
            listOf(UserDto(userId = "U1"))
        )

        val vm = EditProfileViewModel(authRepo, apiService)
        advanceUntilIdle()
        vm.onFullNameChange("New Name")
        vm.onPhoneChange("0812345678")
        vm.save()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isSaved)
        assertFalse(vm.uiState.value.isLoading)
        coVerify(exactly = 1) { authRepo.updateLocalUser(match { it.fullName == "New Name" }) }
    }

    @Test
    fun `save http failure should set error`() = runTest {
        every { authRepo.currentUser() } returns AuthUser("U1", "u@test.com", "sale")
        coEvery { apiService.getUserById(any()) } returns Response.success(emptyList())
        coEvery { apiService.updateUserProfile(any(), any()) } returns
            Response.error(500, "x".toResponseBody())

        val vm = EditProfileViewModel(authRepo, apiService)
        advanceUntilIdle()
        vm.onFullNameChange("Name")
        vm.save()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.error?.contains("HTTP 500") == true)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `save exception should set error`() = runTest {
        every { authRepo.currentUser() } returns AuthUser("U1", "u@test.com", "sale")
        coEvery { apiService.getUserById(any()) } returns Response.success(emptyList())
        coEvery { apiService.updateUserProfile(any(), any()) } throws RuntimeException("network")

        val vm = EditProfileViewModel(authRepo, apiService)
        advanceUntilIdle()
        vm.onFullNameChange("Name")
        vm.save()
        advanceUntilIdle()

        assertEquals("network", vm.uiState.value.error)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `init with null current user should keep default state and skip api`() = runTest {
        every { authRepo.currentUser() } returns null

        val vm = EditProfileViewModel(authRepo, apiService)
        advanceUntilIdle()

        assertEquals("", vm.uiState.value.fullName)
        assertEquals("", vm.uiState.value.email)
        assertEquals("", vm.uiState.value.phoneNumber)
        coVerify(exactly = 0) { apiService.getUserById(any()) }
    }

    @Test
    fun `init api exception should still keep basic profile and empty phone`() = runTest {
        every { authRepo.currentUser() } returns AuthUser(
            userId = "U1",
            email = "u@test.com",
            role = "sale",
            fullName = "John",
            branchName = "HQ"
        )
        coEvery { apiService.getUserById("eq.U1") } throws RuntimeException("down")

        val vm = EditProfileViewModel(authRepo, apiService)
        advanceUntilIdle()

        assertEquals("John", vm.uiState.value.fullName)
        assertEquals("u@test.com", vm.uiState.value.email)
        assertEquals("HQ", vm.uiState.value.branchName)
        assertEquals("", vm.uiState.value.phoneNumber)
    }

    @Test
    fun `save should trim name and skip phone update when blank`() = runTest {
        every { authRepo.currentUser() } returns AuthUser("U1", "u@test.com", "sale", fullName = "Old")
        coEvery { apiService.getUserById(any()) } returns Response.success(emptyList())
        coEvery { apiService.updateUserProfile(any(), any()) } returns Response.success(listOf(UserDto(userId = "U1")))

        val vm = EditProfileViewModel(authRepo, apiService)
        advanceUntilIdle()
        vm.onFullNameChange("  New Name  ")
        vm.onPhoneChange("   ")
        vm.save()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            apiService.updateUserProfile(
                "eq.U1",
                match { it["full_name"] == "New Name" && !it.containsKey("phone_number") }
            )
        }
        assertTrue(vm.uiState.value.isSaved)
    }

    @Test
    fun `save with null current user should exit early`() = runTest {
        every { authRepo.currentUser() } returns null

        val vm = EditProfileViewModel(authRepo, apiService)
        advanceUntilIdle()
        vm.save()
        advanceUntilIdle()

        coVerify(exactly = 0) { apiService.updateUserProfile(any(), any()) }
        assertFalse(vm.uiState.value.isLoading)
        assertNull(vm.uiState.value.error)
    }
}
