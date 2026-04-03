package com.example.pp68_salestrackingapp.data.repository

import com.example.pp68_salestrackingapp.data.local.ActivityDao
import com.example.pp68_salestrackingapp.data.local.CustomerDao
import com.example.pp68_salestrackingapp.data.local.ProjectDao
import com.example.pp68_salestrackingapp.data.model.Customer
import com.example.pp68_salestrackingapp.data.model.Project
import com.example.pp68_salestrackingapp.data.model.SalesActivity
import app.cash.turbine.test
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardRepositoryTest {

    private lateinit var repository: DashboardRepository
    private val customerDao = mockk<CustomerDao>(relaxed = true)
    private val projectDao  = mockk<ProjectDao>(relaxed = true)
    private val activityDao = mockk<ActivityDao>(relaxed = true)

    private val sampleCustomers = listOf(
        Customer("CST-001", "แสนสิริ", null, "Developer", null, null, null, "customer", null),
        Customer("CST-002", "เมเจอร์", null, "Developer", null, null, null, "customer", null)
    )

    private val sampleProjects = listOf(
        Project(projectId = "PJ-001", custId = "CST-001", projectName = "P1", projectStatus = "Active"),
        Project(projectId = "PJ-002", custId = "CST-001", projectName = "P2", projectStatus = "active"),
        Project(projectId = "PJ-003", custId = "CST-001", projectName = "P3", projectStatus = "Completed"),
        Project(projectId = "PJ-004", custId = "CST-001", projectName = "P4", projectStatus = "Lead")
    )

    private val sampleActivities = listOf(
        SalesActivity(activityId = "APT-001", userId = "USR-001", customerId = "CST-001",
            activityType = "onsite", activityDate = "2026-03-28", status = "Completed"),
        SalesActivity(activityId = "APT-002", userId = "USR-001", customerId = "CST-001",
            activityType = "onsite", activityDate = "2026-03-28", status = "completed"),
        SalesActivity(activityId = "APT-003", userId = "USR-001", customerId = "CST-001",
            activityType = "call", activityDate = "2026-03-28", status = "planned")
    )

    @Before
    fun setup() {
        repository = DashboardRepository(customerDao, projectDao, activityDao)
    }

    // TC-UNIT-DASH-01
    @Test
    fun `getDashboardSummary should return correct totalCustomers`() = runTest {
        every { customerDao.getAllCustomers() } returns flowOf(sampleCustomers)
        every { projectDao.getAllProjects()   } returns flowOf(sampleProjects)
        every { activityDao.getAllActivities() } returns flowOf(sampleActivities)

        repository.getDashboardSummary().test {
            val summary = awaitItem()
            assertEquals(2, summary.totalCustomers)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // TC-UNIT-DASH-02
    @Test
    fun `getDashboardSummary should count active projects case insensitive`() = runTest {
        every { customerDao.getAllCustomers() } returns flowOf(sampleCustomers)
        every { projectDao.getAllProjects()   } returns flowOf(sampleProjects)
        every { activityDao.getAllActivities() } returns flowOf(sampleActivities)

        repository.getDashboardSummary().test {
            val summary = awaitItem()
            // PJ-001 "Active" และ PJ-002 "active" ควรนับรวมกัน = 2
            assertEquals(2, summary.activeProjects)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // TC-UNIT-DASH-03
    @Test
    fun `getDashboardSummary should count completed activities case insensitive`() = runTest {
        every { customerDao.getAllCustomers() } returns flowOf(sampleCustomers)
        every { projectDao.getAllProjects()   } returns flowOf(sampleProjects)
        every { activityDao.getAllActivities() } returns flowOf(sampleActivities)

        repository.getDashboardSummary().test {
            val summary = awaitItem()
            // APT-001 "Completed" และ APT-002 "completed" ควรนับรวมกัน = 2
            assertEquals(2, summary.completedActivities)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // TC-UNIT-DASH-04
    @Test
    fun `getDashboardSummary with empty data should return zeros`() = runTest {
        every { customerDao.getAllCustomers() } returns flowOf(emptyList())
        every { projectDao.getAllProjects()   } returns flowOf(emptyList())
        every { activityDao.getAllActivities() } returns flowOf(emptyList())

        repository.getDashboardSummary().test {
            val summary = awaitItem()
            assertEquals(0, summary.totalCustomers)
            assertEquals(0, summary.activeProjects)
            assertEquals(0, summary.completedActivities)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // TC-UNIT-DASH-05
    @Test
    fun `getDashboardSummary non-active projects should not be counted`() = runTest {
        val onlyClosedProjects = listOf(
            Project(projectId = "PJ-001", custId = "CST-001", projectName = "P1", projectStatus = "Completed"),
            Project(projectId = "PJ-002", custId = "CST-001", projectName = "P2", projectStatus = "Lost"),
        )
        every { customerDao.getAllCustomers() } returns flowOf(sampleCustomers)
        every { projectDao.getAllProjects()   } returns flowOf(onlyClosedProjects)
        every { activityDao.getAllActivities() } returns flowOf(emptyList())

        repository.getDashboardSummary().test {
            val summary = awaitItem()
            assertEquals(0, summary.activeProjects)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // TC-UNIT-DASH-06
    @Test
    fun `getDashboardSummary with single customer should return count 1`() = runTest {
        val oneCustomer = listOf(
            Customer("CST-001", "แสนสิริ", null, "Developer", null, null, null, "customer", null)
        )
        every { customerDao.getAllCustomers() } returns flowOf(oneCustomer)
        every { projectDao.getAllProjects()   } returns flowOf(emptyList())
        every { activityDao.getAllActivities() } returns flowOf(emptyList())

        repository.getDashboardSummary().test {
            val summary = awaitItem()
            assertEquals(1, summary.totalCustomers)
            cancelAndIgnoreRemainingEvents()
        }
    }
}