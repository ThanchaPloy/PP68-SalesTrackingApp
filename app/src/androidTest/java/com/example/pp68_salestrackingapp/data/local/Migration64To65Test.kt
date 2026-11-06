package com.example.pp68_salestrackingapp.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Mobile phase 2 — เพิ่ม updated_at/updated_by ให้ 4 ตารางที่ backend ส่งค่ามาให้ตั้งแต่เฟส 1
 *
 * สิ่งที่เทสนี้ต้องพิสูจน์:
 *   1. ข้อมูลเดิมทั้ง 4 ตารางไม่หาย/ไม่เพี้ยนหลัง migrate (เป็น ALTER TABLE ADD COLUMN ล้วน)
 *   2. แถวเดิมที่มีอยู่ก่อน migration ได้ค่า NULL ในคอลัมน์ใหม่ ไม่ใช่ค่าขยะ
 *   3. คอลัมน์ใหม่เขียน/อ่านได้จริง (ชื่อคอลัมน์ตรงกับที่ entity ประกาศไว้ ไม่งั้น
 *      runMigrationsAndValidate จะ fail ตั้งแต่ขั้น validate schema อยู่แล้ว)
 */
@RunWith(AndroidJUnit4::class)
class Migration64To65Test {
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
    fun migrationAddsAuditColumnsWithoutTouchingExistingRows() {
        helper.createDatabase(TEST_DATABASE, 64).apply {
            execSQL(
                """INSERT INTO project(projectId, projectName, is_synced, remark)
                   VALUES ('P1', 'โครงการเดิม', 1, 'remark เดิม')"""
            )
            execSQL(
                """INSERT INTO customer(cust_id, company_name, is_synced, is_lead)
                   VALUES ('C1', 'บริษัทเดิม', 1, 1)"""
            )
            execSQL(
                """INSERT INTO activity_table(
                       appointment_id, user_id, type, is_appointment, planned_date,
                       is_location_verified, plan_status, is_synced, plan_edit_time_trusted)
                   VALUES ('A1', 'U1', 'onsite', 1, '2026-11-07', 0, 'planned', 1, 0)"""
            )
            execSQL(
                """INSERT INTO contact_person(contactId, custId, is_synced, fullName)
                   VALUES ('CT1', 'C1', 1, 'ผู้ติดต่อเดิม')"""
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DATABASE,
            65,
            true,
            AppDatabase.MIGRATION_64_65
        )

        // 1. ข้อมูลเดิมต้องอยู่ครบและค่าไม่เพี้ยน
        assertEquals(1, migrated.count("SELECT COUNT(*) FROM project WHERE projectId = 'P1'"))
        assertEquals("remark เดิม", migrated.text("SELECT remark FROM project WHERE projectId = 'P1'"))
        assertEquals(1, migrated.count("SELECT COUNT(*) FROM customer WHERE cust_id = 'C1'"))
        assertEquals(1, migrated.count("SELECT COUNT(*) FROM activity_table WHERE appointment_id = 'A1'"))
        assertEquals("ผู้ติดต่อเดิม", migrated.text("SELECT fullName FROM contact_person WHERE contactId = 'CT1'"))

        // 2. แถวเก่าต้องได้ NULL ในคอลัมน์ใหม่ (ไม่ใช่ค่าขยะ/ค่า default ที่ทำให้เข้าใจผิดว่าเคยถูกแก้)
        assertNull(migrated.text("SELECT updatedBy FROM project WHERE projectId = 'P1'"))
        assertNull(migrated.text("SELECT updated_at FROM customer WHERE cust_id = 'C1'"))
        assertNull(migrated.text("SELECT updated_by FROM customer WHERE cust_id = 'C1'"))
        assertNull(migrated.text("SELECT updated_at FROM activity_table WHERE appointment_id = 'A1'"))
        assertNull(migrated.text("SELECT updated_by FROM activity_table WHERE appointment_id = 'A1'"))
        assertNull(migrated.text("SELECT updated_at FROM contact_person WHERE contactId = 'CT1'"))
        assertNull(migrated.text("SELECT updated_by FROM contact_person WHERE contactId = 'CT1'"))

        // 3. คอลัมน์ใหม่เขียนค่าที่ backend ส่งมาได้จริง
        migrated.execSQL("UPDATE project SET updatedBy = 'E001' WHERE projectId = 'P1'")
        migrated.execSQL(
            "UPDATE activity_table SET updated_at = '2026-11-07T10:00:00Z', updated_by = 'E002' " +
                "WHERE appointment_id = 'A1'"
        )
        assertEquals("E001", migrated.text("SELECT updatedBy FROM project WHERE projectId = 'P1'"))
        assertEquals(
            "2026-11-07T10:00:00Z",
            migrated.text("SELECT updated_at FROM activity_table WHERE appointment_id = 'A1'")
        )
        assertEquals("E002", migrated.text("SELECT updated_by FROM activity_table WHERE appointment_id = 'A1'"))

        migrated.close()
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.count(sql: String): Int =
        query(sql).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.text(sql: String): String? =
        query(sql).use { cursor ->
            cursor.moveToFirst()
            if (cursor.isNull(0)) null else cursor.getString(0)
        }

    private companion object {
        const val TEST_DATABASE = "phase2-audit-columns-migration-test"
    }
}
