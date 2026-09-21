package com.example.pp68_salestrackingapp.ui.viewmodels.customer

import com.example.pp68_salestrackingapp.data.model.AuthUser
import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import com.example.pp68_salestrackingapp.data.repository.CustomerRepository
import io.mockk.coEvery
import io.mockk.every
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddCustomerSaveNoticeTest {

    private val customerRepo: CustomerRepository = mockk(relaxed = true)
    private val authRepo: AuthRepository = mockk(relaxed = true)
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { authRepo.currentUser() } returns AuthUser("U1", "u@test.com", "sale")
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vmReadyToSave(): AddCustomerViewModel =
        AddCustomerViewModel(customerRepo, authRepo).apply {
            onEvent(AddCustomerEvent.CompanyNameChanged("บริษัท ทดสอบ จำกัด"))
            onEvent(AddCustomerEvent.CustTypeChanged("Owner"))
        }

    @Test
    fun `a customer the server accepted saves with no notice`() = runTest {
        coEvery { customerRepo.addCustomer(any()) } returns Result.success("C00123")

        val vm = vmReadyToSave()
        vm.onEvent(AddCustomerEvent.Save)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isSaved)
        assertNull(vm.uiState.value.saveNotice)
        assertNull(vm.uiState.value.saveError)
    }

    // เดิมกรณีนี้ขึ้น error "HTTP 500: ..." แล้วผู้ใช้ไปต่อไม่ได้ ทั้งที่ข้อมูลอยู่ในเครื่องแล้ว
    // และ outbox จะลองส่งใหม่ให้ — ต้องไปต่อได้ พร้อมบอกตามจริงว่ายังไม่ถึงเซิร์ฟเวอร์
    @Test
    fun `a customer still holding a temp id saves and says it is waiting to upload`() = runTest {
        coEvery { customerRepo.addCustomer(any()) } returns Result.success("TEMP-A1B2C3")

        val vm = vmReadyToSave()
        vm.onEvent(AddCustomerEvent.Save)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isSaved)
        assertNull("ต้องไม่ถือเป็น error", vm.uiState.value.saveError)
        assertEquals(
            "บันทึกลงเครื่องแล้ว กำลังรอส่งขึ้นเซิร์ฟเวอร์",
            vm.uiState.value.saveNotice
        )
    }

    @Test
    fun `a genuine failure is still reported as an error`() = runTest {
        coEvery { customerRepo.addCustomer(any()) } returns Result.failure(Exception("พัง"))

        val vm = vmReadyToSave()
        vm.onEvent(AddCustomerEvent.Save)
        advanceUntilIdle()

        assertEquals("พัง", vm.uiState.value.saveError)
        assertNull(vm.uiState.value.saveNotice)
    }
}
