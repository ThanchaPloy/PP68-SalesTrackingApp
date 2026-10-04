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

    /**
     * Home is a month view, so a bounded joined query is more appropriate than paging grouped
     * sections. endDateExclusive keeps timestamp values indexable and includes the whole last day.
     */
    @Query(
        """
        SELECT
            a.appointment_id AS activityId,
            a.type AS activityType,
            COALESCE(p.projectName, a.project_name) AS projectName,
            COALESCE(c.company_name, a.company_name) AS companyName,
            a.contact_name AS contactName,
            a.topic AS objective,
            a.plan_status AS planStatus,
            a.planned_date AS plannedDate,
            a.planned_time AS plannedTime,
            a.planned_end_time AS plannedEndTime,
            COALESCE(a.weekly_note, a.note) AS weeklyNote,
            a.cust_id AS customerId,
            CASE WHEN EXISTS (
                SELECT 1 FROM activity_result ar
                WHERE ar.appointment_id = a.appointment_id AND ar.is_latest = 1
            ) THEN 1 ELSE 0 END AS hasResult,
            a.check_in_time AS checkInTime,
            a.is_location_verified AS isLocationVerified,
            a.planned_lat AS plannedLat,
            a.planned_long AS plannedLong,
            a.location_name AS locationName
        FROM activity_table a
        LEFT JOIN project p ON p.projectId = a.project_id
        LEFT JOIN customer c ON c.cust_id = a.cust_id
        WHERE a.user_id = :userId
          AND (
              (a.planned_date >= :startDate AND a.planned_date < :endDateExclusive)
              OR a.planned_date NOT GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]*'
          )
        ORDER BY a.planned_date ASC, a.planned_time ASC, a.appointment_id ASC
        """
    )
    fun getActivityCardsForMonth(
        userId: String,
        startDate: String,
        endDateExclusive: String
    ): Flow<List<ActivityCardRow>>

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


    @Transaction
    suspend fun insertAll(activities: List<SalesActivity>) {
        insertActivities(activities)
    }


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

    @Query("UPDATE activity_table SET location_name = :locationName WHERE appointment_id = :activityId")
    suspend fun updateLocationName(activityId: String, locationName: String)

    @Query("UPDATE activity_table SET cust_id = :newCustId WHERE cust_id = :oldCustId")
    suspend fun updateCustIdForActivities(oldCustId: String, newCustId: String)

    @Query("UPDATE activity_table SET project_id = :newProjectId WHERE project_id = :oldProjectId")
    suspend fun updateProjectIdForActivities(oldProjectId: String, newProjectId: String)

    @Query("UPDATE appointment_contact SET appointment_id = :realId WHERE appointment_id = :tempId")
    suspend fun remapAppointmentContactIds(tempId: String, realId: String)

    @Query("UPDATE activity_plan_item SET appointmentId = :realId WHERE appointmentId = :tempId")
    suspend fun remapPlanItemAppointmentIds(tempId: String, realId: String)

    /** Replaces a server-generated appointment ID and all local references atomically. */
    @Transaction
    suspend fun replaceTemporaryActivity(tempId: String, replacement: SalesActivity) {
        insertActivity(replacement)
        remapAppointmentContactIds(tempId, replacement.activityId)
        remapPlanItemAppointmentIds(tempId, replacement.activityId)
        deleteActivityById(tempId)
    }
}
