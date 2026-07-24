package com.example.pp68_salestrackingapp.integration

import com.example.pp68_salestrackingapp.data.remote.AuthService
import com.example.pp68_salestrackingapp.data.model.LoginRequest
import com.example.pp68_salestrackingapp.data.model.RegisterApiRequest
import com.example.pp68_salestrackingapp.data.model.ChangePasswordRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

@OptIn(ExperimentalCoroutinesApi::class)
class CloudFunctionIntegrationTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var authService: AuthService

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val client = OkHttpClient.Builder().build()
        
        authService = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AuthService::class.java)
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `TC-INT-CF-01 login-api returns JWT token on success`() = runTest {
        // Arrange
        val responseBody = """
            {
                "token": "eyJhbGciOiJIUzI1NiIsInR5c...",
                "user_id": "USR-0002",
                "role": "sale"
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(responseBody))

        // Act
        val response = authService.login(LoginRequest("test@test.com", "password123"))

        // Assert
        assertTrue(response.isSuccessful)
        assertNotNull(response.body()?.token)
        assertEquals("USR-0002", response.body()?.userId)
    }

    @Test
    fun `TC-INT-CF-02 login-api returns 401 on wrong password`() = runTest {
        // Arrange
        val responseBody = """{ "error": "Invalid password" }"""
        mockWebServer.enqueue(MockResponse().setResponseCode(401).setBody(responseBody))

        // Act
        val response = authService.login(LoginRequest("test@test.com", "wrongpass"))

        // Assert
        assertEquals(401, response.code())
    }

    @Test
    fun `TC-INT-CF-03 register-api creates new user and returns token`() = runTest {
        // Arrange
        val responseBody = """
            {
                "token": "new_jwt_token",
                "user_id": "USR-0047",
                "role": "sale"
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(201).setBody(responseBody))

        // Act
        val response = authService.register(
            RegisterApiRequest("new@test.com", "password", "New User", "BR-01")
        )

        // Assert
        assertTrue(response.isSuccessful)
        assertEquals("USR-0047", response.body()?.userId)
    }

    @Test
    fun `TC-INT-CF-04 change-password-api succeeds with correct old password`() = runTest {
        // Arrange
        val responseBody = """{ "message": "Password updated successfully" }"""
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(responseBody))

        // Act
        val response = authService.changePassword(
            ChangePasswordRequest("USR-0002", "oldpass", "newpass")
        )

        // Assert
        assertTrue(response.isSuccessful)
        assertEquals("Password updated successfully", response.body()?.message)
    }
}
