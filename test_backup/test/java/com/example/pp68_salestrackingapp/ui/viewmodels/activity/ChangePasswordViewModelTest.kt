package com.example.pp68_salestrackingapp.ui.viewmodels.activity

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
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
class ChangePasswordViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private val authRepository = mockk<AuthRepository>(relaxed = true)
    private lateinit var viewModel: ChangePasswordViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = ChangePasswordViewModel(authRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // TC-UNIT-VM-CHPWD-01
    @Test
    fun `initial state should be empty with no loading or success`() {
        val state = viewModel.uiState.value
        assertEquals("", state.oldPassword)
        assertEquals("", state.newPassword)
        assertEquals("", state.confirmPassword)
        assertFalse(state.isLoading)
        assertFalse(state.isSuccess)
        assertNull(state.error)
    }

    // TC-UNIT-VM-CHPWD-02
    @Test
    fun `onOldPasswordChange should update oldPassword and clear error`() {
        viewModel.onOldPasswordChange("oldPass")
        assertEquals("oldPass", viewModel.uiState.value.oldPassword)
        assertNull(viewModel.uiState.value.error)
    }

    // TC-UNIT-VM-CHPWD-03
    @Test
    fun `onNewPasswordChange should update newPassword and clear error`() {
        viewModel.onNewPasswordChange("newPass123")
        assertEquals("newPass123", viewModel.uiState.value.newPassword)
        assertNull(viewModel.uiState.value.error)
    }

    // TC-UNIT-VM-CHPWD-04
    @Test
    fun `onConfirmPasswordChange should update confirmPassword and clear error`() {
        viewModel.onConfirmPasswordChange("newPass123")
        assertEquals("newPass123", viewModel.uiState.value.confirmPassword)
        assertNull(viewModel.uiState.value.error)
    }

    // TC-UNIT-VM-CHPWD-05
    @Test
    fun `save with blank oldPassword should set error and not call repo`() {
        viewModel.onOldPasswordChange("")
        viewModel.onNewPasswordChange("newPass123")
        viewModel.onConfirmPasswordChange("newPass123")
        viewModel.save()

        assertEquals("กรุณากรอกข้อมูลให้ครบ", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isSuccess)
    }

    // TC-UNIT-VM-CHPWD-06
    @Test
    fun `save with blank newPassword should set error`() {
        viewModel.onOldPasswordChange("oldPass")
        viewModel.onNewPasswordChange("")
        viewModel.onConfirmPasswordChange("newPass123")
        viewModel.save()

        assertEquals("กรุณากรอกข้อมูลให้ครบ", viewModel.uiState.value.error)
    }

    // TC-UNIT-VM-CHPWD-07
    @Test
    fun `save with blank confirmPassword should set error`() {
        viewModel.onOldPasswordChange("oldPass")
        viewModel.onNewPasswordChange("newPass123")
        viewModel.onConfirmPasswordChange("")
        viewModel.save()

        assertEquals("กรุณากรอกข้อมูลให้ครบ", viewModel.uiState.value.error)
    }

    // TC-UNIT-VM-CHPWD-08
    @Test
    fun `save with newPassword shorter than 6 chars should set error`() {
        viewModel.onOldPasswordChange("oldPass")
        viewModel.onNewPasswordChange("12345")
        viewModel.onConfirmPasswordChange("12345")
        viewModel.save()

        assertEquals("รหัสผ่านใหม่ต้องมีอย่างน้อย 6 ตัวอักษร", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isSuccess)
    }

    // TC-UNIT-VM-CHPWD-09
    @Test
    fun `save with mismatched passwords should set error`() {
        viewModel.onOldPasswordChange("oldPass")
        viewModel.onNewPasswordChange("newPass123")
        viewModel.onConfirmPasswordChange("differentPass")
        viewModel.save()

        assertEquals("รหัสผ่านใหม่ไม่ตรงกัน", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isSuccess)
    }

    // TC-UNIT-VM-CHPWD-10
    @Test
    fun `save success should set isSuccess to true and clear loading`() = runTest {
        coEvery { authRepository.changePassword(any(), any()) } returns Result.success("Success")

        viewModel.onOldPasswordChange("oldPass")
        viewModel.onNewPasswordChange("newPass123")
        viewModel.onConfirmPasswordChange("newPass123")
        viewModel.save()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSuccess)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.error)
    }

    // TC-UNIT-VM-CHPWD-11
    @Test
    fun `save failure should set error message and clear loading`() = runTest {
        coEvery { authRepository.changePassword(any(), any()) } returns
            Result.failure(Exception("รหัสผ่านเดิมไม่ถูกต้อง"))

        viewModel.onOldPasswordChange("wrongOld")
        viewModel.onNewPasswordChange("newPass123")
        viewModel.onConfirmPasswordChange("newPass123")
        viewModel.save()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSuccess)
        assertEquals("รหัสผ่านเดิมไม่ถูกต้อง", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    // TC-UNIT-VM-CHPWD-12
    @Test
    fun `save should call authRepository changePassword with correct parameters`() = runTest {
        coEvery { authRepository.changePassword(any(), any()) } returns Result.success("Success")

        viewModel.onOldPasswordChange("myOldPass")
        viewModel.onNewPasswordChange("myNewPass123")
        viewModel.onConfirmPasswordChange("myNewPass123")
        viewModel.save()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { authRepository.changePassword("myOldPass", "myNewPass123") }
    }

    // TC-UNIT-VM-CHPWD-13
    @Test
    fun `save should emit Loading state before repository call completes`() = runTest {
        coEvery { authRepository.changePassword(any(), any()) } coAnswers {
            kotlinx.coroutines.delay(500)
            Result.success("Success")
        }

        viewModel.onOldPasswordChange("oldPass")
        viewModel.onNewPasswordChange("newPass123")
        viewModel.onConfirmPasswordChange("newPass123")
        viewModel.save()
        testDispatcher.scheduler.advanceTimeBy(100)

        assertTrue(viewModel.uiState.value.isLoading)

        testDispatcher.scheduler.advanceUntilIdle()
    }

    // TC-UNIT-VM-CHPWD-14
    @Test
    fun `input change after validation error should clear error`() {
        viewModel.onOldPasswordChange("old")
        viewModel.onNewPasswordChange("12345")
        viewModel.onConfirmPasswordChange("12345")
        viewModel.save()
        assertEquals("รหัสผ่านใหม่ต้องมีอย่างน้อย 6 ตัวอักษร", viewModel.uiState.value.error)

        viewModel.onNewPasswordChange("newSecure123")
        assertNull(viewModel.uiState.value.error)
    }
}
