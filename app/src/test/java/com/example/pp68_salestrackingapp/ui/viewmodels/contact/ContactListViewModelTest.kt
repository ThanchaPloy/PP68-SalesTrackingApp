package com.example.pp68_salestrackingapp.ui.viewmodels.contact

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import app.cash.turbine.test
import com.example.pp68_salestrackingapp.data.model.AuthUser
import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import com.example.pp68_salestrackingapp.data.repository.ContactRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
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
class ContactListViewModelTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val dispatcher = StandardTestDispatcher()
    private val repo = mockk<ContactRepository>()
    private val authRepo = mockk<AuthRepository>()
    private lateinit var viewModel: ContactListViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        every { authRepo.currentUser() } returns AuthUser("U1", "u@test.com", "sale", "T1")
        every { repo.getContactsPagingFlow(any(), any()) } returns flowOf(PagingData.empty())
        coEvery { repo.refreshContacts() } returns Result.success(Unit)
    }

    @After
    fun tearDown() {
        if (::viewModel.isInitialized) viewModel.viewModelScope.cancel()
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun initVm() {
        viewModel = ContactListViewModel(repo, authRepo)
    }

    private suspend fun collectPagingGeneration() {
        viewModel.contacts.test {
            delay(305)
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.viewModelScope.coroutineContext[Job]?.cancelAndJoin()
    }

    @Test
    fun defaultPageAndRefreshAreRequested() = runTest {
        initVm()
        collectPagingGeneration()

        verify(exactly = 1) { repo.getContactsPagingFlow("", null) }
        coVerify(exactly = 1) { repo.refreshContacts() }
    }

    @Test
    fun initialFilterIsDelegatedAndSearchClearsIt() = runTest {
        initVm()
        viewModel.onInitialSelected("A")
        assertEquals("A", viewModel.selectedInitial.value)
        viewModel.onSearchChange("Beta")
        assertNull(viewModel.selectedInitial.value)
        collectPagingGeneration()

        verify(exactly = 1) { repo.getContactsPagingFlow("Beta", null) }
    }

    @Test
    fun refreshFailureStopsLoadingAndSetsError() = runTest {
        coEvery { repo.refreshContacts() } returns Result.failure(Exception("offline"))
        initVm()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals("offline", viewModel.uiState.value.error)
    }
}
