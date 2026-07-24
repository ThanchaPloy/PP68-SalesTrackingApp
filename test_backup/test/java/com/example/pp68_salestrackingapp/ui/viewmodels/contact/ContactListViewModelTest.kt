package com.example.pp68_salestrackingapp.ui.viewmodels.contact

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.pp68_salestrackingapp.data.model.AuthUser
import com.example.pp68_salestrackingapp.data.model.ContactPerson
import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import com.example.pp68_salestrackingapp.data.repository.ContactRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ContactListViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private val contactRepository = mockk<ContactRepository>(relaxed = true)
    private val authRepository = mockk<AuthRepository>(relaxed = true)
    private lateinit var viewModel: ContactListViewModel

    private val mockUser = AuthUser(
        userId = "U01",
        email = "user@example.com",
        role = "sale",
        teamId = null,
        fullName = "Test User",
        branchName = null
    )

    private val sampleContacts = listOf(
        ContactPerson("C01", "CUST-01", "Alice Smith"),
        ContactPerson("C02", "CUST-01", "Bob Jones"),
        ContactPerson("C03", "CUST-02", "Charlie Brown")
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() {
        viewModel = ContactListViewModel(contactRepository, authRepository)
    }

    // TC-UNIT-VM-CLIST-01
    @Test
    fun `init should set authUser from authRepository`() = runTest {
        every { authRepository.currentUser() } returns mockUser
        every { contactRepository.getAllContactsFlow() } returns flowOf(sampleContacts)
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(mockUser, viewModel.uiState.value.authUser)
    }

    // TC-UNIT-VM-CLIST-02
    @Test
    fun `init with null user should set authUser to null`() = runTest {
        every { authRepository.currentUser() } returns null
        every { contactRepository.getAllContactsFlow() } returns flowOf(emptyList())
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.authUser)
    }

    // TC-UNIT-VM-CLIST-03
    @Test
    fun `init should load contacts from flow`() = runTest {
        every { authRepository.currentUser() } returns mockUser
        every { contactRepository.getAllContactsFlow() } returns flowOf(sampleContacts)
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(sampleContacts, viewModel.uiState.value.contacts)
    }

    // TC-UNIT-VM-CLIST-04
    @Test
    fun `init with empty contacts should have empty list in state`() = runTest {
        every { authRepository.currentUser() } returns mockUser
        every { contactRepository.getAllContactsFlow() } returns flowOf(emptyList())
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.contacts.isEmpty())
    }

    // TC-UNIT-VM-CLIST-05
    @Test
    fun `onSearchChange should update searchQuery in state`() = runTest {
        every { authRepository.currentUser() } returns mockUser
        every { contactRepository.getAllContactsFlow() } returns flowOf(sampleContacts)
        every { contactRepository.searchContactsFlow(any()) } returns flowOf(emptyList())
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onSearchChange("Alice")
        assertEquals("Alice", viewModel.uiState.value.searchQuery)
    }

    // TC-UNIT-VM-CLIST-06
    @Test
    fun `onSearchChange with blank query should use getAllContactsFlow`() = runTest {
        every { authRepository.currentUser() } returns mockUser
        every { contactRepository.getAllContactsFlow() } returns flowOf(sampleContacts)
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onSearchChange("")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(sampleContacts, viewModel.uiState.value.contacts)
    }

    // TC-UNIT-VM-CLIST-07
    @Test
    fun `onSearchChange with query should use searchContactsFlow`() = runTest {
        val searchResult = listOf(ContactPerson("C01", "CUST-01", "Alice Smith"))
        every { authRepository.currentUser() } returns mockUser
        every { contactRepository.getAllContactsFlow() } returns flowOf(sampleContacts)
        every { contactRepository.searchContactsFlow(any()) } returns flowOf(searchResult)
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onSearchChange("Alice")
        testDispatcher.scheduler.advanceTimeBy(400) // Wait past debounce threshold (300ms)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(searchResult, viewModel.uiState.value.contacts)
    }

    // TC-UNIT-VM-CLIST-08
    @Test
    fun `refreshData should complete without errors when user is logged in`() = runTest {
        every { authRepository.currentUser() } returns mockUser
        every { contactRepository.getAllContactsFlow() } returns flowOf(emptyList())
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.refreshData()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.error)
    }

    // TC-UNIT-VM-CLIST-09
    @Test
    fun `refreshData with no logged in user should not crash`() = runTest {
        every { authRepository.currentUser() } returns null
        every { contactRepository.getAllContactsFlow() } returns flowOf(emptyList())
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.refreshData()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
    }

    // TC-UNIT-VM-CLIST-10
    @Test
    fun `logout should call authRepository logout`() = runTest {
        every { authRepository.currentUser() } returns mockUser
        every { contactRepository.getAllContactsFlow() } returns flowOf(emptyList())
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.logout()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { authRepository.logout() }
    }
}
