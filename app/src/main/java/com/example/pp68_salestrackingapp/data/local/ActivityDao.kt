package com.example.pp68_salestrackingapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.pp68_salestrackingapp.data.model.SalesActivity
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityDao {
    @Query("SELECT * FROM activity_table ORDER BY activityDate DESC")
    fun getAllActivities(): Flow<List<SalesActivity>>

    @Query("SELECT * FROM activity_table WHERE activityDate >= :startDate AND activityDate <= :endDate")
    fun getActivitiesByDateRange(startDate: String, endDate: String): Flow<List<SalesActivity>>

    @Query("SELECT * FROM activity_table WHERE projectId = :projectId")
    fun getActivitiesByProject(projectId: String): Flow<List<SalesActivity>>

    @Query("SELECT * FROM activity_table WHERE customerId = :customerId")
    fun getActivitiesByCustomer(customerId: String): Flow<List<SalesActivity>>

    @Query("SELECT * FROM activity_table WHERE activityId = :id LIMIT 1")
    suspend fun getActivityById(id: String): SalesActivity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivities(activities: List<SalesActivity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivity(activity: SalesActivity)

    @Query("DELETE FROM activity_table")
    suspend fun deleteAllActivities()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(activities: List<SalesActivity>)

    @Query("DELETE FROM activity_table")
    suspend fun deleteAll()

    @Query("SELECT * FROM activity_table")
    fun getAllActivitiesFlow(): Flow<List<SalesActivity>>

    @Transaction
    suspend fun clearAndInsert(activities: List<SalesActivity>) {
        deleteAll()    // ตอนนี้มันจะรู้จัก deleteAll แล้วเพราะอยู่ข้างบน
        insertAll(activities) // ตอนนี้มันจะรู้จัก insertAll แล้ว
    }

    @Query("DELETE FROM activity_table WHERE activityId = :activityId")
    suspend fun deleteActivityById(activityId: String)

}
