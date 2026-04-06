package com.example.pp68_salestrackingapp.ui.viewmodels.auth

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.pp68_salestrackingapp.data.model.Branch
import com.example.pp68_salestrackingapp.data.model.LoginResponse
import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import com.example.pp68_salestrackingapp.data.repository.BranchRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.regex.Matcher
import java.util.regex.Pattern

@OptIn(ExperimentalCoroutinesApi::class)
class RegisterViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private val authRepository = mockk<AuthRepository>(relaxed = true)
    private val branchRepository = mockk<BranchRepository>(relaxed = true)
    private lateinit var viewModel: RegisterViewModel

    private val sampleBranches = listOf(
        Branch(branchId = "B01", branchName = "สาขากรุงเทพ"),
        Branch(branchId = "B02", branchName = "สาขาเชียงใหม่")
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun createViewModel() {
        viewModel = RegisterViewModel(authRepository, branchRepository)
    }

    private fun setupEmailPatternMock(matches: Boolean) {
        mockkStatic(android.util.Patterns::class)
        val mockPattern = mockk<Pattern>()
        val mockMatcher = mockk<Matcher>()
        every { android.util.Patterns.EMAIL_ADDRESS } returns mockPattern
        every { mockPattern.matcher(any<CharSequence>()) } returns mockMatcher
        every { mockMatcher.matches() } returns matches
    }

    // TC-UNIT-VM-REG-01
    @Test
    fun `initial state should have empty fields and not be loading or success`() = runTest {
        coEvery { branchRepository.syncFromRemote() } returns Result.success(Unit)
        coEvery { branchRepository.observeBranches() } returns sampleBranches
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("", state.fullName)
        assertEquals("", state.email)
        assertEquals("", state.password)
        assertEquals("", state.selectedBranchId)
        assertFalse(state.isSuccess)
        assertNull(state.error)
    }

    // TC-UNIT-VM-REG-02
    @Test
    fun `loadBranches on init should populate branches list`() = runTest {
        coEvery { branchRepository.syncFromRemote() } returns Result.success(Unit)
        coEvery { branchRepository.observeBranches() } returns sampleBranches
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(sampleBranches, viewModel.uiState.value.branches)
    }

    // TC-UNIT-VM-REG-03
    @Test
    fun `loadBranches failure should set error in state`() = runTest {
        coEvery { branchRepository.syncFromRemote() } throws Exception("Network error")
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.error)
    }

    // TC-UNIT-VM-REG-04
    @Test
    fun `onFullNameChange should update fullName in state`() = runTest {
        coEvery { branchRepository.syncFromRemote() } returns Result.success(Unit)
        coEvery { branchRepository.observeBranches() } returns emptyList()
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onFullNameChange("สมชาย ใจดี")
        assertEquals("สมชาย ใจดี", viewModel.uiState.value.fullName)
    }

    // TC-UNIT-VM-REG-05
    @Test
    fun `onEmailChange should update email in state`() = runTest {
        coEvery { branchRepository.syncFromRemote() } returns Result.success(Unit)
        coEvery { branchRepository.observeBranches() } returns emptyList()
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEmailChange("test@example.com")
        assertEquals("test@example.com", viewModel.uiState.value.email)
    }

    // TC-UNIT-VM-REG-06
    @Test
    fun `onPasswordChange should update password in state`() = runTest {
        coEvery { branchRepository.syncFromRemote() } returns Result.success(Unit)
        coEvery { branchRepository.observeBranches() } returns emptyList()
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onPasswordChange("secure123")
        assertEquals("secure123", viewModel.uiState.value.password)
    }

    // TC-UNIT-VM-REG-07
    @Test
    fun `onBranchSelected should update selectedBranchId and name`() = runTest {
        coEvery { branchRepository.syncFromRemote() } returns Result.success(Unit)
        coEvery { branchRepository.observeBranches() } returns sampleBranches
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onBranchSelected(0)
        assertEquals("B01", viewModel.uiState.value.selectedBranchId)
        assertEquals("สาขากรุงเทพ", viewModel.uiState.value.selectedBranchName)
    }

    // TC-UNIT-VM-REG-08
    @Test
    fun `onBranchSelected with out-of-bounds index should not update state`() = runTest {
        coEvery { branchRepository.syncFromRemote() } returns Result.success(Unit)
        coEvery { branchRepository.observeBranches() } returns sampleBranches
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onBranchSelected(99)
        assertEquals("", viewModel.uiState.value.selectedBranchId)
        assertEquals("", viewModel.uiState.value.selectedBranchName)
    }

    // TC-UNIT-VM-REG-09
    @Test
    fun `register with blank fullName should set error`() = runTest {
        coEvery { branchRepository.syncFromRemote() } returns Result.success(Unit)
        coEvery { branchRepository.observeBranches() } returns sampleBranches
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onFullNameChange("")
        viewModel.onEmailChange("test@example.com")
        viewModel.onPasswordChange("pass123")
        viewModel.onBranchSelected(0)
        viewModel.register()

        assertEquals("กรุณากรอกข้อมูลให้ครบถ้วน", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isSuccess)
    }

    // TC-UNIT-VM-REG-10
    @Test
    fun `register with blank email should set error`() = runTest {
        coEvery { branchRepository.syncFromRemote() } returns Result.success(Unit)
        coEvery { branchRepository.observeBranches() } returns sampleBranches
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onFullNameChange("John")
        viewModel.onEmailChange("")
        viewModel.onPasswordChange("pass123")
        viewModel.onBranchSelected(0)
        viewModel.register()

        assertEquals("กรุณากรอกข้อมูลให้ครบถ้วน", viewModel.uiState.value.error)
    }

    // TC-UNIT-VM-REG-11
    @Test
    fun `register with blank password should set error`() = runTest {
        coEvery { branchRepository.syncFromRemote() } returns Result.success(Unit)
        coEvery { branchRepository.observeBranches() } returns sampleBranches
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onFullNameChange("John")
        viewModel.onEmailChange("test@example.com")
        viewModel.onPasswordChange("")
        viewModel.onBranchSelected(0)
        viewModel.register()

        assertEquals("กรุณากรอกข้อมูลให้ครบถ้วน", viewModel.uiState.value.error)
    }

    // TC-UNIT-VM-REG-12
    @Test
    fun `register with no branch selected should set error`() = runTest {
        coEvery { branchRepository.syncFromRemote() } returns Result.success(Unit)
        coEvery { branchRepository.observeBranches() } returns emptyList()
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onFullNameChange("John")
        viewModel.onEmailChange("test@example.com")
        viewModel.onPasswordChange("pass123")
        // No branch selected
        viewModel.register()

        assertEquals("กรุณากรอกข้อมูลให้ครบถ้วน", viewModel.uiState.value.error)
    }

    // TC-UNIT-VM-REG-13
    @Test
    fun `register with invalid email format should set error`() = runTest {
        setupEmailPatternMock(matches = false)
        coEvery { branchRepository.syncFromRemote() } returns Result.success(Unit)
        coEvery { branchRepository.observeBranches() } returns sampleBranches
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onFullNameChange("John")
        viewModel.onEmailChange("not-valid-email")
        viewModel.onPasswordChange("pass123")
        viewModel.onBranchSelected(0)
        viewModel.register()

        assertEquals("รูปแบบอีเมลไม่ถูกต้อง", viewModel.uiState.value.error)
    }

    // TC-UNIT-VM-REG-14
    @Test
    fun `register with password shorter than 6 chars should set error`() = runTest {
        setupEmailPatternMock(matches = true)
        coEvery { branchRepository.syncFromRemote() } returns Result.success(Unit)
        coEvery { branchRepository.observeBranches() } returns sampleBranches
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onFullNameChange("John")
        viewModel.onEmailChange("test@example.com")
        viewModel.onPasswordChange("12345")
        viewModel.onBranchSelected(0)
        viewModel.register()

        assertEquals("รหัสผ่านต้องมีอย่างน้อย 6 ตัวอักษร", viewModel.uiState.value.error)
    }

    // TC-UNIT-VM-REG-15
    @Test
    fun `register success should set isSuccess to true`() = runTest {
        setupEmailPatternMock(matches = true)
        coEvery { branchRepository.syncFromRemote() } returns Result.success(Unit)
        coEvery { branchRepository.observeBranches() } returns sampleBranches
        coEvery { authRepository.register(any(), any(), any(), any()) } returns
            Result.success(LoginResponse(token = "tok123", userId = "U01", role = "sale"))
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onFullNameChange("John Doe")
        viewModel.onEmailChange("john@example.com")
        viewModel.onPasswordChange("secure123")
        viewModel.onBranchSelected(0)
        viewModel.register()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSuccess)
        assertNull(viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    // TC-UNIT-VM-REG-16
    @Test
    fun `register failure should set error message`() = runTest {
        setupEmailPatternMock(matches = true)
        coEvery { branchRepository.syncFromRemote() } returns Result.success(Unit)
        coEvery { branchRepository.observeBranches() } returns sampleBranches
        coEvery { authRepository.register(any(), any(), any(), any()) } returns
            Result.failure(Exception("อีเมลนี้ถูกใช้งานแล้ว"))
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onFullNameChange("John Doe")
        viewModel.onEmailChange("john@example.com")
        viewModel.onPasswordChange("secure123")
        viewModel.onBranchSelected(0)
        viewModel.register()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSuccess)
        assertEquals("อีเมลนี้ถูกใช้งานแล้ว", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    // TC-UNIT-VM-REG-17
    @Test
    fun `loadBranches should toggle loading then set branches without error`() = runTest {
        coEvery { branchRepository.syncFromRemote() } coAnswers {
            kotlinx.coroutines.delay(200)
            Result.success(Unit)
        }
        coEvery { branchRepository.observeBranches() } returns sampleBranches
        createViewModel()

        testDispatcher.scheduler.advanceTimeBy(100)
        assertTrue(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.branches.isEmpty())
        assertNull(viewModel.uiState.value.error)

        testDispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(sampleBranches, state.branches)
        assertNull(state.error)
    }

    // TC-UNIT-VM-REG-18
    @Test
    fun `loadBranches failure should clear loading and keep branches empty`() = runTest {
        coEvery { branchRepository.syncFromRemote() } throws Exception("sync failed")
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.branches.isEmpty())
        assertEquals("sync failed", state.error)
    }

    // TC-UNIT-VM-REG-19
    @Test
    fun `register validation failure should not call auth repository`() = runTest {
        setupEmailPatternMock(matches = true)
        coEvery { branchRepository.syncFromRemote() } returns Result.success(Unit)
        coEvery { branchRepository.observeBranches() } returns sampleBranches
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onFullNameChange("John")
        viewModel.onEmailChange("")
        viewModel.onPasswordChange("secure123")
        viewModel.onBranchSelected(0)
        viewModel.register()

        coVerify(exactly = 0) { authRepository.register(any(), any(), any(), any()) }
    }

    // TC-UNIT-VM-REG-20
    @Test
    fun `register should call auth repository with exact state payload`() = runTest {
        setupEmailPatternMock(matches = true)
        coEvery { branchRepository.syncFromRemote() } returns Result.success(Unit)
        coEvery { branchRepository.observeBranches() } returns sampleBranches
        coEvery { authRepository.register(any(), any(), any(), any()) } returns
            Result.success(LoginResponse(token = "tok123", userId = "U01", role = "sale"))
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onFullNameChange("John Doe")
        viewModel.onEmailChange("john@example.com")
        viewModel.onPasswordChange("secure123")
        viewModel.onBranchSelected(1)
        viewModel.register()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) {
            authRepository.register(
                email = "john@example.com",
                password = "secure123",
                fullName = "John Doe",
                branchId = "B02"
            )
        }
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.isSuccess)
        assertNull(state.error)
        assertEquals("B02", state.selectedBranchId)
        assertEquals("สาขาเชียงใหม่", state.selectedBranchName)
    }

    // TC-UNIT-VM-REG-21
    @Test
    fun `register should clear stale error when request starts and keep form fields intact`() = runTest {
        setupEmailPatternMock(matches = true)
        coEvery { branchRepository.syncFromRemote() } returns Result.success(Unit)
        coEvery { branchRepository.observeBranches() } returns sampleBranches
        coEvery { authRepository.register(any(), any(), any(), any()) } coAnswers {
            kotlinx.coroutines.delay(200)
            Result.success(LoginResponse(token = "tok", userId = "U", role = "sale"))
        }
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onFullNameChange("John Doe")
        viewModel.onEmailChange("john@example.com")
        viewModel.onPasswordChange("secure123")
        viewModel.register()
        assertEquals("กรุณากรอกข้อมูลให้ครบถ้วน", viewModel.uiState.value.error)

        viewModel.onBranchSelected(0)
        viewModel.register()
        testDispatcher.scheduler.advanceTimeBy(50)

        val loadingState = viewModel.uiState.value
        assertTrue(loadingState.isLoading)
        assertNull(loadingState.error)
        assertEquals("John Doe", loadingState.fullName)
        assertEquals("john@example.com", loadingState.email)
        assertEquals("secure123", loadingState.password)
        assertEquals("B01", loadingState.selectedBranchId)

        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isSuccess)
    }

    // TC-UNIT-VM-REG-22
    @Test
    fun `register failure with null exception message should keep null error`() = runTest {
        setupEmailPatternMock(matches = true)
        coEvery { branchRepository.syncFromRemote() } returns Result.success(Unit)
        coEvery { branchRepository.observeBranches() } returns sampleBranches
        coEvery { authRepository.register(any(), any(), any(), any()) } returns
            Result.failure(Exception())
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onFullNameChange("John Doe")
        viewModel.onEmailChange("john@example.com")
        viewModel.onPasswordChange("secure123")
        viewModel.onBranchSelected(0)
        viewModel.register()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSuccess)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.error)
    }
}
