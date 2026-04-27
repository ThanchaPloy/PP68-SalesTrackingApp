package com.example.pp68_salestrackingapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.pp68_salestrackingapp.data.model.*
import kotlinx.coroutines.flow.Flow

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
    version = 26,
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
}
