package com.example.pp68_salestrackingapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertActivitiesRaw(activities: List<SalesActivity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertActivityRaw(activity: SalesActivity): Long

    @Update
    suspend fun updateActivities(activities: List<SalesActivity>)

    @Update
    suspend fun updateActivityRaw(activity: SalesActivity)

    @Query("SELECT appointment_id FROM activity_table WHERE is_synced = 0")
    suspend fun getUnsyncedActivityIds(): List<String>

    @Transaction
    suspend fun insertActivities(activities: List<SalesActivity>) {
        val insertResults = insertActivitiesRaw(activities)
        val updateList = mutableListOf<SalesActivity>()
        var unsyncedIds: Set<String>? = null
        for (i in insertResults.indices) {
            if (insertResults[i] == -1L) {
                if (unsyncedIds == null) unsyncedIds = getUnsyncedActivityIds().toSet()
                if (!unsyncedIds.contains(activities[i].activityId)) {
                    updateList.add(activities[i])
                }
            }
        }
        if (updateList.isNotEmpty()) {
            updateActivities(updateList)
        }
    }

    @Transaction
    suspend fun insertActivity(activity: SalesActivity) {
        val insertResult = insertActivityRaw(activity)
        if (insertResult == -1L) {
            updateActivityRaw(activity)
        }
    }

    @Query("DELETE FROM activity_table")
    suspend fun deleteAllActivities()

    @Transaction
    suspend fun insertAll(activities: List<SalesActivity>) {
        insertActivities(activities)
    }

    @Query("DELETE FROM activity_table")
    suspend fun deleteAll()

    @Query("DELETE FROM activity_table WHERE is_synced = 1")
    suspend fun deleteAllSynced()

    @Transaction
    suspend fun clearAndInsert(activities: List<SalesActivity>) {
        val incomingIds = activities.map { it.activityId }
        if (incomingIds.isNotEmpty()) {
            deleteSyncedActivitiesNotIn(incomingIds)
        } else {
            deleteAllSynced()
        }
        if (activities.isNotEmpty()) insertAll(activities)
    }

    @Query("DELETE FROM activity_table WHERE is_synced = 1 AND appointment_id NOT IN (:incomingIds)")
    suspend fun deleteSyncedActivitiesNotIn(incomingIds: List<String>)

    @Query("DELETE FROM activity_table WHERE appointment_id = :activityId")
    suspend fun deleteActivityById(activityId: String)

    @Query("DELETE FROM activity_table WHERE project_id = :projectId")
    suspend fun deleteActivitiesByProjectId(projectId: String)

    @Query("DELETE FROM activity_table WHERE cust_id = :customerId")
    suspend fun deleteActivitiesByCustomerId(customerId: String)

    @Query("SELECT * FROM activity_table WHERE is_synced = 0")
    suspend fun getUnsyncedActivities(): List<SalesActivity>

    @Query("UPDATE activity_table SET is_synced = :isSynced WHERE appointment_id = :activityId")
    suspend fun updateSyncStatus(activityId: String, isSynced: Boolean)

    @Query("UPDATE activity_table SET cust_id = :newCustId WHERE cust_id = :oldCustId")
    suspend fun updateCustIdForActivities(oldCustId: String, newCustId: String)

    @Query("UPDATE activity_table SET project_id = :newProjectId WHERE project_id = :oldProjectId")
    suspend fun updateProjectIdForActivities(oldProjectId: String, newProjectId: String)
}
