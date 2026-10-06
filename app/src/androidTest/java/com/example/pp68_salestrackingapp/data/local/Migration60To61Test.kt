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
class Migration60To61Test {
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
    fun migrationAddsDurableLocalIdMappingWithoutChangingPendingRows() {
        helper.createDatabase(TEST_DATABASE, 60).apply {
            execSQL(
                """INSERT INTO project(projectId, projectName, is_synced)
                   VALUES ('TEMP-P', 'Offline project', 0)"""
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DATABASE,
            61,
            true,
            AppDatabase.MIGRATION_60_61
        )
        migrated.execSQL(
            """INSERT INTO local_id_mapping(entity_type, temp_id, real_id, created_at_epoch_ms)
               VALUES ('project', 'TEMP-P', 'P1', 1)"""
        )

        assertEquals(1, migrated.count("SELECT COUNT(*) FROM project WHERE projectId = 'TEMP-P' AND is_synced = 0"))
        assertEquals(1, migrated.count("SELECT COUNT(*) FROM local_id_mapping WHERE temp_id = 'TEMP-P' AND real_id = 'P1'"))
        migrated.close()
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.count(sql: String): Int =
        query(sql).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }

    private companion object {
        const val TEST_DATABASE = "phase3-local-id-mapping-migration-test"
    }
}
