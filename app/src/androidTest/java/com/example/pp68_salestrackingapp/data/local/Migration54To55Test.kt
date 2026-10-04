package com.example.pp68_salestrackingapp.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration54To55Test {
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
    fun migrationAddsDurableOperationIdsWithoutLosingPendingRows() {
        helper.createDatabase(TEST_DATABASE, 54).apply {
            execSQL("""INSERT INTO customer
                (cust_id, company_name, is_synced, is_lead)
                VALUES ('TEMP-C1', 'Pending customer', 0, 1)""")
            execSQL("""INSERT INTO project
                (projectId, projectName, is_synced)
                VALUES ('TEMP-P1', 'Pending project', 0)""")
            execSQL("""INSERT INTO activity_table
                (appointment_id, user_id, type, is_appointment, planned_date,
                 is_location_verified, plan_status, is_synced)
                VALUES ('TEMP-A1', 'U1', 'Visit', 1, '2026-10-04', 0, 'planned', 0)""")
            execSQL("""INSERT INTO activity_result
                (result_id, dm_involved, is_proposal_sent, competitor_count,
                 version, is_latest, is_synced)
                VALUES ('TEMP-R1', 0, 0, 0, 1, 1, 0)""")
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DATABASE,
            55,
            true,
            AppDatabase.MIGRATION_54_55
        )

        val ids = listOf(
            migrated.operationId("customer", "cust_id", "TEMP-C1"),
            migrated.operationId("project", "projectId", "TEMP-P1"),
            migrated.operationId("activity_table", "appointment_id", "TEMP-A1"),
            migrated.operationId("activity_result", "result_id", "TEMP-R1")
        )
        assertTrue(ids.all { it.length == 36 })
        assertEquals(4, ids.toSet().size)

        val firstId = ids.first()
        migrated.close()

        // Reopening the migrated database must keep the same key; retry must never generate a new one.
        val reopened = helper.runMigrationsAndValidate(TEST_DATABASE, 55, true)
        assertEquals(firstId, reopened.operationId("customer", "cust_id", "TEMP-C1"))
        assertNotEquals("", firstId)
        reopened.close()
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.operationId(
        table: String,
        idColumn: String,
        id: String
    ): String = query("SELECT operation_id FROM $table WHERE $idColumn = ?", arrayOf(id)).use { cursor ->
        check(cursor.moveToFirst())
        cursor.getString(0)
    }

    private companion object {
        const val TEST_DATABASE = "phase2c-migration-test"
    }
}
