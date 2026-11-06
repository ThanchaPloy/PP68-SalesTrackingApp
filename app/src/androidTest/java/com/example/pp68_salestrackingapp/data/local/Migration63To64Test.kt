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
class Migration63To64Test {
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
    fun migrationAddsCallLogSyncTableAndKeepsExistingDataIntact() {
        helper.createDatabase(TEST_DATABASE, 63).apply {
            execSQL(
                // คอลัมน์ NOT NULL ต้องครบตาม exported schema ของ v63 (ดู Migration61To62Test)
                """INSERT INTO activity_table(
                       appointment_id, user_id, type, is_appointment, planned_date,
                       is_location_verified, plan_status, is_synced, plan_edit_time_trusted)
                   VALUES ('A1', 'U1', 'onsite', 1, '2026-10-10', 0, 'planned', 0, 0)"""
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DATABASE,
            64,
            true,
            AppDatabase.MIGRATION_63_64
        )

        // ข้อมูลเดิมต้องไม่หาย ตารางใหม่ไม่ได้แตะอะไรของใคร
        assertEquals(1, migrated.count("SELECT COUNT(*) FROM activity_table WHERE appointment_id = 'A1'"))

        // ตารางใหม่ใช้งานได้ตามรูปแบบที่ MIGRATION_63_64 สร้างไว้
        migrated.execSQL(
            """INSERT INTO call_log_sync(log_id, cust_id, phone_number, start_time, end_time, duration, is_synced)
               VALUES ('LOG-AAAAAAAA', 'C1', '0812345678', '2026-11-01T10:00:00Z', '2026-11-01T10:05:00Z', 300, 0)"""
        )
        assertEquals(1, migrated.count("SELECT COUNT(*) FROM call_log_sync WHERE log_id = 'LOG-AAAAAAAA'"))

        migrated.close()
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.count(sql: String): Int =
        query(sql).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }

    private companion object {
        const val TEST_DATABASE = "phase4c-call-log-migration-test"
    }
}
