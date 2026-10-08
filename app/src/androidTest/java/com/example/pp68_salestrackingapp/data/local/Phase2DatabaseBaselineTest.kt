package com.example.pp68_salestrackingapp.data.local

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.example.pp68_salestrackingapp.data.model.Customer
import com.example.pp68_salestrackingapp.data.model.Project
import java.util.Locale
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Baseline ก่อนเปลี่ยน list screens เป็น Paging 3 หรือเพิ่ม index
 *
 * เทสต์นี้ไม่ตั้ง performance threshold เพราะเวลา emulator ไม่ใช่ SLO ของเครื่องจริง แต่พิมพ์ผลด้วย
 * tag `Phase2Baseline` เพื่อให้เปรียบเทียบ build ก่อน/หลังบน Android 12/API 31, RAM 4 GB ได้
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class Phase2DatabaseBaselineTest {
    private lateinit var db: AppDatabase

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDatabase() {
        db.close()
    }

    @Test
    fun baselineWithFiveThousandCustomersAndProjects() = runBlocking<Unit> {
        val customers = (1..ROW_COUNT).map { index ->
            Customer(
                custId = "C%05d".format(Locale.US, index),
                companyName = "Customer %05d".format(Locale.US, index),
                bizPostingGroup = listOf("R", "W", "I", "P")[index % 4],
                custType = if (index % 2 == 0) "Developer" else "Main Contractor",
                createdBy = "U${index % 50}",
                isLead = index % 5 == 0
            )
        }
        val projects = (1..ROW_COUNT).map { index ->
            Project(
                projectId = "P%05d".format(Locale.US, index),
                custId = customers[index - 1].custId,
                projectName = "Project %05d".format(Locale.US, index),
                projectStatus = listOf("Quotation", "Bidding", "PO", "Lost")[index % 4],
                opportunityScore = listOf("HOT", "WARM", "COLD", null)[index % 4],
                startDate = "2026-%02d-%02d".format(Locale.US, (index % 12) + 1, (index % 28) + 1),
                createBy = "U${index % 50}"
            )
        }

        val customerWriteMs = measureTimeMillis { db.customerDao().insertCustomers(customers) }
        val projectWriteMs = measureTimeMillis { db.projectDao().insertProjects(projects) }

        lateinit var loadedCustomers: List<Customer>
        lateinit var loadedProjects: List<Project>
        val customerReadMs = measureTimeMillis {
            loadedCustomers = db.customerDao().getAllCustomers(OWNER).first()
        }
        val projectReadMs = measureTimeMillis {
            loadedProjects = db.projectDao().getAllProjects().first()
        }
        val customerSearchMs = measureTimeMillis {
            db.customerDao().searchCustomers("%Customer 04999%").first()
        }
        val projectSearchMs = measureTimeMillis {
            db.projectDao().searchProjects("%Project 04999%").first()
        }

        assertEquals(ROW_COUNT, loadedCustomers.size)
        assertEquals(ROW_COUNT, loadedProjects.size)
        Log.i(
            TAG,
            "rows=$ROW_COUNT customer_write_ms=$customerWriteMs project_write_ms=$projectWriteMs " +
                "customer_full_read_ms=$customerReadMs project_full_read_ms=$projectReadMs " +
                "customer_contains_search_ms=$customerSearchMs project_contains_search_ms=$projectSearchMs"
        )

        val plans = BASELINE_QUERIES.mapValues { (_, sql) -> explain(sql) }
        plans.forEach { (name, details) ->
            assertTrue("EXPLAIN QUERY PLAN returned no rows for $name", details.isNotEmpty())
            Log.i(TAG, "query=$name plan=${details.joinToString(" | ")}")
        }
    }

    private fun explain(sql: String): List<String> =
        db.openHelper.readableDatabase.query(SimpleSQLiteQuery("EXPLAIN QUERY PLAN $sql"))
            .use { cursor ->
                val detailIndex = cursor.getColumnIndexOrThrow("detail")
                buildList {
                    while (cursor.moveToNext()) add(cursor.getString(detailIndex))
                }
            }

    private companion object {
        const val TAG = "Phase2Baseline"
        // แถวถูกกระจายเจ้าของเป็น U0..U49 อ่านในมุมของคนหนึ่งคนตามที่ query จริงทำ
        const val OWNER = "U1"
        const val ROW_COUNT = 5_000
        val BASELINE_QUERIES = linkedMapOf(
            "customer_list" to "SELECT * FROM customer ORDER BY company_name ASC LIMIT 50",
            "customer_search" to "SELECT * FROM customer WHERE company_name LIKE '%4999%' ORDER BY company_name ASC LIMIT 50",
            "customer_pending" to "SELECT * FROM customer WHERE is_synced = 0 LIMIT 100",
            "project_list" to "SELECT * FROM project ORDER BY startDate DESC, projectId ASC LIMIT 50",
            "project_active" to "SELECT * FROM project WHERE projectStatus NOT IN ('PO','Lost','Failed') ORDER BY startDate DESC, projectId ASC LIMIT 50",
            "project_pending" to "SELECT * FROM project WHERE is_synced = 0 LIMIT 100",
            "activity_by_user_date" to "SELECT * FROM activity_table WHERE user_id = 'U1' ORDER BY planned_date DESC LIMIT 50",
            "latest_result_by_project" to "SELECT * FROM activity_result WHERE project_id = 'P00001' AND is_latest = 1 ORDER BY report_date DESC LIMIT 50"
        )
    }
}
