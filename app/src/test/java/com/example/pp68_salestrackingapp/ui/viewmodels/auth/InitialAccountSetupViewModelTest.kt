package com.example.pp68_salestrackingapp.ui.viewmodels.auth

import com.example.pp68_salestrackingapp.data.model.InitialSetupInfo
import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * ประตูบังคับตั้งค่าบัญชี — กติกาเดียวกับ PasswordPolicy/ThaiMobilePhone ฝั่ง backend
 * ฝั่งแอปตรวจก่อนเพื่อไม่ยิงคำขอที่รู้อยู่แล้วว่าไม่ผ่าน แต่ backend ยังเป็นผู้ตัดสินจริง
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InitialAccountSetupViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val authRepository = mockk<AuthRepository>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { authRepository.completeInitialSetup(any(), any(), any()) } returns Result.success(Unit)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun viewModel(
        required: Boolean = true,
        phoneRequired: Boolean = false
    ): InitialAccountSetupViewModel {
        every { authRepository.initialSetupInfo() } returns
            InitialSetupInfo(required, phoneRequired, "2026-10-06")
        return InitialAccountSetupViewModel(authRepository)
    }

    private fun InitialAccountSetupViewModel.fill(
        current: String = "default-pass",
        new: String = "passphrase-ใหม่",
        confirm: String = "passphrase-ใหม่",
        phone: String = ""
    ) {
        onCurrentPasswordChange(current)
        onNewPasswordChange(new)
        onConfirmPasswordChange(confirm)
        if (phone.isNotEmpty()) onPhoneNumberChange(phone)
    }

    @Test
    fun `seeds phone requirement from the login response`() {
        assertTrue(viewModel(phoneRequired = true).uiState.value.phoneRequired)
        assertFalse(viewModel(phoneRequired = false).uiState.value.phoneRequired)
    }

    @Test
    fun `submits and reports completion when the account has a phone already`() = runTest {
        val vm = viewModel(phoneRequired = false)
        vm.fill()

        vm.submit()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            authRepository.completeInitialSetup("default-pass", "passphrase-ใหม่", "")
        }
        assertTrue(vm.uiState.value.isCompleted)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `sends the phone number when the account has none`() = runTest {
        val vm = viewModel(phoneRequired = true)
        vm.fill(phone = "081-234-5678")

        vm.submit()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            authRepository.completeInitialSetup("default-pass", "passphrase-ใหม่", "081-234-5678")
        }
    }

    @Test
    fun `rejects a password shorter than eight characters`() = runTest {
        val vm = viewModel()
        vm.fill(new = "สั้นไป", confirm = "สั้นไป")

        vm.submit()
        advanceUntilIdle()

        assertEquals("รหัสผ่านใหม่ต้องมีอย่างน้อย 8 ตัวอักษร", vm.uiState.value.error)
        coVerify(exactly = 0) { authRepository.completeInitialSetup(any(), any(), any()) }
    }

    @Test
    fun `counts Thai characters as single characters not bytes`() = runTest {
        val vm = viewModel()
        // 8 code points. นับเป็น byte จะได้ 24 ซึ่งจะผ่านด่านด้วยเหตุผลผิด
        vm.fill(new = "แปดตัวอักษร".take(8), confirm = "แปดตัวอักษร".take(8))

        vm.submit()
        advanceUntilIdle()

        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `rejects mismatched confirmation and reuse of the current password`() = runTest {
        val mismatch = viewModel()
        mismatch.fill(new = "passphrase-ก", confirm = "passphrase-ข")
        mismatch.submit()
        advanceUntilIdle()
        assertEquals("รหัสผ่านใหม่ไม่ตรงกัน", mismatch.uiState.value.error)

        val reused = viewModel()
        reused.fill(current = "same-password", new = "same-password", confirm = "same-password")
        reused.submit()
        advanceUntilIdle()
        assertEquals("รหัสผ่านใหม่ต้องไม่ซ้ำกับรหัสผ่านปัจจุบัน", reused.uiState.value.error)

        coVerify(exactly = 0) { authRepository.completeInitialSetup(any(), any(), any()) }
    }

    @Test
    fun `rejects a landline or malformed mobile number`() = runTest {
        val vm = viewModel(phoneRequired = true)
        vm.fill(phone = "021234567")

        vm.submit()
        advanceUntilIdle()

        assertEquals("กรุณากรอกเบอร์มือถือไทยให้ถูกต้อง", vm.uiState.value.error)
        coVerify(exactly = 0) { authRepository.completeInitialSetup(any(), any(), any()) }
    }

    @Test
    fun `refuses to submit when the server never asked for setup`() = runTest {
        val vm = viewModel(required = false)
        vm.fill()

        vm.submit()
        advanceUntilIdle()

        assertEquals("กรุณาเข้าสู่ระบบใหม่", vm.uiState.value.error)
        coVerify(exactly = 0) { authRepository.completeInitialSetup(any(), any(), any()) }
    }

    /** ล้มเหลวแล้วต้องไม่ค้างรหัสผ่านไว้ในหน่วยความจำของหน้าจอ และต้องไม่ติด isLoading */
    @Test
    fun `clears entered passwords and stops loading when the server rejects`() = runTest {
        coEvery { authRepository.completeInitialSetup(any(), any(), any()) } returns
            Result.failure(Exception("รหัสผ่านปัจจุบันไม่ถูกต้อง"))
        val vm = viewModel()
        vm.fill()

        vm.submit()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals("รหัสผ่านปัจจุบันไม่ถูกต้อง", state.error)
        assertEquals("", state.currentPassword)
        assertEquals("", state.newPassword)
        assertEquals("", state.confirmPassword)
        assertFalse(state.isLoading)
        assertFalse(state.isCompleted)
    }

    @Test
    fun `logout abandons the setup session`() {
        viewModel().logout()
        io.mockk.verify(exactly = 1) { authRepository.abandonInitialSetup() }
    }
}
