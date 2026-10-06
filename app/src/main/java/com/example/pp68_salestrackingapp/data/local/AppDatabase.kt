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
        ActivityResultPhoto::class,
        SyncRejection::class,
        AttachmentOutbox::class,
        SyncState::class,
        SyncConflict::class,
        SyncSnapshotItem::class,
        LocalIdMapping::class,
        AppointmentDraft::class
    ],
    version = 63,
    exportSchema = true
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
    abstract fun activityResultPhotoDao(): ActivityResultPhotoDao
    abstract fun syncRejectionDao(): SyncRejectionDao
    abstract fun attachmentOutboxDao(): AttachmentOutboxDao
    abstract fun syncStateDao(): SyncStateDao
    abstract fun syncConflictDao(): SyncConflictDao
    abstract fun syncSnapshotDao(): SyncSnapshotDao
    abstract fun localIdMappingDao(): LocalIdMappingDao
    abstract fun appointmentDraftDao(): AppointmentDraftDao

    // clearAllData() ถูกลบออก — เป็น wrapper บาง ๆ ของ clearAllTables() ที่ไม่มีใครเรียกเลย
    // ตัวที่ใช้งานจริงคือ clearAllTables() ที่ AuthRepository เรียกตอน login คนละคน/logout ซึ่งมี
    // การ์ดกันงานค้างหายอยู่แล้ว การมีชื่อที่สองสำหรับ "ล้างข้อมูลทั้งเครื่อง" ชวนให้เรียกผิดจุดเปล่า ๆ

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

        // ไม่พบหลักฐานว่ามีการเปลี่ยน schema จริงระหว่าง v30 กับ v31 (ทุก migration ตั้งแต่ 31_32
        // เป็นต้นไปสมมติ schema เดียวกับที่ MIGRATION_29_30 ทิ้งไว้) — ใส่เป็น no-op ไว้ปิดช่องว่าง
        // เพื่อไม่ให้ผู้ใช้ที่ค้างอยู่ที่ v30 หรือต่ำกว่าโดน fallbackToDestructiveMigration() ล้างข้อมูล
        val MIGRATION_30_31 = object : Migration(30, 31) {
            override fun migrate(db: SupportSQLiteDatabase) {}
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

        val MIGRATION_45_46 = object : Migration(45, 46) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE project_new (
                        projectId TEXT PRIMARY KEY NOT NULL,
                        custId TEXT,
                        customerName TEXT,
                        branchId TEXT,
                        billingBranchId TEXT,
                        projectName TEXT NOT NULL,
                        remark TEXT,
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
                        updatedAt TEXT,
                        lossReason TEXT,
                        user_id TEXT,
                        create_by TEXT,
                        is_synced INTEGER NOT NULL DEFAULT 1
                    )
                """)
                db.execSQL("""
                    INSERT INTO project_new SELECT
                        projectId, custId, customerName, branchId, billingBranchId,
                        projectName, remark, expectedValue, projectStatus,
                        startDate, closingDate, desiredCompletionDate,
                        projectLat, projectLong, opportunityScore,
                        progressPct, createdAt, updatedAt, lossReason,
                        user_id, create_by, is_synced
                    FROM project
                """)
                db.execSQL("DROP TABLE project")
                db.execSQL("ALTER TABLE project_new RENAME TO project")
            }
        }

        // ✅ 1 โครงการมีเจ้าของคนเดียว (project.create_by) — เลิกใช้ตาราง M2M นี้แล้ว
        val MIGRATION_46_47 = object : Migration(46, 47) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS project_sales_member")
            }
        }

        // ✅ cache ชื่อสถานที่ที่ reverse geocode มาแล้ว กันยิงซ้ำทุกครั้งที่ export
        val MIGRATION_47_48 = object : Migration(47, 48) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE activity_table ADD COLUMN location_name TEXT")
            }
        }

        // checklist ไม่เคยมีธงซิงค์ ทำให้ outbox มองข้ามทั้งตาราง — ติ๊กตอนออฟไลน์แล้วหายถาวร
        // ตั้งค่าเริ่มต้นเป็น 1 เพราะแถวที่มีอยู่ตอนอัปเกรดคือแถวที่ซิงค์แล้วทั้งหมด
        val MIGRATION_48_49 = object : Migration(48, 49) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE activity_plan_item ADD COLUMN is_synced INTEGER NOT NULL DEFAULT 1")
            }
        }

        // W4: แยกรหัสเหตุผลที่แพ้ออกจากข้อความอิสระ — loss_reason เดิมเก็บทั้งรหัส 3 แบบ และ
        // ข้อความที่พิมพ์เองปนกัน คอลัมน์ใหม่นี้เก็บเฉพาะข้อความอิสระ ส่วน loss_reason จะเหลือแค่รหัส
        val MIGRATION_49_50 = object : Migration(49, 50) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE project ADD COLUMN lossReasonNote TEXT")
                db.execSQL("ALTER TABLE activity_result ADD COLUMN loss_reason_note TEXT")
            }
        }

        // W6-2: ปัจจัยข้อ 4-7 sync มาจาก activity_result ล่าสุดของโครงการผ่าน backend trigger
        // (เหมือน opportunityScore) ให้ project เก็บสำเนาไว้ในเครื่องด้วย เพื่อ prefill หน้าบันทึกผล
        val MIGRATION_50_51 = object : Migration(50, 51) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE project ADD COLUMN dealPosition TEXT")
                db.execSQL("ALTER TABLE project ADD COLUMN previousSolution TEXT")
                db.execSQL("ALTER TABLE project ADD COLUMN counterpartyType TEXT")
                db.execSQL("ALTER TABLE project ADD COLUMN responseSpeed TEXT")
            }
        }

        // ปัจจัยข้อ 8-9 ย้ายมาอยู่ระดับโครงการชุดเดียวกับข้อ 4-7 ใน MIGRATION_50_51 ด้านบน
        // ต้อง nullable ทั้งสามคอลัมน์ (ไม่มี NOT NULL DEFAULT) เพราะ null = ยังไม่เคยตอบ
        // ซึ่งต่างจาก 0/false ที่แปลว่าตอบแล้ว — ถ้าใส่ DEFAULT จะแยกสองกรณีนี้ไม่ออก
        val MIGRATION_51_52 = object : Migration(51, 52) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE project ADD COLUMN isProposalSent INTEGER")
                db.execSQL("ALTER TABLE project ADD COLUMN proposalDate TEXT")
                db.execSQL("ALTER TABLE project ADD COLUMN competitorCount INTEGER")
            }
        }

        // แยก "ยังส่งไม่สำเร็จเพราะเน็ต" ออกจาก "เซิร์ฟเวอร์ปฏิเสธถาวร" — เดิมรวมอยู่ใน is_synced = 0
        // เหมือนกันหมด ด่าน logout จึงบล็อกทั้งที่แถวบางส่วนไม่มีวันส่งผ่านไม่ว่าเน็ตจะดีแค่ไหน
        val MIGRATION_52_53 = object : Migration(52, 53) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sync_rejection (
                        entity_type TEXT NOT NULL,
                        entity_id   TEXT NOT NULL,
                        http_code   INTEGER NOT NULL,
                        reason      TEXT,
                        rejected_at TEXT NOT NULL,
                        PRIMARY KEY(entity_type, entity_id)
                    )
                    """.trimIndent()
                )
            }
        }

        // Phase 2A: เพิ่มเฉพาะ index ที่ baseline 5,000 แถวพบว่าเป็น full scan หรือใช้ temp B-tree
        // migration นี้ไม่แก้หรือลบ business row จึงรักษา pending/TEMP data เดิมทั้งหมด
        val MIGRATION_53_54 = object : Migration(53, 54) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_customer_company_name ON customer(company_name)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_customer_is_synced ON customer(is_synced)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_customer_user_id ON customer(user_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_contact_customer_id ON contact_person(custId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_contact_is_synced ON contact_person(is_synced)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_project_start_date_id ON project(startDate DESC, projectId ASC)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_project_status_start_date_id ON project(projectStatus ASC, startDate DESC, projectId ASC)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_project_is_synced ON project(is_synced)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_activity_user_date_id ON activity_table(user_id, planned_date, planned_time, appointment_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_activity_is_synced ON activity_table(is_synced)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_result_project_latest_date_id ON activity_result(project_id, is_latest, report_date, result_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_result_group_version_id ON activity_result(result_group_id ASC, version DESC, result_id ASC)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_result_is_synced ON activity_result(is_synced)")
            }
        }

        // Phase 2C: operation ID ต้องอยู่กับแถว TEMP เพื่อให้ retry หลัง process death/reboot
        // ใช้ key เดิมเสมอ ทุกคอลัมน์ nullable เพื่อรักษา compatibility กับข้อมูลที่ sync แล้วเดิม
        val MIGRATION_54_55 = object : Migration(54, 55) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE customer ADD COLUMN operation_id TEXT")
                db.execSQL("ALTER TABLE project ADD COLUMN operation_id TEXT")
                db.execSQL("ALTER TABLE activity_table ADD COLUMN operation_id TEXT")
                db.execSQL("ALTER TABLE activity_result ADD COLUMN operation_id TEXT")
                val uuidExpression = """
                    lower(
                        hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-' ||
                        hex(randomblob(2)) || '-' || hex(randomblob(2)) || '-' ||
                        hex(randomblob(6))
                    )
                """.trimIndent()
                db.execSQL("UPDATE customer SET operation_id = $uuidExpression WHERE is_synced = 0 AND cust_id LIKE 'TEMP-%'")
                db.execSQL("UPDATE project SET operation_id = $uuidExpression WHERE is_synced = 0 AND projectId LIKE 'TEMP-%'")
                db.execSQL("UPDATE activity_table SET operation_id = $uuidExpression WHERE is_synced = 0 AND appointment_id LIKE 'TEMP-%'")
                db.execSQL("UPDATE activity_result SET operation_id = $uuidExpression WHERE is_synced = 0 AND result_id LIKE 'TEMP-%'")
            }
        }

        // Phase 2D: รูปเก่าคงอ่านจาก URL เดิม เฉพาะรูปใหม่เท่านั้นที่เข้าคิวถาวรนี้
        val MIGRATION_55_56 = object : Migration(55, 56) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `attachment_outbox` (
                        `operation_id` TEXT NOT NULL,
                        `owner_id` TEXT NOT NULL,
                        `result_id` TEXT NOT NULL,
                        `photo_order` INTEGER NOT NULL,
                        `local_path` TEXT NOT NULL,
                        `mime_type` TEXT NOT NULL,
                        `sha256` TEXT NOT NULL,
                        `size_bytes` INTEGER NOT NULL,
                        `state` TEXT NOT NULL,
                        `remote_url` TEXT,
                        `attempt_count` INTEGER NOT NULL,
                        `last_error` TEXT,
                        `created_at` TEXT NOT NULL,
                        `updated_at` TEXT NOT NULL,
                        PRIMARY KEY(`operation_id`),
                        FOREIGN KEY(`result_id`) REFERENCES `activity_result`(`result_id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_attachment_outbox_result_id` ON `attachment_outbox` (`result_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_attachment_outbox_owner_id_state` ON `attachment_outbox` (`owner_id`, `state`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_attachment_outbox_result_id_photo_order` ON `attachment_outbox` (`result_id`, `photo_order`)")
            }
        }

        // Phase 3: retain only the customer code/name snapshot needed by contact history,
        // then remove successfully synced ERP customer rows from the local customer table.
        // Unsynced rows are preserved so offline work cannot be lost during an upgrade.
        val MIGRATION_56_57 = object : Migration(56, 57) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `contact_person` ADD COLUMN `customer_name` TEXT")
                db.execSQL(
                    """
                    UPDATE `contact_person`
                    SET `customer_name` = (
                        SELECT `company_name`
                        FROM `customer`
                        WHERE `customer`.`cust_id` = `contact_person`.`custId`
                    )
                    WHERE `customer_name` IS NULL
                    """.trimIndent()
                )
                db.execSQL("DELETE FROM `customer` WHERE `is_synced` = 1 AND `is_lead` = 0")
            }
        }

        val MIGRATION_57_58 = object : Migration(57, 58) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sync_state` (
                        `account_key` TEXT NOT NULL,
                        `stream` TEXT NOT NULL,
                        `cursor` INTEGER NOT NULL,
                        `snapshot_cursor` INTEGER,
                        `bootstrap_status` TEXT NOT NULL,
                        `updated_at_epoch_ms` INTEGER NOT NULL,
                        PRIMARY KEY(`account_key`, `stream`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_58_59 = object : Migration(58, 59) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sync_conflict` (
                        `account_key` TEXT NOT NULL,
                        `entity_type` TEXT NOT NULL,
                        `entity_id` TEXT NOT NULL,
                        `operation` TEXT NOT NULL,
                        `server_revision` INTEGER NOT NULL,
                        `server_seq` INTEGER NOT NULL,
                        `server_payload_json` TEXT,
                        `detected_at_epoch_ms` INTEGER NOT NULL,
                        PRIMARY KEY(`account_key`, `entity_type`, `entity_id`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_59_60 = object : Migration(59, 60) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sync_snapshot_item` (
                        `account_key` TEXT NOT NULL,
                        `entity_type` TEXT NOT NULL,
                        `item_key` TEXT NOT NULL,
                        `payload_json` TEXT NOT NULL,
                        PRIMARY KEY(`account_key`, `entity_type`, `item_key`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_60_61 = object : Migration(60, 61) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `local_id_mapping` (
                        `entity_type` TEXT NOT NULL,
                        `temp_id` TEXT NOT NULL,
                        `real_id` TEXT NOT NULL,
                        `created_at_epoch_ms` INTEGER NOT NULL,
                        PRIMARY KEY(`entity_type`, `temp_id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_local_id_mapping_entity_type_real_id` " +
                        "ON `local_id_mapping` (`entity_type`, `real_id`)"
                )
            }
        }

        /**
         * B.4: จำไว้ว่าการแก้แผนเกิดขึ้นเมื่อไหร่ เพื่อให้การแก้ตอนออฟไลน์ก่อนเวลานัด
         * ยังผ่านกติกาได้แม้จะถูก sync ขึ้นไปหลังเวลานัดไปแล้ว
         *
         * additive ล้วน แถวเดิมได้ NULL/0 ซึ่งแปลว่า "ไม่มีข้อมูลเวลาแก้" และถูกตัดสิน
         * ด้วยเวลาที่ server เห็นตอนรับคำขอเหมือนเดิม
         */
        /**
         * C.1: ฉบับร่างนัดหมายหลายรายการ ย้ายจาก SharedPreferences มาอยู่ Room
         *
         * แผนเขียนไว้ว่า 61 -> 62 แต่ 62 ถูกใช้ไปกับงาน B.4 แล้ว จึงเป็น 62 -> 63
         *
         * ตารางใหม่ล้วน ไม่แตะข้อมูลเดิม การย้ายร่างเก่าจาก SharedPreferences ทำในแอป
         * ไม่ใช่ใน migration เพราะต้องพิสูจน์เจ้าของก่อน ซึ่ง SQL ไม่รู้
         */
        val MIGRATION_62_63 = object : Migration(62, 63) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `appointment_draft` (
                        `draft_id` TEXT NOT NULL,
                        `owner_key` TEXT NOT NULL,
                        `schema_version` INTEGER NOT NULL,
                        `title` TEXT,
                        `planned_date` TEXT,
                        `planned_time` TEXT,
                        `project_id` TEXT,
                        `project_name_snapshot` TEXT,
                        `customer_id` TEXT,
                        `customer_name_snapshot` TEXT,
                        `payload_json` TEXT NOT NULL,
                        `created_at` TEXT NOT NULL,
                        `updated_at` TEXT NOT NULL,
                        `expires_at` TEXT NOT NULL,
                        PRIMARY KEY(`draft_id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_appointment_draft_owner_updated` " +
                        "ON `appointment_draft` (`owner_key`, `updated_at`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_appointment_draft_owner_expires` " +
                        "ON `appointment_draft` (`owner_key`, `expires_at`)"
                )
            }
        }

        val MIGRATION_61_62 = object : Migration(61, 62) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `activity_table` ADD COLUMN `plan_edit_at` TEXT")
                db.execSQL(
                    "ALTER TABLE `activity_table` ADD COLUMN `plan_edit_time_trusted` " +
                        "INTEGER NOT NULL DEFAULT 0"
                )
            }
        }
    }
}
