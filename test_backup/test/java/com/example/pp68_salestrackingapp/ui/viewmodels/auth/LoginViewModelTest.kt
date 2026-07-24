package com.example.pp68_salestrackingapp.ui.viewmodels.auth

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.pp68_salestrackingapp.data.model.LoginResponse
import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import io.mockk.coEvery
import io.mockk.coVerify
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
class LoginViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private val authRepository = mockk<AuthRepository>(relaxed = true)
    private lateinit var viewModel: LoginViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = LoginViewModel(authRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // TC-UNIT-VM-LOGIN-01
    @Test
    fun `initial uiState should be Idle`() {
        assertTrue(viewModel.uiState.value is LoginUiState.Idle)
    }

    // TC-UNIT-VM-LOGIN-02
    @Test
    fun `login with blank email should emit Error state`() {
        viewModel.onUsernameChange("")
        viewModel.onPasswordChange("password123")

        viewModel.login()

        assertTrue(viewModel.uiState.value is LoginUiState.Error)
        assertEquals(
            "กรุณากรอกข้อมูลให้ครบถ้วน",
            (viewModel.uiState.value as LoginUiState.Error).message
        )
    }

    // TC-UNIT-VM-LOGIN-03
    @Test
    fun `login with blank password should emit Error state`() {
        viewModel.onUsernameChange("test@example.com")
        viewModel.onPasswordChange("")

        viewModel.login()

        assertTrue(viewModel.uiState.value is LoginUiState.Error)
        assertEquals(
            "กรุณากรอกข้อมูลให้ครบถ้วน",
            (viewModel.uiState.value as LoginUiState.Error).message
        )
    }

    // TC-UNIT-VM-LOGIN-04
    @Test
    fun `login with both fields blank should emit Error state`() {
        viewModel.onUsernameChange("")
        viewModel.onPasswordChange("")

        viewModel.login()

        assertTrue(viewModel.uiState.value is LoginUiState.Error)
        assertEquals(
            "กรุณากรอกข้อมูลให้ครบถ้วน",
            (viewModel.uiState.value as LoginUiState.Error).message
        )
    }

    // TC-UNIT-VM-LOGIN-05
    @Test
    fun `login success should emit Success state with AuthUser`() = runTest {
        val loginResponse = LoginResponse(
            token  = "token_abc",
            userId = "USR-001",
            role   = "sale"
        )
        coEvery { authRepository.login("test@example.com", "password123") } returns
                Result.success(loginResponse)

        viewModel.onUsernameChange("test@example.com")
        viewModel.onPasswordChange("password123")
        viewModel.login()

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is LoginUiState.Success)
        val user = (state as LoginUiState.Success).user
        assertEquals("USR-001", user.userId)
        assertEquals("test@example.com", user.email)
        assertEquals("sale", user.role)
    }

    // TC-UNIT-VM-LOGIN-06
    @Test
    fun `login failure should emit Error state with repository message`() = runTest {
        coEvery { authRepository.login(any(), any()) } returns
                Result.failure(Exception("อีเมลหรือรหัสผ่านไม่ถูกต้อง"))

        viewModel.onUsernameChange("test@example.com")
        viewModel.onPasswordChange("wrongpassword")
        viewModel.login()

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is LoginUiState.Error)
        assertEquals(
            "อีเมลหรือรหัสผ่านไม่ถูกต้อง",
            (state as LoginUiState.Error).message
        )
    }

    // TC-UNIT-VM-LOGIN-07
    @Test
    fun `login should emit Loading state before repository call completes`() = runTest {
        coEvery { authRepository.login(any(), any()) } coAnswers {
            kotlinx.coroutines.delay(500)
            Result.success(LoginResponse("t", "u", "r"))
        }

        viewModel.onUsernameChange("test@example.com")
        viewModel.onPasswordChange("password123")
        viewModel.login()

        testDispatcher.scheduler.advanceTimeBy(100)

        assertTrue(viewModel.uiState.value is LoginUiState.Loading)

        testDispatcher.scheduler.advanceUntilIdle()
    }

    // TC-UNIT-VM-LOGIN-08
    @Test
    fun `resetState should return to Idle and clear fields`() = runTest {
        coEvery { authRepository.login(any(), any()) } returns
                Result.success(LoginResponse("t", "u", "r"))

        viewModel.onUsernameChange("test@example.com")
        viewModel.onPasswordChange("password123")
        viewModel.login()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.resetState()

        assertTrue(viewModel.uiState.value is LoginUiState.Idle)
        assertEquals("", viewModel.username.value)
        assertEquals("", viewModel.password.value)
    }

    // TC-UNIT-VM-LOGIN-09
    @Test
    fun `onUsernameChange should update email StateFlow`() {
        viewModel.onUsernameChange("new@example.com")

        assertEquals("new@example.com", viewModel.username.value)
    }

    // TC-UNIT-VM-LOGIN-10
    @Test
    fun `onUsernameChange after Error should clear error state to Idle`() {
        viewModel.onUsernameChange("")
        viewModel.onPasswordChange("pass")
        viewModel.login()
        assertTrue(viewModel.uiState.value is LoginUiState.Error)

        viewModel.onUsernameChange("fix@example.com")

        assertTrue(viewModel.uiState.value is LoginUiState.Idle)
    }

    // TC-UNIT-VM-LOGIN-11
    @Test
    fun `onPasswordChange should update password StateFlow`() {
        viewModel.onPasswordChange("secret123")

        assertEquals("secret123", viewModel.password.value)
    }

    // TC-UNIT-VM-LOGIN-12
    @Test
    fun `login failure with null message should use fallback message`() = runTest {
        coEvery { authRepository.login(any(), any()) } returns
                Result.failure(Exception())

        viewModel.onUsernameChange("test@example.com")
        viewModel.onPasswordChange("password123")
        viewModel.login()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is LoginUiState.Error)
        assertEquals(
            "เกิดข้อผิดพลาดในการเข้าสู่ระบบ",
            (state as LoginUiState.Error).message
        )
    }



    // TC-UNIT-VM-LOGIN-14
    @Test
    fun `login should trim email before repository call and success user payload`() = runTest {
        val loginResponse = LoginResponse(
            token = "token_x",
            userId = "USR-TRIM",
            role = "sale"
        )
        coEvery { authRepository.login("trim@example.com", "password123") } returns
            Result.success(loginResponse)

        viewModel.onUsernameChange("  trim@example.com  ")
        viewModel.onPasswordChange("password123")
        viewModel.login()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is LoginUiState.Success)
        assertEquals("trim@example.com", (state as LoginUiState.Success).user.email)
        assertEquals("  trim@example.com  ", viewModel.username.value)
        coVerify(exactly = 1) { authRepository.login("trim@example.com", "password123") }
    }

    // TC-UNIT-VM-LOGIN-15
    @Test
    fun `onPasswordChange after Error should clear error state to Idle`() {
        viewModel.onUsernameChange("invalid-email")
        viewModel.onPasswordChange("password123")
        viewModel.login()
        assertTrue(viewModel.uiState.value is LoginUiState.Error)

        viewModel.onPasswordChange("new-password")

        assertTrue(viewModel.uiState.value is LoginUiState.Idle)
    }

    // TC-UNIT-VM-LOGIN-16
    @Test
    fun `login with network error should emit Error state with message`() = runTest {
        coEvery { authRepository.login(any(), any()) } returns
            Result.failure(Exception("Network error"))

        viewModel.onUsernameChange("test@example.com")
        viewModel.onPasswordChange("password123")
        viewModel.login()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is LoginUiState.Error)
        assertEquals("Network error", (state as LoginUiState.Error).message)
    }
}
