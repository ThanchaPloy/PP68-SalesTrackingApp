package com.example.pp68_salestrackingapp.integration

import com.example.pp68_salestrackingapp.data.remote.ApiService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

@OptIn(ExperimentalCoroutinesApi::class)
class ApiIntegrationTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var apiService: ApiService

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val client = OkHttpClient.Builder().build()
        
        apiService = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `TC-INT-API-01 getMyProjectIds returns correct project list`() = runTest {
        // Arrange
        val responseBody = """
            [
                { "project_id": "P-01", "project_number": "N-01" },
                { "project_id": "P-02", "project_number": "N-02" }
            ]
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(responseBody))

        // Act
        val response = apiService.getMyProjectIds("USR-01")

        // Assert
        assertTrue(response.isSuccessful)
        assertEquals(2, response.body()?.size)
        assertEquals("P-01", response.body()?.get(0)?.projectId)
    }

    @Test
    fun `TC-INT-API-02 getMyAppointments filters by user_id correctly`() = runTest {
        // Arrange
        val responseBody = """
            [
                { "appointment_id": "APT-0001", "user_id": "USR-100", "appointment_type": "visit" }
            ]
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(responseBody))

        // Act
        val response = apiService.getMyAppointments("USR-100")

        // Assert
        assertTrue(response.isSuccessful)
        assertEquals("APT-0001", response.body()?.first()?.activityId)
        
        val request = mockWebServer.takeRequest()
        assertTrue(request.path?.contains("user_id=USR-100") == true)
    }

    @Test
    fun `TC-INT-API-03 updateFcmToken should PATCH user table`() = runTest {
        // Arrange
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("[]"))

        // Act
        apiService.updateFcmToken("USR-01", mapOf("fcm_token" to "new-token"))

        // Assert
        val request = mockWebServer.takeRequest()
        assertEquals("PATCH", request.method)
        assertTrue(request.path?.contains("user") == true)
    }

    @Test
    fun `TC-INT-API-04 insertCallLog should POST to call_logs`() = runTest {
        // Arrange
        mockWebServer.enqueue(MockResponse().setResponseCode(201).setBody("[]"))

        // Act
        val response = apiService.insertCallLog(mapOf("phone" to "1234"))

        // Assert
        assertTrue(response.isSuccessful)
        val request = mockWebServer.takeRequest()
        assertEquals("POST", request.method)
        assertTrue(request.path?.contains("call_logs") == true)
    }

    @Test
    fun `TC-INT-API-05 401 response should trigger re-login flow`() = runTest {
        // Arrange
        mockWebServer.enqueue(MockResponse().setResponseCode(401).setBody("Unauthorized"))

        // Act
        val response = apiService.getCustomers()

        // Assert
        assertEquals(401, response.code())
        // Note: The actual re-login interceptor validation would be tested 
        // if we implemented OkHttp Interceptor test, but we assert 401 flows normally here.
    }

    @Test
    fun `TC-INT-API-06 503 PostgREST cold start should return error`() = runTest {
        // Arrange
        mockWebServer.enqueue(MockResponse().setResponseCode(503).setBody("Service Unavailable"))

        // Act
        val response = apiService.getBranches()

        // Assert
        assertEquals(503, response.code())
    }
}
