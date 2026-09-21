package com.example.pp68_salestrackingapp.ui.viewmodels.customer

import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import com.example.pp68_salestrackingapp.data.repository.CustomerRepository
import com.example.pp68_salestrackingapp.data.repository.ProjectRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
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

@OptIn(ExperimentalCoroutinesApi::class)
class CustomerDeleteErrorTest {

    private val customerRepo: CustomerRepository = mockk(relaxed = true)
    private val authRepo: AuthRepository = mockk(relaxed = true)
    private val projectRepo: ProjectRepository = mockk(relaxed = true)
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { customerRepo.getCustomerById(any()) } returns Result.success(mockk(relaxed = true))
        coEvery { customerRepo.getContactPersons(any(), any()) } returns Result.success(emptyList())
        every { projectRepo.getAllProjectsFlow() } returns flowOf(emptyList())
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm() = CustomerDetailViewModel(customerRepo, projectRepo, authRepo)
        .also { it.load("C1") }

    // เคสที่ผู้ใช้เจอจริง: กดลบบริษัทแล้วไม่มีอะไรเกิดขึ้น ไม่มีข้อความบอก
    // เพราะ onFailure เป็น lambda ว่างที่มีแต่คอมเมนต์ "Handle error"
    @Test
    fun `a refused delete reports why instead of doing nothing`() = runTest {
        coEvery { customerRepo.deleteCustomer("C1") } returns
            Result.failure(Exception("ลบโครงการไม่สำเร็จ (HTTP 400) จึงยังไม่ลบลูกค้า"))

        val vm = vm()
        advanceUntilIdle()
        vm.deleteCustomer()
        advanceUntilIdle()

        assertEquals(
            "ลบโครงการไม่สำเร็จ (HTTP 400) จึงยังไม่ลบลูกค้า",
            vm.deleteError.value
        )
        assertFalse("ห้ามปิดหน้าจอเมื่อลบไม่สำเร็จ", vm.deleteSuccess.value)
    }

    @Test
    fun `a successful delete reports no error`() = runTest {
        coEvery { customerRepo.deleteCustomer("C1") } returns Result.success(Unit)

        val vm = vm()
        advanceUntilIdle()
        vm.deleteCustomer()
        advanceUntilIdle()

        assertTrue(vm.deleteSuccess.value)
        assertNull(vm.deleteError.value)
    }

    @Test
    fun `dismissing the message clears it so it does not show twice`() = runTest {
        coEvery { customerRepo.deleteCustomer("C1") } returns Result.failure(Exception("พัง"))

        val vm = vm()
        advanceUntilIdle()
        vm.deleteCustomer()
        advanceUntilIdle()
        assertEquals("พัง", vm.deleteError.value)

        vm.clearDeleteError()
        assertNull(vm.deleteError.value)
    }
}
