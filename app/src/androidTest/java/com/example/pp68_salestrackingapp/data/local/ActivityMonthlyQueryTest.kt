package com.example.pp68_salestrackingapp.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pp68_salestrackingapp.data.model.ActivityResult
import com.example.pp68_salestrackingapp.data.model.Customer
import com.example.pp68_salestrackingapp.data.model.Project
import com.example.pp68_salestrackingapp.data.model.SalesActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActivityMonthlyQueryTest {
    private lateinit var database: AppDatabase

    @Before
    fun createDatabase() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        database.customerDao().insertCustomers(
            listOf(Customer(custId = "C1", companyName = "Joined customer"))
        )
        database.projectDao().insertProjects(
            listOf(Project(projectId = "P1", projectName = "Joined project"))
        )
        database.activityDao().insertActivities(
            listOf(
                activity("A-LATE", "U1", "2026-10-15", "14:00"),
                activity("A-EARLY", "U1", "2026-10-15", "09:00"),
                activity("A-OTHER-MONTH", "U1", "2026-11-01", "08:00"),
                activity("A-OTHER-USER", "U2", "2026-10-10", "08:00"),
                activity("A-INVALID-DATE", "U1", "unknown", "08:00")
            )
        )
        database.activityResultDao().insertResult(
            ActivityResult(resultId = "R1", activityId = "A-EARLY")
        )
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun queryReturnsOnlySelectedUserAndMonthWithJoinedNamesAndResultState() = runBlocking<Unit> {
        val rows = database.activityDao()
            .getActivityCardsForMonth("U1", "2026-10-01", "2026-11-01")
            .first()

        assertEquals(
            listOf("A-EARLY", "A-LATE", "A-INVALID-DATE"),
            rows.map(ActivityCardRow::activityId)
        )
        assertEquals("Joined customer", rows.first().companyName)
        assertEquals("Joined project", rows.first().projectName)
        assertTrue(rows.first().hasResult)
        assertEquals(false, rows.last().hasResult)

        val plan = database.openHelper.readableDatabase.query(
            SimpleSQLiteQuery(
                """EXPLAIN QUERY PLAN
                    SELECT a.appointment_id
                    FROM activity_table a
                    WHERE a.user_id = 'U1'
                      AND a.planned_date >= '2026-10-01'
                      AND a.planned_date < '2026-11-01'
                    ORDER BY a.planned_date ASC, a.planned_time ASC, a.appointment_id ASC"""
            )
        ).use { cursor ->
            val detail = cursor.getColumnIndexOrThrow("detail")
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(detail))
            }
        }
        assertTrue(plan.joinToString().contains("index_activity_user_date_id"))
        assertTrue(plan.none { it.contains("USE TEMP B-TREE") })
    }

    private fun activity(id: String, userId: String, date: String, time: String) =
        SalesActivity(
            activityId = id,
            userId = userId,
            customerId = "C1",
            projectId = "P1",
            activityType = "onsite",
            activityDate = date,
            plannedTime = time,
            status = "planned"
        )
}
