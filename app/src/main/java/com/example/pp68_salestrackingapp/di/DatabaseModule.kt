package com.example.pp68_salestrackingapp.di

import android.content.Context
import androidx.room.Room
import com.example.pp68_salestrackingapp.data.local.ActivityDao
import com.example.pp68_salestrackingapp.data.local.ActivityPlanItemDao
import com.example.pp68_salestrackingapp.data.local.ActivityResultDao
import com.example.pp68_salestrackingapp.data.local.ActivityResultPhotoDao
import com.example.pp68_salestrackingapp.data.local.AppDatabase
import com.example.pp68_salestrackingapp.data.local.AppointmentContactDao
import com.example.pp68_salestrackingapp.data.local.ProjectContactDao
import com.example.pp68_salestrackingapp.data.local.BranchDao
import com.example.pp68_salestrackingapp.data.local.CustomerDao
import com.example.pp68_salestrackingapp.data.local.ProjectDao
import com.example.pp68_salestrackingapp.data.local.ContactDao
import com.example.pp68_salestrackingapp.data.local.SyncRejectionDao
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
            .addMigrations(
                AppDatabase.MIGRATION_28_29,
                AppDatabase.MIGRATION_29_30,
                AppDatabase.MIGRATION_30_31,
                AppDatabase.MIGRATION_31_32,
                AppDatabase.MIGRATION_32_33,
                AppDatabase.MIGRATION_33_34,
                AppDatabase.MIGRATION_34_35,
                AppDatabase.MIGRATION_35_36,
                AppDatabase.MIGRATION_36_37,
                AppDatabase.MIGRATION_37_38,
                AppDatabase.MIGRATION_38_39,
                AppDatabase.MIGRATION_39_40,
                AppDatabase.MIGRATION_40_41,
                AppDatabase.MIGRATION_41_42,
                AppDatabase.MIGRATION_42_43,
                AppDatabase.MIGRATION_43_44,
                AppDatabase.MIGRATION_44_45,
                AppDatabase.MIGRATION_45_46,
                AppDatabase.MIGRATION_46_47,
                AppDatabase.MIGRATION_47_48,
                AppDatabase.MIGRATION_48_49,
                AppDatabase.MIGRATION_49_50,
                AppDatabase.MIGRATION_50_51,
                AppDatabase.MIGRATION_51_52,
                AppDatabase.MIGRATION_52_53
            )
            .build()
    }

    @Provides
    @Singleton
    fun provideCustomerDao(database: AppDatabase): CustomerDao {
        return database.customerDao()
    }

    @Provides
    @Singleton
    fun provideProjectDao(database: AppDatabase): ProjectDao {
        return database.projectDao()
    }

    @Provides
    @Singleton
    fun provideActivityDao(database: AppDatabase): ActivityDao {
        return database.activityDao()
    }

    @Provides
    @Singleton
    fun provideContactDao(database: AppDatabase): ContactDao {
        return database.contactDao()
    }

    @Provides
    @Singleton
    fun provideBranchDao(database: AppDatabase): BranchDao {
        return database.branchDao()
    }

    @Provides
    @Singleton
    fun provideActivityPlanItemDao(database: AppDatabase): ActivityPlanItemDao {
        return database.activityPlanItemDao()
    }

    @Provides
    @Singleton
    fun provideActivityResultDao(db: AppDatabase): ActivityResultDao = db.activityResultDao()

    @Provides
    @Singleton
    fun provideAppointmentContactDao(db: AppDatabase): AppointmentContactDao = db.appointmentContactDao()

    @Provides
    @Singleton
    fun provideProjectContactDao(db: AppDatabase): ProjectContactDao = db.projectContactDao()

    @Provides
    @Singleton
    fun provideActivityResultPhotoDao(db: AppDatabase): ActivityResultPhotoDao = db.activityResultPhotoDao()

    @Provides
    @Singleton
    fun provideSyncRejectionDao(db: AppDatabase): SyncRejectionDao = db.syncRejectionDao()
}
