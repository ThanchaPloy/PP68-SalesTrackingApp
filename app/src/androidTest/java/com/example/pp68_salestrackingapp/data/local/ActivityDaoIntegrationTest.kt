package com.example.pp68_salestrackingapp.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pp68_salestrackingapp.data.model.SalesActivity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ActivityDaoIntegrationTest {

    private lateinit var db: AppDatabase
    private lateinit var activityDao: ActivityDao

    private val sampleActivity = SalesActivity(
        activityId   = "APT-001",
        userId       = "USR-001",
        customerId   = "CST-001",
        projectId    = "PJ-001",
        activityType = "onsite",
        activityDate = "2026-03-28",
        status       = "planned"
    )

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        activityDao = db.activityDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun insertActivityAndRetrieveById() = runTest {
        activityDao.insertActivity(sampleActivity)

        val retrieved = activityDao.getActivityById("APT-001")

        assertNotNull(retrieved)
        assertEquals("APT-001", retrieved?.activityId)
        assertEquals("USR-001", retrieved?.userId)
        assertEquals("onsite",  retrieved?.activityType)
    }

    @Test
    fun insertActivitiesAndGetAll() = runTest {
        val activities = listOf(
            sampleActivity,
            SalesActivity(
                activityId   = "APT-002",
                userId       = "USR-001",
                customerId   = "CST-002",
                activityType = "call",
                activityDate = "2026-04-01",
                status       = "completed"
            )
        )

        activityDao.insertActivities(activities)

        val all = activityDao.getAllActivities().first()
        assertEquals(2, all.size)
    }

    @Test
    fun getActivitiesByProjectFiltersCorrectly() = runTest {
        val a1 = sampleActivity.copy(activityId = "APT-001", projectId = "PJ-001")
        val a2 = sampleActivity.copy(activityId = "APT-002", projectId = "PJ-002")
        activityDao.insertActivities(listOf(a1, a2))

        val result = activityDao.getActivitiesByProject("PJ-001").first()

        assertEquals(1, result.size)
        assertEquals("APT-001", result.first().activityId)
    }

    @Test
    fun getActivitiesByCustomerFiltersCorrectly() = runTest {
        val a1 = sampleActivity.copy(activityId = "APT-001", customerId = "CST-001")
        val a2 = sampleActivity.copy(activityId = "APT-002", customerId = "CST-002")
        activityDao.insertActivities(listOf(a1, a2))

        val result = activityDao.getActivitiesByCustomer("CST-001").first()

        assertEquals(1, result.size)
        assertEquals("CST-001", result.first().customerId)
    }

    @Test
    fun getActivitiesByDateRangeReturnsMatchingActivities() = runTest {
        val inRange  = sampleActivity.copy(activityId = "APT-001", activityDate = "2026-03-15")
        val outRange = sampleActivity.copy(activityId = "APT-002", activityDate = "2026-04-15")
        activityDao.insertActivities(listOf(inRange, outRange))

        val result = activityDao.getActivitiesByDateRange("2026-03-01", "2026-03-31").first()

        assertEquals(1, result.size)
        assertEquals("APT-001", result.first().activityId)
    }

    @Test
    fun deleteActivityByIdRemovesOnlyTargetRecord() = runTest {
        val a1 = sampleActivity.copy(activityId = "APT-001")
        val a2 = sampleActivity.copy(activityId = "APT-002")
        activityDao.insertActivities(listOf(a1, a2))

        activityDao.deleteActivityById("APT-001")

        val all = activityDao.getAllActivities().first()
        assertEquals(1, all.size)
        assertEquals("APT-002", all.first().activityId)
    }

    @Test
    fun deleteActivitiesByProjectIdRemovesAllForProject() = runTest {
        val a1 = sampleActivity.copy(activityId = "APT-001", projectId = "PJ-001")
        val a2 = sampleActivity.copy(activityId = "APT-002", projectId = "PJ-001")
        val a3 = sampleActivity.copy(activityId = "APT-003", projectId = "PJ-002")
        activityDao.insertActivities(listOf(a1, a2, a3))

        activityDao.deleteActivitiesByProjectId("PJ-001")

        val all = activityDao.getAllActivities().first()
        assertEquals(1, all.size)
        assertEquals("PJ-002", all.first().projectId)
    }

    @Test
    fun deleteActivitiesByCustomerIdRemovesAllForCustomer() = runTest {
        val a1 = sampleActivity.copy(activityId = "APT-001", customerId = "CST-001")
        val a2 = sampleActivity.copy(activityId = "APT-002", customerId = "CST-001")
        val a3 = sampleActivity.copy(activityId = "APT-003", customerId = "CST-002")
        activityDao.insertActivities(listOf(a1, a2, a3))

        activityDao.deleteActivitiesByCustomerId("CST-001")

        val all = activityDao.getAllActivities().first()
        assertEquals(1, all.size)
        assertEquals("CST-002", all.first().customerId)
    }

    @Test
    fun clearAndInsertReplacesAllExistingActivities() = runTest {
        activityDao.insertActivity(sampleActivity)

        val replacement = listOf(
            SalesActivity(
                activityId   = "APT-999",
                userId       = "USR-002",
                customerId   = "CST-999",
                activityType = "call",
                activityDate = "2026-05-01",
                status       = "completed"
            )
        )
        activityDao.clearAndInsert(replacement)

        val all = activityDao.getAllActivities().first()
        assertEquals(1, all.size)
        assertEquals("APT-999", all.first().activityId)
    }

    @Test
    fun insertActivityWithReplaceStrategyUpdatesExistingRecord() = runTest {
        activityDao.insertActivity(sampleActivity)

        val updated = sampleActivity.copy(status = "completed")
        activityDao.insertActivity(updated)

        val retrieved = activityDao.getActivityById("APT-001")
        assertEquals("completed", retrieved?.status)
    }

    @Test
    fun getActivityByIdReturnsNullWhenNotFound() = runTest {
        val retrieved = activityDao.getActivityById("NONEXISTENT")

        assertNull(retrieved)
    }

    @Test
    fun deleteAllActivitiesEmptiesTable() = runTest {
        activityDao.insertActivities(listOf(
            sampleActivity.copy(activityId = "APT-001"),
            sampleActivity.copy(activityId = "APT-002")
        ))

        activityDao.deleteAllActivities()

        val all = activityDao.getAllActivities().first()
        assertTrue(all.isEmpty())
    }

    @Test
    fun getAllActivitiesOrderedByDateDescending() = runTest {
        val older  = sampleActivity.copy(activityId = "APT-001", activityDate = "2026-01-01")
        val newer  = sampleActivity.copy(activityId = "APT-002", activityDate = "2026-06-01")
        val middle = sampleActivity.copy(activityId = "APT-003", activityDate = "2026-03-15")
        activityDao.insertActivities(listOf(older, newer, middle))

        val all = activityDao.getAllActivities().first()

        assertEquals("APT-002", all[0].activityId)
        assertEquals("APT-003", all[1].activityId)
        assertEquals("APT-001", all[2].activityId)
    }
}
