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
    @Query("SELECT * FROM activity_table ORDER BY planned_date DESC")
    fun getAllActivities(): Flow<List<@JvmSuppressWildcards SalesActivity>>

    @Query("SELECT * FROM activity_table WHERE planned_date >= :startDate AND planned_date <= :endDate")
    fun getActivitiesByDateRange(startDate: String, endDate: String): Flow<List<@JvmSuppressWildcards SalesActivity>>

    @Query("SELECT * FROM activity_table WHERE project_id = :projectId")
    fun getActivitiesByProject(projectId: String): Flow<List<@JvmSuppressWildcards SalesActivity>>

    @Query("SELECT * FROM activity_table WHERE cust_id = :customerId")
    fun getActivitiesByCustomer(customerId: String): Flow<List<@JvmSuppressWildcards SalesActivity>>

    @Query("SELECT * FROM activity_table WHERE appointment_id = :id LIMIT 1")
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

    @Transaction
    suspend fun clearAndInsert(activities: List<SalesActivity>) {
        deleteAll()
        insertAll(activities)
    }

    @Query("DELETE FROM activity_table WHERE appointment_id = :activityId")
    suspend fun deleteActivityById(activityId: String)

    @Query("DELETE FROM activity_table WHERE project_id = :projectId")
    suspend fun deleteActivitiesByProjectId(projectId: String)

    @Query("DELETE FROM activity_table WHERE cust_id = :customerId")
    suspend fun deleteActivitiesByCustomerId(customerId: String)
}
