package com.example.pp68_salestrackingapp.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration61To62Test {
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
    fun migrationAddsPlanEditTimeWithoutTouchingPendingRows() {
        helper.createDatabase(TEST_DATABASE, 61).apply {
            execSQL(
                """INSERT INTO activity_table(appointment_id, user_id, type, planned_date, plan_status, is_synced)
                   VALUES ('A-OFFLINE', 'U1', 'onsite', '2026-10-10', 'planned', 0)"""
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DATABASE,
            62,
            true,
            AppDatabase.MIGRATION_61_62
        )

        // แถวที่ค้างรอ sync ต้องไม่ถูกแตะ และต้องได้ค่าเริ่มต้นที่แปลว่า "ไม่รู้เวลาที่แก้"
        assertEquals(
            1,
            migrated.count(
                """SELECT COUNT(*) FROM activity_table
                   WHERE appointment_id = 'A-OFFLINE' AND is_synced = 0
                     AND plan_edit_at IS NULL AND plan_edit_time_trusted = 0"""
            )
        )

        migrated.execSQL(
            """UPDATE activity_table
               SET plan_edit_at = '2026-10-10T06:00:00Z', plan_edit_time_trusted = 1
               WHERE appointment_id = 'A-OFFLINE'"""
        )
        assertEquals(
            1,
            migrated.count(
                "SELECT COUNT(*) FROM activity_table WHERE plan_edit_time_trusted = 1"
            )
        )
        migrated.close()
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.count(sql: String): Int =
        query(sql).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }

    private companion object {
        const val TEST_DATABASE = "phase4b-plan-edit-time-migration-test"
    }
}
