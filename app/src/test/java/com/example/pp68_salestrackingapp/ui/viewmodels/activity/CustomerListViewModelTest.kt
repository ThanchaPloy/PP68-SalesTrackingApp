package com.example.pp68_salestrackingapp.ui.viewmodels.customer

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.example.pp68_salestrackingapp.data.model.*
import com.example.pp68_salestrackingapp.data.repository.*
import com.example.pp68_salestrackingapp.ui.screen.customer.CustomerListViewModel
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class CustomerListViewModelTest {

    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()

    // ✅ เปลี่ยนเป็น UnconfinedTestDispatcher() ทำให้ StateFlow ปล่อยค่าออกทันทีไม่ต้องรอคิว
    private val testDispatcher = UnconfinedTestDispatcher()

    private val customerRepo = mockk<CustomerRepository>(relaxed = true)
    private val authRepo = mockk<AuthRepository>(relaxed = true)
    private lateinit var viewModel: CustomerListViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        every { authRepo.currentUser() } returns AuthUser("U1", "t@t.com", "sale", "T1")
        every { customerRepo.getAllCustomersFlow() } returns flowOf(emptyList())
        coEvery { customerRepo.refreshCustomers(any()) } returns Result.success(Unit)
    }

    @After
    fun tearDown() {
        clearAllMocks()
        Dispatchers.resetMain()
    }

    @Test
    fun `onSearchChange should update flow after debounce`() = runTest {
        val query = "Sansiri"
        val result = listOf(Customer("C1", "Sansiri", null, null, null, null, null, null, null))

        every { customerRepo.searchCustomersFlow(query) } returns flowOf(result)

        viewModel = CustomerListViewModel(customerRepo, authRepo)

        viewModel.customers.test {
            // 1. รับค่าเริ่มต้น (Initial State) ที่เป็น EmptyList หรือค่าเก่าออกไปก่อน
            awaitItem()

            // 2. จำลองการพิมพ์ข้อความค้นหา
            viewModel.onSearchChange(query)

            // ✅ 3. ใช้ advanceUntilIdle() เพื่อวาร์ปเวลาข้าม Debounce แบบอัตโนมัติไม่ต้องเดาตัวเลข
            advanceUntilIdle()

            // 4. ตรวจสอบข้อมูลที่ได้รับ
            assertEquals(result, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }
}