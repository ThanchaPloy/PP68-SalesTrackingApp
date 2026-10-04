package com.example.pp68_salestrackingapp.ui.viewmodels.customer

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import app.cash.turbine.test
import com.example.pp68_salestrackingapp.data.model.AuthUser
import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import com.example.pp68_salestrackingapp.data.repository.CustomerRepository
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CustomerListViewModelTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val dispatcher = StandardTestDispatcher()
    private val customerRepo = mockk<CustomerRepository>()
    private val authRepo = mockk<AuthRepository>()
    private lateinit var viewModel: CustomerListViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        every { authRepo.currentUser() } returns AuthUser("U1", "t@t.com", "sale", "T1")
        every { customerRepo.getCustomersPagingFlow(any(), any(), any(), any(), any()) } returns
            flowOf(PagingData.empty())
        coEvery { customerRepo.refreshCustomers(any()) } returns Result.success(Unit)
    }

    @After
    fun tearDown() {
        if (::viewModel.isInitialized) viewModel.viewModelScope.cancel()
        clearAllMocks()
        Dispatchers.resetMain()
    }

    private fun initVm() {
        viewModel = CustomerListViewModel(customerRepo, authRepo)
    }

    private suspend fun collectPagingGeneration() {
        viewModel.customers.test {
            delay(305)
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.viewModelScope.coroutineContext[Job]?.cancelAndJoin()
    }

    @Test
    fun defaultFiltersRequestUnfilteredPage() = runTest {
        initVm()
        collectPagingGeneration()

        verify(exactly = 1) {
            customerRepo.getCustomersPagingFlow("", null, null, 0, null)
        }
    }

    @Test
    fun searchAndFiltersAreDelegatedToPagingRepository() = runTest {
        initVm()
        viewModel.onSearchChange("Alpha")
        viewModel.onBizGroupFilter("R")
        viewModel.onCustTypeFilter("Dealer")
        viewModel.onTabSelected(1)
        collectPagingGeneration()

        verify(exactly = 1) {
            customerRepo.getCustomersPagingFlow("Alpha", "R", "Dealer", 1, null)
        }
    }

    @Test
    fun selectingInitialDelegatesFilterAndSearchClearsIt() = runTest {
        initVm()
        viewModel.onInitialSelected("ก")
        assertEquals("ก", viewModel.selectedInitial.value)

        viewModel.onSearchChange("บริษัท")
        assertNull(viewModel.selectedInitial.value)
        collectPagingGeneration()

        verify(exactly = 1) {
            customerRepo.getCustomersPagingFlow("บริษัท", null, null, 0, null)
        }
    }

    @Test
    fun sameBizGroupTappedTwiceClearsFilter() = runTest {
        initVm()
        viewModel.onBizGroupFilter("R")
        assertEquals("R", viewModel.selectedBizGroup.value)
        viewModel.onBizGroupFilter("R")
        assertNull(viewModel.selectedBizGroup.value)
    }

    @Test
    fun initRefreshesBranchAndClearsLoading() = runTest {
        initVm()
        advanceUntilIdle()

        assertFalse(viewModel.isLoading.value)
        assertNull(viewModel.error.value)
        coVerify(exactly = 1) { customerRepo.refreshCustomers("T1") }
    }

    @Test
    fun refreshFailureSetsError() = runTest {
        coEvery { customerRepo.refreshCustomers("T1") } returns Result.failure(Exception("network down"))
        initVm()
        advanceUntilIdle()

        assertEquals("network down", viewModel.error.value)
        assertFalse(viewModel.isLoading.value)
    }

    @Test
    fun missingUserSkipsRefresh() = runTest {
        every { authRepo.currentUser() } returns null
        initVm()
        advanceUntilIdle()

        coVerify(exactly = 0) { customerRepo.refreshCustomers(any()) }
        assertFalse(viewModel.isLoading.value)
        assertEquals("ไม่พบรหัสสาขาของผู้ใช้", viewModel.error.value)
    }
}
