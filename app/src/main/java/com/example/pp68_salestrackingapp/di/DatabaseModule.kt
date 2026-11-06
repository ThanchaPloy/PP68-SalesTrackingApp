package com.example.pp68_salestrackingapp.di

import android.content.Context
import androidx.room.Room
import com.example.pp68_salestrackingapp.data.local.ActivityDao
import com.example.pp68_salestrackingapp.data.local.ActivityPlanItemDao
import com.example.pp68_salestrackingapp.data.local.ActivityResultDao
import com.example.pp68_salestrackingapp.data.local.ActivityResultPhotoDao
import com.example.pp68_salestrackingapp.data.local.AttachmentOutboxDao
import com.example.pp68_salestrackingapp.data.local.AppDatabase
import com.example.pp68_salestrackingapp.data.local.AppointmentDraftDao
import com.example.pp68_salestrackingapp.data.local.AppointmentContactDao
import com.example.pp68_salestrackingapp.data.local.ProjectContactDao
import com.example.pp68_salestrackingapp.data.local.BranchDao
import com.example.pp68_salestrackingapp.data.local.CustomerDao
import com.example.pp68_salestrackingapp.data.local.ProjectDao
import com.example.pp68_salestrackingapp.data.local.ContactDao
import com.example.pp68_salestrackingapp.data.local.SyncRejectionDao
import com.example.pp68_salestrackingapp.data.local.SyncStateDao
import com.example.pp68_salestrackingapp.data.local.SyncConflictDao
import com.example.pp68_salestrackingapp.data.local.SyncSnapshotDao
import com.example.pp68_salestrackingapp.data.local.LocalIdMappingDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    // สร้างและส่งมอบ App ฐานข้อมูล
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "sales_tracking_db"
        )
            // ✅ เดิมเป็น fallbackToDestructiveMigration() เปล่า ๆ = ถ้าใครขยับ version โดยไม่เขียน
            // Migration คู่กัน (CLAUDE.md เตือนไว้ว่าเคยเกิด) Room จะ "ล้าง DB ทั้งก้อนแบบเงียบ ๆ"
            // รวมงานที่ยังไม่ได้ซิงค์ขึ้น server ด้วย ผู้ใช้ไม่รู้ตัวและกู้ไม่ได้เลย
            // จำกัดให้ล้างได้เฉพาะ DB เก่ากว่าต้นสาย migration (28) ซึ่งไม่มีทางกู้อยู่แล้ว —
            // ส่วนกรณีลืมเขียน migration จะกลายเป็น crash ตอนเปิดแอป เจอในเทสต์ทันที ไม่ใช่ข้อมูลหาย
            .fallbackToDestructiveMigrationFrom(*IntArray(27) { it + 1 })
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .build()
    }

    @Provides
    @Singleton
    // สร้างและส่งมอบ นัดหมาย ฉบับร่าง Dao
    fun provideAppointmentDraftDao(database: AppDatabase): AppointmentDraftDao {
        return database.appointmentDraftDao()
    }

    @Provides
    @Singleton
    // สร้างและส่งมอบ Call Log Dao
    fun provideCallLogDao(database: AppDatabase): com.example.pp68_salestrackingapp.data.local.CallLogDao {
        return database.callLogDao()
    }

    @Provides
    @Singleton
    // สร้างและส่งมอบ ลูกค้า Dao
    fun provideCustomerDao(database: AppDatabase): CustomerDao {
        return database.customerDao()
    }

    @Provides
    @Singleton
    // สร้างและส่งมอบ โครงการ Dao
    fun provideProjectDao(database: AppDatabase): ProjectDao {
        return database.projectDao()
    }

    @Provides
    @Singleton
    // สร้างและส่งมอบ กิจกรรม Dao
    fun provideActivityDao(database: AppDatabase): ActivityDao {
        return database.activityDao()
    }

    @Provides
    @Singleton
    // สร้างและส่งมอบ ผู้ติดต่อ Dao
    fun provideContactDao(database: AppDatabase): ContactDao {
        return database.contactDao()
    }

    @Provides
    @Singleton
    // สร้างและส่งมอบ สาขา Dao
    fun provideBranchDao(database: AppDatabase): BranchDao {
        return database.branchDao()
    }

    @Provides
    @Singleton
    // สร้างและส่งมอบ กิจกรรม แผน รายการ Dao
    fun provideActivityPlanItemDao(database: AppDatabase): ActivityPlanItemDao {
        return database.activityPlanItemDao()
    }

    @Provides
    @Singleton
    // สร้างและส่งมอบ กิจกรรม ผลการขาย Dao
    fun provideActivityResultDao(db: AppDatabase): ActivityResultDao = db.activityResultDao()

    @Provides
    @Singleton
    // สร้างและส่งมอบ นัดหมาย ผู้ติดต่อ Dao
    fun provideAppointmentContactDao(db: AppDatabase): AppointmentContactDao = db.appointmentContactDao()

    @Provides
    @Singleton
    // สร้างและส่งมอบ โครงการ ผู้ติดต่อ Dao
    fun provideProjectContactDao(db: AppDatabase): ProjectContactDao = db.projectContactDao()

    @Provides
    @Singleton
    // สร้างและส่งมอบ กิจกรรม ผลการขาย รูปภาพ Dao
    fun provideActivityResultPhotoDao(db: AppDatabase): ActivityResultPhotoDao = db.activityResultPhotoDao()

    @Provides
    @Singleton
    // สร้างและส่งมอบ การซิงก์ รายการถูกปฏิเสธ Dao
    fun provideSyncRejectionDao(db: AppDatabase): SyncRejectionDao = db.syncRejectionDao()

    @Provides
    @Singleton
    // สร้างและส่งมอบ ไฟล์แนบ Outbox Dao
    fun provideAttachmentOutboxDao(db: AppDatabase): AttachmentOutboxDao = db.attachmentOutboxDao()

    @Provides
    @Singleton
    // สร้างและส่งมอบ การซิงก์ State Dao
    fun provideSyncStateDao(db: AppDatabase): SyncStateDao = db.syncStateDao()

    @Provides
    @Singleton
    // สร้างและส่งมอบ การซิงก์ ข้อมูลขัดแย้ง Dao
    fun provideSyncConflictDao(db: AppDatabase): SyncConflictDao = db.syncConflictDao()

    @Provides
    @Singleton
    // สร้างและส่งมอบ การซิงก์ สแนปช็อต Dao
    fun provideSyncSnapshotDao(db: AppDatabase): SyncSnapshotDao = db.syncSnapshotDao()

    @Provides
    @Singleton
    // สร้างและส่งมอบ Local รหัส การจับคู่รหัส Dao
    fun provideLocalIdMappingDao(db: AppDatabase): LocalIdMappingDao = db.localIdMappingDao()
}
