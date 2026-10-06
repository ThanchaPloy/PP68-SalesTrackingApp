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
class Migration56To57Test {
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
    fun migrationBackfillsContactSnapshotAndRemovesOnlySyncedErpCustomers() {
        helper.createDatabase(TEST_DATABASE, 56).apply {
            execSQL("INSERT INTO customer (cust_id, company_name, is_synced, is_lead) VALUES ('ERP-1', 'ERP One', 1, 0)")
            execSQL("INSERT INTO customer (cust_id, company_name, is_synced, is_lead) VALUES ('LEAD-1', 'Lead One', 1, 1)")
            execSQL("INSERT INTO customer (cust_id, company_name, is_synced, is_lead) VALUES ('TEMP-1', 'Offline ERP', 0, 0)")
            execSQL("INSERT INTO contact_person (contactId, custId, fullName, is_synced) VALUES ('CT-1', 'ERP-1', 'Contact One', 1)")
            execSQL("INSERT INTO contact_person (contactId, custId, fullName, is_synced) VALUES ('CT-2', 'LEAD-1', 'Contact Two', 1)")
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DATABASE,
            57,
            true,
            AppDatabase.MIGRATION_56_57
        )

        assertEquals(0, migrated.count("SELECT COUNT(*) FROM customer WHERE cust_id = 'ERP-1'"))
        assertEquals(1, migrated.count("SELECT COUNT(*) FROM customer WHERE cust_id = 'LEAD-1'"))
        assertEquals(1, migrated.count("SELECT COUNT(*) FROM customer WHERE cust_id = 'TEMP-1'"))
        assertEquals("ERP One", migrated.text("SELECT customer_name FROM contact_person WHERE contactId = 'CT-1'"))
        assertEquals("Lead One", migrated.text("SELECT customer_name FROM contact_person WHERE contactId = 'CT-2'"))
        migrated.close()
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.count(sql: String): Int =
        query(sql).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.text(sql: String): String =
        query(sql).use { cursor -> cursor.moveToFirst(); cursor.getString(0) }

    private companion object {
        const val TEST_DATABASE = "phase3-customer-snapshot-migration-test"
    }
}
