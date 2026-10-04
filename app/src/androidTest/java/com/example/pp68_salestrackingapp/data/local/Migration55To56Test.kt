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
class Migration55To56Test {
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
    fun migrationAddsEmptyAttachmentOutboxWithoutChangingExistingResults() {
        helper.createDatabase(TEST_DATABASE, 55).apply {
            execSQL("""INSERT INTO activity_result
                (result_id, dm_involved, is_proposal_sent, competitor_count,
                 version, is_latest, is_synced, operation_id)
                VALUES ('R1', 0, 0, 0, 1, 1, 1, 'result-op')""")
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DATABASE,
            56,
            true,
            AppDatabase.MIGRATION_55_56
        )

        assertEquals(1, migrated.query("SELECT COUNT(*) FROM activity_result WHERE result_id = 'R1'").use {
            it.moveToFirst(); it.getInt(0)
        })
        assertEquals(0, migrated.query("SELECT COUNT(*) FROM attachment_outbox").use {
            it.moveToFirst(); it.getInt(0)
        })
        migrated.close()
    }

    private companion object {
        const val TEST_DATABASE = "phase2d-migration-test"
    }
}
