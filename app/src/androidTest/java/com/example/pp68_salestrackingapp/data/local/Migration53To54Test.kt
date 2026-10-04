package com.example.pp68_salestrackingapp.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the first Phase 2 schema change against the exported v53 schema.
 *
 * Unsynced rows are deliberately inserted before migration because losing those rows is the
 * highest-risk failure mode for an offline-first application.
 */
@RunWith(AndroidJUnit4::class)
class Migration53To54Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @After
    fun deleteDatabase() {
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun migrationPreservesPendingRowsAndCreatesMeasuredIndexes() {
        helper.createDatabase(TEST_DATABASE, 53).apply {
            execSQL(
                """INSERT INTO customer
                    (cust_id, company_name, user_id, is_synced, is_lead)
                    VALUES ('TEMP-C1', 'Pending customer', 'U1', 0, 1)"""
            )
            execSQL(
                """INSERT INTO project
                    (projectId, projectName, projectStatus, startDate, create_by, is_synced)
                    VALUES ('TEMP-P1', 'Pending project', 'Quotation', '2026-10-04', 'U1', 0)"""
            )
            execSQL(
                """INSERT INTO activity_table
                    (appointment_id, user_id, type, is_appointment, planned_date,
                     is_location_verified, plan_status, is_synced)
                    VALUES ('TEMP-A1', 'U1', 'Visit', 1, '2026-10-04', 0, 'planned', 0)"""
            )
            execSQL(
                """INSERT INTO activity_result
                    (result_id, project_id, report_date, dm_involved, is_proposal_sent,
                     competitor_count, version, is_latest, is_synced)
                    VALUES ('TEMP-R1', 'TEMP-P1', '2026-10-04', 0, 0, 0, 1, 1, 0)"""
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DATABASE,
            54,
            true,
            AppDatabase.MIGRATION_53_54
        )

        assertEquals(1, migrated.count("customer", "cust_id = 'TEMP-C1' AND is_synced = 0"))
        assertEquals(1, migrated.count("project", "projectId = 'TEMP-P1' AND is_synced = 0"))
        assertEquals(1, migrated.count("activity_table", "appointment_id = 'TEMP-A1' AND is_synced = 0"))
        assertEquals(1, migrated.count("activity_result", "result_id = 'TEMP-R1' AND is_synced = 0"))

        val actualIndexes = migrated.query(
            "SELECT name FROM sqlite_master WHERE type = 'index'"
        ).use { cursor ->
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }
        assertTrue(
            "Missing Phase 2 indexes: ${EXPECTED_INDEXES - actualIndexes}",
            actualIndexes.containsAll(EXPECTED_INDEXES)
        )
        migrated.close()
    }

    private fun SupportSQLiteDatabase.count(table: String, where: String): Int =
        query("SELECT COUNT(*) FROM $table WHERE $where").use { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private companion object {
        const val TEST_DATABASE = "phase2-migration-test"
        val EXPECTED_INDEXES = setOf(
            "index_customer_company_name",
            "index_customer_is_synced",
            "index_customer_user_id",
            "index_contact_customer_id",
            "index_contact_is_synced",
            "index_project_start_date_id",
            "index_project_status_start_date_id",
            "index_project_is_synced",
            "index_activity_user_date_id",
            "index_activity_is_synced",
            "index_result_project_latest_date_id",
            "index_result_group_version_id",
            "index_result_is_synced"
        )
    }
}
