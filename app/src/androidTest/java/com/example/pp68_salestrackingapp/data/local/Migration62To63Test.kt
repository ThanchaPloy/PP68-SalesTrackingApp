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
class Migration62To63Test {
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
    fun migrationAddsDraftTableAndKeepsAccountsApart() {
        helper.createDatabase(TEST_DATABASE, 62).apply {
            execSQL(
                """INSERT INTO activity_table(appointment_id, user_id, type, planned_date, plan_status, is_synced)
                   VALUES ('A1', 'U1', 'onsite', '2026-10-10', 'planned', 0)"""
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DATABASE,
            63,
            true,
            AppDatabase.MIGRATION_62_63
        )

        // ข้อมูลเดิมต้องไม่หาย ตารางใหม่ไม่ได้แตะอะไรของใคร
        assertEquals(1, migrated.count("SELECT COUNT(*) FROM activity_table WHERE appointment_id = 'A1'"))

        listOf("owner-a", "owner-b").forEachIndexed { index, owner ->
            migrated.execSQL(
                """INSERT INTO appointment_draft(
                       draft_id, owner_key, schema_version, payload_json, created_at, updated_at, expires_at)
                   VALUES ('D$index', '$owner', 1, '{}', '2026-10-06T03:00:00Z',
                           '2026-10-06T03:00:00Z', '2026-11-05T03:00:00Z')"""
            )
        }

        // ร่างของอีกบัญชีต้องไม่โผล่มาในผลลัพธ์ของบัญชีนี้
        assertEquals(
            1,
            migrated.count("SELECT COUNT(*) FROM appointment_draft WHERE owner_key = 'owner-a'")
        )
        migrated.close()
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.count(sql: String): Int =
        query(sql).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }

    private companion object {
        const val TEST_DATABASE = "phase4c-appointment-draft-migration-test"
    }
}
