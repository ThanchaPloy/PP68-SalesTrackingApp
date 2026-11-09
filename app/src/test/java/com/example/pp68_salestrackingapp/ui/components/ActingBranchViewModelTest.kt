package com.example.pp68_salestrackingapp.ui.components

import com.example.pp68_salestrackingapp.data.model.Branch
import com.example.pp68_salestrackingapp.data.repository.BranchRepository
import com.example.pp68_salestrackingapp.di.TokenManager
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ActingBranchViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val tokenManager = mockk<TokenManager>(relaxed = true)
    private val branchRepo = mockk<BranchRepository>(relaxed = true)

    private val north = listOf(
        Branch("11PL", "สาขาพิษณุโลก", "NORTH"),
        Branch("12CM", "สาขาเชียงใหม่", "NORTH")
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        unmockkAll()
        Dispatchers.resetMain()
    }

    @Test
    fun `zone manager without an acting branch is asked to pick one`() = runTest {
        every { tokenManager.getManagedRegion() } returns "NORTH"
        every { tokenManager.getActingBranch() } returns null
        coEvery { branchRepo.observeBranches() } returns north + Branch("13SR", "สาขาสุราษฎร์ธานี", "SOUTH")

        val vm = ActingBranchViewModel(tokenManager, branchRepo)
        advanceUntilIdle()

        assertTrue(vm.mustChoose.value)
        assertEquals(listOf("11PL", "12CM"), vm.options.value.map { it.branchId })
    }

    @Test
    fun `a role that is not a zone manager is never asked`() = runTest {
        every { tokenManager.getManagedRegion() } returns null

        val vm = ActingBranchViewModel(tokenManager, branchRepo)
        advanceUntilIdle()

        assertFalse(vm.mustChoose.value)
        coVerify(exactly = 0) { branchRepo.observeBranches() }
    }

    @Test
    fun `a zone manager who already picked today is not asked again`() = runTest {
        every { tokenManager.getManagedRegion() } returns "NORTH"
        every { tokenManager.getActingBranch() } returns "12CM"

        val vm = ActingBranchViewModel(tokenManager, branchRepo)
        advanceUntilIdle()

        assertFalse(vm.mustChoose.value)
    }

    /**
     * dialog นี้ปิดเองไม่ได้โดยตั้งใจ ถ้าโผล่มาตอนไม่มีสาขาให้เลือกเลย (ออฟไลน์ตั้งแต่ login แรก
     * ยังไม่เคย sync ตารางสาขา) จะกลายเป็นจอค้างที่ออกไม่ได้ — ต้องไม่โผล่
     */
    @Test
    fun `no branch list means no dialog at all, never a dead end`() = runTest {
        every { tokenManager.getManagedRegion() } returns "NORTH"
        every { tokenManager.getActingBranch() } returns null
        coEvery { branchRepo.observeBranches() } returns emptyList()
        coEvery { branchRepo.syncFromRemote() } returns Result.failure(Exception("offline"))

        val vm = ActingBranchViewModel(tokenManager, branchRepo)
        advanceUntilIdle()

        assertFalse(vm.mustChoose.value)
    }

    @Test
    fun `an empty local list is refilled from the server before giving up`() = runTest {
        every { tokenManager.getManagedRegion() } returns "NORTH"
        every { tokenManager.getActingBranch() } returns null
        coEvery { branchRepo.observeBranches() } returnsMany listOf(emptyList(), north)
        coEvery { branchRepo.syncFromRemote() } returns Result.success(Unit)

        val vm = ActingBranchViewModel(tokenManager, branchRepo)
        advanceUntilIdle()

        assertTrue(vm.mustChoose.value)
        coVerify(exactly = 1) { branchRepo.syncFromRemote() }
    }

    @Test
    fun `picking a branch stores it and closes the dialog`() = runTest {
        every { tokenManager.getManagedRegion() } returns "NORTH"
        every { tokenManager.getActingBranch() } returns null
        coEvery { branchRepo.observeBranches() } returns north

        val vm = ActingBranchViewModel(tokenManager, branchRepo)
        advanceUntilIdle()

        vm.select("12CM")

        verify { tokenManager.saveActingBranch("12CM") }
        assertFalse(vm.mustChoose.value)
    }
}
