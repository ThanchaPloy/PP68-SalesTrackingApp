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
class Migration58To59Test {
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
    fun migrationAddsDurableAccountScopedConflictWithoutChangingExistingState() {
        helper.createDatabase(TEST_DATABASE, 58).apply {
            execSQL(
                """INSERT INTO sync_state(
                    account_key, stream, cursor, snapshot_cursor, bootstrap_status, updated_at_epoch_ms
                ) VALUES ('hash-a', 'main', 42, 40, 'READY', 1)"""
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DATABASE,
            59,
            true,
            AppDatabase.MIGRATION_58_59
        )
        migrated.execSQL(
            """INSERT INTO sync_conflict(
                account_key, entity_type, entity_id, operation, server_revision, server_seq,
                server_payload_json, detected_at_epoch_ms
            ) VALUES ('hash-a', 'project', 'P-1', 'UPSERT', 43, 43, '{}', 2)"""
        )

        assertEquals(42L, migrated.long("SELECT cursor FROM sync_state WHERE account_key = 'hash-a'"))
        assertEquals(1, migrated.count("SELECT COUNT(*) FROM sync_conflict WHERE account_key = 'hash-a'"))
        migrated.close()
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.count(sql: String): Int =
        query(sql).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.long(sql: String): Long =
        query(sql).use { cursor -> cursor.moveToFirst(); cursor.getLong(0) }

    private companion object {
        const val TEST_DATABASE = "phase3-delta-sync-conflict-migration-test"
    }
}
