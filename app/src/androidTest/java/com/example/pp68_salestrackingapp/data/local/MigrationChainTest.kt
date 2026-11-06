package com.example.pp68_salestrackingapp.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * พิสูจน์ "เส้นทางอัปเกรด" ซึ่งเป็นความเสี่ยงที่ใหญ่ที่สุดของการขยับ Room version
 *
 * เทส migration รายตัว (MigrationNNToMMTest) พิสูจน์แค่ก้าวเดียว แต่ผู้ใช้จริงอัปเดตแอปข้ามหลาย
 * เวอร์ชันรวดเดียว เช่นเครื่องที่ยังอยู่ v53 อัปเดตมา v65 ต้องวิ่งผ่าน migration 12 ตัวติดกัน
 * ถ้ามีตัวใดตัวหนึ่งพัง **แอปจะ crash ทันทีที่เปิดหลังอัปเดต** ซึ่งร้ายแรงกว่าบั๊กทั่วไปมาก
 * เพราะผู้ใช้เข้าแอปไม่ได้เลยและงานออฟไลน์ที่ยังไม่ได้ซิงค์อาจหายไปด้วย
 *
 * เทสนี้จึงไล่ทุกจุดเริ่มต้นที่มี exported schema (53..64) แล้ว migrate ขึ้น 65 พร้อม validate
 * schema ปลายทางทุกรอบ และเช็คว่าข้อมูลเดิมรอดข้ามทั้งเส้น ไม่ใช่แค่ schema ถูก
 *
 * ใช้ [AppDatabase.ALL_MIGRATIONS] ตัวเดียวกับที่ DatabaseModule ใช้จริง ถ้ามีใครเพิ่ม migration
 * ใหม่แล้วลืมใส่ในลิสต์ เทสนี้จะจับได้ทันทีแทนที่จะไปเจอตอนผู้ใช้อัปเดตแล้วแอปพัง
 */
@RunWith(AndroidJUnit4::class)
class MigrationChainTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun everyStartingVersionMigratesCleanlyToLatest() {
        for (startVersion in OLDEST_EXPORTED_SCHEMA until LATEST_SCHEMA) {
            val dbName = "chain-migration-test-$startVersion"
            context.deleteDatabase(dbName)

            // คอลัมน์บังคับของสองตารางนี้เหมือนกันตลอดช่วง 53..64 (ตรวจจาก exported schema แล้ว)
            // จึงใช้ INSERT ชุดเดียวกันได้ทุกจุดเริ่มต้น
            helper.createDatabase(dbName, startVersion).apply {
                execSQL(
                    "INSERT INTO project(projectId, projectName, is_synced) " +
                        "VALUES ('P-$startVersion', 'โครงการก่อนอัปเกรด', 1)"
                )
                execSQL(
                    "INSERT INTO customer(cust_id, company_name, is_synced, is_lead) " +
                        "VALUES ('C-$startVersion', 'บริษัทก่อนอัปเกรด', 1, 1)"
                )
                close()
            }

            // validateDroppedTables = true: จับกรณี migration ลืมลบตารางเก่าทิ้งด้วย
            val migrated = helper.runMigrationsAndValidate(
                dbName,
                LATEST_SCHEMA,
                true,
                *AppDatabase.ALL_MIGRATIONS
            )

            assertEquals(
                "ข้อมูลโครงการต้องรอดจาก v$startVersion ถึง v$LATEST_SCHEMA",
                "โครงการก่อนอัปเกรด",
                migrated.text("SELECT projectName FROM project WHERE projectId = 'P-$startVersion'")
            )
            assertEquals(
                "ข้อมูลลูกค้าต้องรอดจาก v$startVersion ถึง v$LATEST_SCHEMA",
                "บริษัทก่อนอัปเกรด",
                migrated.text("SELECT company_name FROM customer WHERE cust_id = 'C-$startVersion'")
            )
            // คอลัมน์ audit ของเฟส 2 ต้องโผล่ครบไม่ว่าจะเริ่มจากเวอร์ชันไหน
            assertEquals(
                "แถวเก่าต้องได้ NULL ใน updatedBy ไม่ใช่ค่าขยะ",
                null,
                migrated.text("SELECT updatedBy FROM project WHERE projectId = 'P-$startVersion'")
            )

            migrated.close()
            context.deleteDatabase(dbName)
        }
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.text(sql: String): String? =
        query(sql).use { cursor ->
            cursor.moveToFirst()
            if (cursor.isNull(0)) null else cursor.getString(0)
        }

    private companion object {
        const val OLDEST_EXPORTED_SCHEMA = 53
        const val LATEST_SCHEMA = 65
    }
}
