package com.example.pp68_salestrackingapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.pp68_salestrackingapp.data.model.*

@Database(
    entities = [
        Customer::class,
        Project::class,
        SalesActivity::class,
        ContactPerson::class,
        Branch::class,
        ActivityPlanItem::class,
        ActivityResult::class,
        ProjectContact::class,
        AppointmentContact::class,
        ProjectSalesMember::class,
        ActivityResultPhoto::class
    ],
    version = 45,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun customerDao(): CustomerDao
    abstract fun projectDao(): ProjectDao
    abstract fun activityDao(): ActivityDao
    abstract fun contactDao(): ContactDao
    abstract fun branchDao(): BranchDao
    abstract fun activityPlanItemDao(): ActivityPlanItemDao
    abstract fun activityResultDao(): ActivityResultDao
    abstract fun appointmentContactDao(): AppointmentContactDao
    abstract fun projectContactDao(): ProjectContactDao
    abstract fun projectSalesMemberDao(): ProjectSalesMemberDao
    abstract fun activityResultPhotoDao(): ActivityResultPhotoDao

    fun clearAllData() {
        this.clearAllTables()
    }

    companion object {
        val MIGRATION_28_29 = object : Migration(28, 29) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE project_new (
                        projectId TEXT PRIMARY KEY NOT NULL,
                        custId TEXT NOT NULL,
                        branchId TEXT,
                        billingBranchId TEXT,
                        projectName TEXT NOT NULL,
                        expectedValue REAL,
                        projectStatus TEXT,
                        startDate TEXT,
                        closingDate TEXT,
                        desiredCompletionDate TEXT,
                        projectLat REAL,
                        projectLong REAL,
                        opportunityScore TEXT,
                        progressPct INTEGER,
                        createdAt TEXT,
                        lossReason TEXT
                    )
                """)
                db.execSQL("""
                    INSERT INTO project_new SELECT
                        projectId, custId, branchId, billingBranchId,
                        projectName, expectedValue, projectStatus,
                        startDate, closingDate, desiredCompletionDate,
                        projectLat, projectLong, opportunityScore,
                        progressPct, createdAt, lossReason
                    FROM project
                """)
                db.execSQL("DROP TABLE project")
                db.execSQL("ALTER TABLE project_new RENAME TO project")
            }
        }

        val MIGRATION_29_30 = object : Migration(29, 30) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE project ADD COLUMN updatedAt TEXT")
            }
        }

        val MIGRATION_31_32 = object : Migration(31, 32) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE customer_new (
                        custId TEXT PRIMARY KEY NOT NULL,
                        companyName TEXT NOT NULL,
                        branchId TEXT,
                        branch TEXT,
                        custType TEXT,
                        companyAddr TEXT,
                        companyLat REAL,
                        companyLong REAL,
                        companyStatus TEXT,
                        createdAt TEXT
                    )
                """)
                db.execSQL("""
                    INSERT INTO customer_new (
                        custId, companyName, branchId, branch, custType,
                        companyAddr, companyLat, companyLong, companyStatus, createdAt
                    ) SELECT
                        custId, companyName, branchId, branch, custType,
                        companyAddr, companyLat, companyLong, companyStatus, firstCustomerDate
                    FROM customer
                """)
                db.execSQL("DROP TABLE customer")
                db.execSQL("ALTER TABLE customer_new RENAME TO customer")
            }
        }

        val MIGRATION_32_33 = object : Migration(32, 33) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE customer ADD COLUMN user_id TEXT")
                db.execSQL("ALTER TABLE project ADD COLUMN user_id TEXT")
            }
        }

        val MIGRATION_34_35 = object : Migration(34, 35) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE project ADD COLUMN customerName TEXT")
                db.execSQL("ALTER TABLE project ADD COLUMN remark TEXT")
            }
        }

        val MIGRATION_33_34 = object : Migration(33, 34) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE customer ADD COLUMN grade INTEGER")
                db.execSQL("""
                    CREATE TABLE customer_new (
                        cust_id TEXT PRIMARY KEY NOT NULL,
                        company_name TEXT NOT NULL,
                        branch_id TEXT,
                        branch TEXT,
                        cust_type TEXT,
                        company_addr TEXT,
                        company_lat REAL,
                        company_long REAL,
                        company_status INTEGER,
                        created_at TEXT,
                        user_id TEXT,
                        grade INTEGER
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO customer_new (cust_id, company_name, branch_id, branch, cust_type,
                        company_addr, company_lat, company_long, created_at, user_id)
                    SELECT cust_id, company_name, branch_id, branch, cust_type,
                        company_addr, company_lat, company_long, created_at, user_id
                    FROM customer
                """.trimIndent())
                db.execSQL("DROP TABLE customer")
                db.execSQL("ALTER TABLE customer_new RENAME TO customer")
            }
        }

        val MIGRATION_35_36 = object : Migration(35, 36) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE project ADD COLUMN create_by TEXT")
            }
        }

        val MIGRATION_36_37 = object : Migration(36, 37) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE customer ADD COLUMN is_synced INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE project ADD COLUMN is_synced INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE contact_person ADD COLUMN is_synced INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE activity_table ADD COLUMN is_synced INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE activity_result ADD COLUMN is_synced INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_37_38 = object : Migration(37, 38) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS project_sales_member (
                        project_code TEXT NOT NULL,
                        emp_code TEXT NOT NULL,
                        sales_role TEXT NOT NULL DEFAULT 'support',
                        PRIMARY KEY(project_code, emp_code)
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_38_39 = object : Migration(38, 39) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE activity_table ADD COLUMN created_at TEXT")
            }
        }

        val MIGRATION_40_41 = object : Migration(40, 41) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `customer` ADD COLUMN `vat_registration_no` TEXT")
            }
        }

        val MIGRATION_39_40 = object : Migration(39, 40) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `appointment_contact`")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `appointment_contact` (
                        `appointment_id` TEXT NOT NULL,
                        `contact_id` TEXT NOT NULL,
                        PRIMARY KEY(`appointment_id`, `contact_id`),
                        FOREIGN KEY(`appointment_id`) REFERENCES `activity_table`(`appointment_id`) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_appointment_contact_appointment_id` ON `appointment_contact`(`appointment_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_appointment_contact_contact_id` ON `appointment_contact`(`contact_id`)")
            }
        }

        val MIGRATION_41_42 = object : Migration(41, 42) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // ✅ cust_id ต้องรองรับ null เพราะบางนัดหมายจากเซิร์ฟเวอร์ไม่ผูกกับลูกค้า
                db.execSQL("""
                    CREATE TABLE activity_table_new (
                        appointment_id TEXT NOT NULL PRIMARY KEY,
                        user_id TEXT NOT NULL,
                        cust_id TEXT,
                        project_id TEXT,
                        type TEXT NOT NULL,
                        is_appointment INTEGER NOT NULL,
                        topic TEXT,
                        planned_date TEXT NOT NULL,
                        planned_time TEXT,
                        planned_end_time TEXT,
                        planned_lat REAL,
                        planned_long REAL,
                        check_in_time TEXT,
                        check_in_lat REAL,
                        check_in_long REAL,
                        distance_deviation REAL,
                        is_location_verified INTEGER NOT NULL,
                        plan_status TEXT NOT NULL,
                        note TEXT,
                        created_at TEXT,
                        project_name TEXT,
                        company_name TEXT,
                        contact_name TEXT,
                        weekly_note TEXT,
                        is_synced INTEGER NOT NULL DEFAULT 1
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO activity_table_new SELECT
                        appointment_id, user_id, cust_id, project_id, type, is_appointment, topic,
                        planned_date, planned_time, planned_end_time, planned_lat, planned_long,
                        check_in_time, check_in_lat, check_in_long, distance_deviation, is_location_verified,
                        plan_status, note, created_at, project_name, company_name, contact_name, weekly_note, is_synced
                    FROM activity_table
                """.trimIndent())
                db.execSQL("DROP TABLE activity_table")
                db.execSQL("ALTER TABLE activity_table_new RENAME TO activity_table")
            }
        }

        val MIGRATION_42_43 = object : Migration(42, 43) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // ✅ รองรับ version history ของบันทึกผลการขาย
                db.execSQL("ALTER TABLE activity_result ADD COLUMN version INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE activity_result ADD COLUMN is_latest INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE activity_result ADD COLUMN result_group_id TEXT")
                db.execSQL("UPDATE activity_result SET result_group_id = result_id WHERE result_group_id IS NULL")
            }
        }

        val MIGRATION_43_44 = object : Migration(43, 44) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // ✅ รองรับหลายรูปต่อบันทึกผลการขาย (สูงสุด 5 รูป)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `activity_result_photo` (
                        `result_id` TEXT NOT NULL,
                        `photo_order` INTEGER NOT NULL,
                        `photo_url` TEXT NOT NULL,
                        PRIMARY KEY(`result_id`, `photo_order`),
                        FOREIGN KEY(`result_id`) REFERENCES `activity_result`(`result_id`) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_activity_result_photo_result_id` ON `activity_result_photo`(`result_id`)")
            }
        }

        val MIGRATION_44_45 = object : Migration(44, 45) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE customer ADD COLUMN is_lead INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
