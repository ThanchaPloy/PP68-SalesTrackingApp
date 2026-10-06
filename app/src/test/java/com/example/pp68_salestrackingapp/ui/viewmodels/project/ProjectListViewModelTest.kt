package com.example.pp68_salestrackingapp.ui.viewmodels.project

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import app.cash.turbine.test
import com.example.pp68_salestrackingapp.data.model.AuthUser
import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import com.example.pp68_salestrackingapp.data.repository.ProjectRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
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
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectListViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val dispatcher = StandardTestDispatcher()
    private val projectRepo = mockk<ProjectRepository>()
    private val authRepo = mockk<AuthRepository>()

    private lateinit var viewModel: ProjectListViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { authRepo.currentUser() } returns AuthUser("U1", "u@test.com", "sale", "T1")
        every { projectRepo.getProjectsPagingFlow(any(), any(), any(), any(), any()) } returns
            flowOf(PagingData.empty())
        coEvery { projectRepo.refreshProjects(any()) } returns Result.success(Unit)
    }

    @After
    fun tearDown() {
        if (::viewModel.isInitialized) viewModel.viewModelScope.cancel()
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun initVm() {
        viewModel = ProjectListViewModel(projectRepo, authRepo)
    }

    private suspend fun collectPagingGeneration() {
        viewModel.projects.test {
            delay(305)
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.viewModelScope.coroutineContext[Job]?.cancelAndJoin()
    }

    @Test
    fun givenInit_whenCreated_thenExposesAuthUserAndTriggersRefreshLoadingCycle() = runTest {
        initVm()
        advanceUntilIdle()

        assertEquals("U1", viewModel.authUser.value?.userId)
        assertFalse(viewModel.isLoading.value)
        coVerify(exactly = 1) { projectRepo.refreshProjects("U1") }
    }

    @Test
    fun givenNoCurrentUser_whenRefreshFromInit_thenSetsReloginErrorAndSkipsRefreshCall() = runTest {
        every { authRepo.currentUser() } returns null

        initVm()
        advanceUntilIdle()

        assertEquals("กรุณาเข้าสู่ระบบใหม่", viewModel.error.value)
        coVerify(exactly = 0) { projectRepo.refreshProjects(any()) }
    }

    @Test
    fun givenRefreshFailure_whenRefreshDataFromApi_thenStopsLoadingAndSetsError() = runTest {
        coEvery { projectRepo.refreshProjects("U1") } returns Result.failure(Exception("refresh boom"))
        initVm()
        advanceUntilIdle()

        assertEquals("refresh boom", viewModel.error.value)
        assertFalse(viewModel.isLoading.value)
    }

    @Test
    fun givenActiveTabDefault_whenProjectsCollected_thenRequestsActivePage() = runTest {
        initVm()
        collectPagingGeneration()

        verify(exactly = 1) {
            projectRepo.getProjectsPagingFlow("U1", "", 0, emptySet(), emptySet())
        }
    }

    // แท็บ "ปิดแล้ว" = โครงการที่ขายได้ (PO) ทุกใบ ไม่ขึ้นกับวันปิด — ปิดการขายได้แล้วคือจบดีลแล้ว
    // เดิมเงื่อนไขคือ PO && closingDate <= today ทำให้ใบที่ยังไม่ถึงวันส่งมอบ (P4) ค้างในแท็บ Active
    // ส่วน Lost/Failed แยกไปแท็บ inactive ต่างหาก เพราะแพ้ ≠ ขายได้
    @Test
    fun givenClosedTab_whenSelected_thenRequestsWonPage() = runTest {
        initVm()
        viewModel.onSelectTab(1)
        collectPagingGeneration()

        verify(exactly = 1) {
            projectRepo.getProjectsPagingFlow("U1", "", 1, emptySet(), emptySet())
        }
    }

    @Test
    fun givenInactiveTab_whenSelected_thenRequestsLostPage() = runTest {
        initVm()
        viewModel.onSelectTab(2)
        collectPagingGeneration()

        verify(exactly = 1) {
            projectRepo.getProjectsPagingFlow("U1", "", 2, emptySet(), emptySet())
        }
    }

    @Test
    fun givenSearchQuery_whenChanged_thenPassesQueryToPagingRepository() = runTest {
        initVm()
        viewModel.onSearchChange("Hot")
        collectPagingGeneration()

        verify(exactly = 1) {
            projectRepo.getProjectsPagingFlow("U1", "Hot", 0, emptySet(), emptySet())
        }
    }

    @Test
    fun givenStatusFilter_whenToggledTwice_thenAddsAndRemovesSelection() = runTest {
        initVm()
        assertTrue(viewModel.selectedStatuses.value.isEmpty())

        viewModel.toggleStatusFilter("Quotation")
        assertEquals(setOf("Quotation"), viewModel.selectedStatuses.value)

        viewModel.toggleStatusFilter("Quotation")
        assertTrue(viewModel.selectedStatuses.value.isEmpty())
    }

    @Test
    fun givenScoreFilterLowercase_whenToggled_thenStoresUppercaseAndPassesFiltersToRepository() = runTest {
        initVm()
        viewModel.toggleStatusFilter("Quotation")
        viewModel.toggleScoreFilter("hot")

        assertEquals(setOf("HOT"), viewModel.selectedScores.value)

        collectPagingGeneration()
        verify(exactly = 1) {
            projectRepo.getProjectsPagingFlow("U1", "", 0, setOf("Quotation"), setOf("HOT"))
        }

        viewModel.toggleScoreFilter("HOT")
        assertTrue(viewModel.selectedScores.value.isEmpty())
    }

    @Test
    fun givenFiltersSet_whenResetFilters_thenClearsStatusAndScoreSets() = runTest {
        initVm()
        viewModel.toggleStatusFilter("Quotation")
        viewModel.toggleScoreFilter("HOT")
        assertTrue(viewModel.selectedStatuses.value.isNotEmpty())
        assertTrue(viewModel.selectedScores.value.isNotEmpty())

        viewModel.resetFilters()

        assertTrue(viewModel.selectedStatuses.value.isEmpty())
        assertTrue(viewModel.selectedScores.value.isEmpty())
    }

    @Test
    fun givenFlowThrows_whenProjectsCollected_thenSetsError() = runTest {
        every { projectRepo.getProjectsPagingFlow(any(), any(), any(), any(), any()) } returns
            flow { throw IllegalStateException("db fail") }
        initVm()
        collectPagingGeneration()

        assertEquals("db fail", viewModel.error.value)
        assertFalse(viewModel.isLoading.value)
    }

    @Test
    fun givenErrorSet_whenClearError_thenErrorBecomesNull() = runTest {
        coEvery { projectRepo.refreshProjects("U1") } returns Result.failure(Exception("x"))
        initVm()
        advanceUntilIdle()
        assertEquals("x", viewModel.error.value)

        viewModel.clearError()

        assertNull(viewModel.error.value)
    }

}
