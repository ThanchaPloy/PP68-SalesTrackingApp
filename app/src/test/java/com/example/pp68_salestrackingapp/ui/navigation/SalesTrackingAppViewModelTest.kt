package com.example.pp68_salestrackingapp.ui.navigation

import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import io.mockk.coEvery
import io.mockk.mockk
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SalesTrackingAppViewModelTest {

    private val authRepo: AuthRepository = mockk(relaxed = true)
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a successful logout hands control to the caller to navigate away`() = runTest {
        coEvery { authRepo.logout() } returns Result.success(Unit)
        val vm = SalesTrackingAppViewModel(authRepo)
        var navigated = false
        var failure: String? = null

        vm.logout(onSuccess = { navigated = true }, onFailure = { failure = it })
        advanceUntilIdle()

        assertTrue(navigated)
        assertEquals(null, failure)
    }

    // นี่คือบั๊กที่แก้: logout() ปฏิเสธเมื่อยังมีข้อมูลค้างไม่ได้ซิงค์ เพื่อกันงานออฟไลน์หาย
    // แต่หน้าจอส่วนใหญ่ทิ้งค่านี้แล้วพาไปหน้า Login ทันที ทำให้กลไกป้องกันไม่เคยทำงาน
    @Test
    fun `a refused logout must not navigate and must surface the reason`() = runTest {
        val reason = "มีข้อมูลที่ยังไม่ได้ซิงค์กับเซิร์ฟเวอร์"
        coEvery { authRepo.logout() } returns Result.failure(Exception(reason))
        val vm = SalesTrackingAppViewModel(authRepo)
        var navigated = false
        var failure: String? = null

        vm.logout(onSuccess = { navigated = true }, onFailure = { failure = it })
        advanceUntilIdle()

        assertFalse(navigated)
        assertEquals(reason, failure)
    }

    @Test
    fun `a failure with no message still reports something the user can read`() = runTest {
        coEvery { authRepo.logout() } returns Result.failure(Exception())
        val vm = SalesTrackingAppViewModel(authRepo)
        var failure: String? = null

        vm.logout(onSuccess = {}, onFailure = { failure = it })
        advanceUntilIdle()

        assertEquals("ออกจากระบบไม่สำเร็จ", failure)
    }
}
