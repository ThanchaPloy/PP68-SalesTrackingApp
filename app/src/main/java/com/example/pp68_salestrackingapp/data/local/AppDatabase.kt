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
        AppointmentContact::class
    ],
    version = 30,          // ✅ เพิ่มจาก 29 → 30
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
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE project ADD COLUMN updatedAt TEXT")
            }
        }
    }
}
