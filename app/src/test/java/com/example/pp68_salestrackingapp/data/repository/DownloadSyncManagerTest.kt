package com.example.pp68_salestrackingapp.data.repository

import com.example.pp68_salestrackingapp.utils.SyncFailureType
import io.mockk.coEvery
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadSyncManagerTest {
    private val customers: CustomerRepository = mockk()
    private val contacts: ContactRepository = mockk()
    private val projects: ProjectRepository = mockk()
    private val activities: ActivityRepository = mockk()
    private val masterData: MasterDataRepository = mockk()
    private lateinit var manager: SyncManager

    @Before fun setUp() {
        manager = SyncManager(customers, contacts, projects, activities, masterData)
        coEvery { customers.refreshCustomers(any()) } returns Result.success(Unit)
        coEvery { contacts.refreshContacts() } returns Result.success(Unit)
        coEvery { projects.refreshProjects(any()) } returns Result.success(Unit)
        coEvery { activities.refreshActivities(any()) } returns Result.success(Unit)
        coEvery { activities.refreshResults(any()) } returns Result.success(Unit)
        coEvery { masterData.getProjectStages() } returns emptyList()
        coEvery { masterData.getLossReasons() } returns emptyList()
        coEvery { masterData.getDealFactorQuestions() } returns emptyList()
    }

    @Test fun `all required downloads report success`() = runTest {
        val result = manager.syncAll("U1", "B1")

        assertTrue(result.failedParts.isEmpty())
        assertFalse(result.shouldRetry)
        assertTrue(manager.failedParts.value.isEmpty())
    }

    @Test fun `offline part is retained in aggregate and requests retry`() = runTest {
        coEvery { projects.refreshProjects(any()) } returns Result.failure(IOException("offline"))

        val result = manager.syncAll("U1", "B1")

        assertEquals(1, result.failedParts.size)
        assertEquals(SyncFailureType.NETWORK, result.failedParts.single().failureType)
        assertTrue(result.shouldRetry)
        assertEquals(result.failedParts.map { it.label }, manager.failedParts.value)
    }

    @Test fun `authentication failure is visible but does not enter retry loop`() = runTest {
        coEvery { customers.refreshCustomers(any()) } returns Result.failure(Exception("HTTP 401"))

        val result = manager.syncAll("U1", "B1")

        assertEquals(SyncFailureType.AUTHENTICATION, result.failedParts.single().failureType)
        assertFalse(result.shouldRetry)
    }

    @Test fun `unexpected repository failure is local fatal`() = runTest {
        coEvery { activities.refreshResults(any()) } returns Result.failure(IllegalStateException("bad local state"))

        val result = manager.syncAll("U1", "B1")

        assertTrue(result.hasLocalFatalFailure)
        assertEquals(SyncFailureType.LOCAL_FATAL, result.failedParts.single().failureType)
    }

    @Test fun `cancellation is never converted to a download failure`() = runTest {
        coEvery { projects.refreshProjects(any()) } throws CancellationException("cancelled")

        var cancelled = false
        try {
            manager.syncAll("U1", "B1")
        } catch (_: CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
    }
}
