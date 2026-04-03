package com.example.pp68_salestrackingapp.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pp68_salestrackingapp.data.model.Customer
import com.example.pp68_salestrackingapp.data.model.Project
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class RoomIntegrationTest {

    private lateinit var db: AppDatabase
    private lateinit var projectDao: ProjectDao
    private lateinit var customerDao: CustomerDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        projectDao = db.projectDao()
        customerDao = db.customerDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun insertAndRetrieveProjectWithProgressPct() = runTest {
        // Arrange
        val project = Project(
            projectId = "PJ-001",
            custId = "C001",
            projectName = "Big System",
            progressPct = 40
        )
        
        // Act
        projectDao.insertProject(project)
        val retrieved = projectDao.getProjectById("PJ-001")

        // Assert
        assertEquals(40, retrieved?.progressPct)
    }

    @Test
    fun customerDaoOnlyReturnsStatusCustomerRecords() = runTest {
        // Arrange
        val customer1 = Customer(
            custId = "C-1",
            companyName = "Comp A",
            branch = null,
            custType = null,
            companyAddr = null,
            companyLat = null,
            companyLong = null,
            companyStatus = "customer",
            firstCustomerDate = null
        )
        val customer2 = Customer(
            custId = "C-2",
            companyName = "Comp B",
            branch = null,
            custType = null,
            companyAddr = null,
            companyLat = null,
            companyLong = null,
            companyStatus = "lead",
            firstCustomerDate = null
        )
        
        // Act
        customerDao.insertCustomers(listOf(customer1, customer2))
        val allCustomers = customerDao.getAllCustomers().first()
        val filtered = allCustomers.filter { it.companyStatus == "customer" }

        // Assert
        assertEquals(1, filtered.size)
        assertEquals("C-1", filtered.first().custId)
    }

    @Test
    fun clearAndInsertReplacesAllProjects() = runTest {
        // Arrange
        val project1 = Project(projectId = "PJ-001", custId = "C001", projectName = "P1")
        projectDao.insertProject(project1)
        
        val project2 = Project(projectId = "PJ-0099", custId = "C001", projectName = "Replacement")
        
        // Act
        projectDao.clearAndInsert(listOf(project2))
        
        val allProjects = projectDao.getAllProjects().first()

        // Assert
        assertEquals(1, allProjects.size)
        assertEquals("PJ-0099", allProjects.first().projectId)
    }

    @Test
    fun searchProjectByNameReturnsMatchingResults() = runTest {
        // Arrange
        val p1 = Project(projectId = "PJ-001", custId = "C001", projectName = "ทดสอบ คอนโดมิเนียม")
        val p2 = Project(projectId = "PJ-002", custId = "C001", projectName = "บ้านเดี่ยว")
        projectDao.insertProjects(listOf(p1, p2))

        // Act
        val results = projectDao.searchProjects("คอนโด").first()

        // Assert
        assertEquals(1, results.size)
        assertTrue(results.first().projectName?.contains("คอนโด") == true)
    }
}
