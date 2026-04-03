package com.example.pp68_salestrackingapp.data.repository

import com.example.pp68_salestrackingapp.data.local.AppDatabase
import com.example.pp68_salestrackingapp.data.model.*
import com.example.pp68_salestrackingapp.data.remote.ApiService
import com.example.pp68_salestrackingapp.data.remote.AuthService
import com.example.pp68_salestrackingapp.di.TokenManager
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import com.example.pp68_salestrackingapp.data.model.ChangePasswordResponse
import okhttp3.MediaType.Companion.toMediaType

@OptIn(ExperimentalCoroutinesApi::class)
class AuthRepositoryTest {

    private lateinit var repository: AuthRepository
    private val apiService: ApiService = mockk()
    private val authService: AuthService = mockk()
    private val tokenManager: TokenManager = mockk(relaxed = true)
    private val database: AppDatabase = mockk(relaxed = true)

    @Before
    fun setUp() {
        repository = AuthRepository(apiService, authService, tokenManager, database)
    }

    @Test
    fun `login successful - saves token and user data`() = runTest {
        // Arrange
        val email = "test@example.com"
        val password = "password"
        val loginResponse = LoginResponse(token = "token123", userId = "user1", role = "admin")
        val userDetail = UserDto(
            userId = "user1",
            fullName = "John Doe",
            email = email,
            branchId = "branch1"
        )
        val branch = Branch(branchId = "branch1", branchName = "Main Branch")

        coEvery { authService.login(any()) } returns Response.success(loginResponse)
        coEvery { apiService.getUserById("eq.user1") } returns Response.success(listOf(userDetail))
        coEvery { apiService.getBranchById("eq.branch1") } returns Response.success(listOf(branch))
        every { tokenManager.getFcmToken() } returns "fcm_token_123"
        coEvery { apiService.updateFcmToken(any(), any()) } returns Response.success(listOf(userDetail))

        // Act
        val result = repository.login(email, password)

        // Assert
        assertTrue(result.isSuccess)
        assertEquals(loginResponse, result.getOrNull())
        verify { tokenManager.saveToken("token123") }
        verify { database.clearAllTables() }
        verify {
            tokenManager.saveUserData(match {
                it.userId == "user1" && it.fullName == "John Doe" && it.branchName == "Main Branch"
            })
        }
    }

    @Test
    fun `login failure - returns error result`() = runTest {
        // Arrange
        coEvery { authService.login(any()) } returns Response.error(401, "".toResponseBody())

        // Act
        val result = repository.login("wrong@email.com", "wrong")

        // Assert
        assertTrue(result.isFailure)
        assertEquals("อีเมลหรือรหัสผ่านไม่ถูกต้อง", result.exceptionOrNull()?.message)
    }

    @Test
    fun `register successful - saves token and user data`() = runTest {
        // Arrange
        val email = "new@example.com"
        val loginResponse = LoginResponse(token = "new_token", userId = "user2", role = "user")
        val userDetail = UserDto(userId = "user2", fullName = "Jane Doe", email = email, branchId = "B1")
        val branch = Branch(branchId = "B1", branchName = "Branch 1")

        coEvery { authService.register(any()) } returns Response.success(loginResponse)
        coEvery { apiService.getUserById("eq.user2") } returns Response.success(listOf(userDetail))
        coEvery { apiService.getBranchById("eq.B1") } returns Response.success(listOf(branch))

        // Act
        val result = repository.register(email, "pass", "Jane Doe", "B1")

        // Assert
        assertTrue(result.isSuccess)
        verify { tokenManager.saveToken("new_token") }
        verify {
            tokenManager.saveUserData(match {
                it.userId == "user2" && it.fullName == "Jane Doe"
            })
        }
    }

    @Test
    fun `logout - clears data and token`() = runTest {
        // Act
        repository.logout()

        // Assert
        verify { database.clearAllTables() }
        verify { tokenManager.clearToken() }
    }

    @Test
    fun `isUserLoggedIn returns true when token exists`() = runTest {
        // Arrange
        every { tokenManager.getToken() } returns "some_token"

        // Act & Assert
        assertTrue(repository.isUserLoggedIn())
    }

    @Test
    fun `changePassword successful`() = runTest {
        // Arrange
        val currentUser = AuthUser(userId = "u1", email = "e", role = "r")
        every { tokenManager.getUserData() } returns currentUser
        coEvery { authService.changePassword(any()) } returns Response.success(ChangePasswordResponse(message = "Success"))

        // Act
        val result = repository.changePassword("old", "new")

        // Assert
        assertTrue(result.isSuccess)
        assertEquals("Success", result.getOrNull())
    }

    @Test
    fun `changePassword no current user should return failure`() = runTest {
        every { tokenManager.getUserData() } returns null

        val result = repository.changePassword("old", "new")

        assertTrue(result.isFailure)
        assertEquals("กรุณา login ใหม่", result.exceptionOrNull()?.message)
    }

    @Test
    fun `changePassword API error should return failure with error message`() = runTest {
        val currentUser = AuthUser(userId = "USR-001", email = "test@test.com", role = "sale")
        every { tokenManager.getUserData() } returns currentUser
        coEvery { authService.changePassword(any()) } returns
                Response.error(401, """{"error":"รหัสผ่านเดิมไม่ถูกต้อง"}"""
                    .toResponseBody("application/json".toMediaType()))

        val result = repository.changePassword("wrongold", "new")

        assertTrue(result.isFailure)
    }
}
