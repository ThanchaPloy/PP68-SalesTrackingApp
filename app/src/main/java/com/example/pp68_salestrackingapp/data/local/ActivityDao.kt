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
    /** นัดหมายของผู้ใช้คนนี้เท่านั้น — คอลัมน์เจ้าของคือ user_id (@SerializedName คือ emp_code) */
    @Query("SELECT * FROM activity_table WHERE user_id = :ownerId ORDER BY planned_date DESC")
    fun getActivitiesOwnedBy(ownerId: String): Flow<List<@JvmSuppressWildcards SalesActivity>>

    @Query("SELECT * FROM activity_table ORDER BY planned_date DESC")
    // ดึงข้อมูล ทั้งหมด กิจกรรม
    fun getAllActivities(): Flow<List<@JvmSuppressWildcards SalesActivity>>

    @Query("SELECT * FROM activity_table WHERE planned_date >= :startDate AND planned_date <= :endDate")
    // ดึงข้อมูล กิจกรรม ตาม วันที่ Range
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
    // ดึงข้อมูล กิจกรรม Cards สำหรับ Month
    fun getActivityCardsForMonth(
        userId: String,
        startDate: String,
        endDateExclusive: String
    ): Flow<List<ActivityCardRow>>

    @Query("SELECT * FROM activity_table WHERE project_id = :projectId")
    // ดึงข้อมูล กิจกรรม ตาม โครงการ
    fun getActivitiesByProject(projectId: String): Flow<List<@JvmSuppressWildcards SalesActivity>>

    @Query("SELECT * FROM activity_table WHERE cust_id = :customerId")
    // ดึงข้อมูล กิจกรรม ตาม ลูกค้า
    fun getActivitiesByCustomer(customerId: String): Flow<List<@JvmSuppressWildcards SalesActivity>>

    @Query("SELECT * FROM activity_table WHERE appointment_id = :id LIMIT 1")
    // ดึงข้อมูล กิจกรรม ตาม รหัส
    suspend fun getActivityById(id: String): SalesActivity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    // เพิ่ม กิจกรรม Raw
    suspend fun insertActivitiesRaw(activities: List<SalesActivity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    // เพิ่ม กิจกรรม Raw
    suspend fun insertActivityRaw(activity: SalesActivity): Long

    @Update
    // อัปเดต กิจกรรม
    suspend fun updateActivities(activities: List<SalesActivity>)

    @Update
    // อัปเดต กิจกรรม Raw
    suspend fun updateActivityRaw(activity: SalesActivity)

    @Query("SELECT appointment_id FROM activity_table WHERE is_synced = 0")
    // ดึงข้อมูล ที่ยังไม่ซิงก์ กิจกรรม รหัส
    suspend fun getUnsyncedActivityIds(): List<String>

    @Transaction
    // เพิ่ม กิจกรรม
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
    // เพิ่ม กิจกรรม
    suspend fun insertActivity(activity: SalesActivity) {
        val insertResult = insertActivityRaw(activity)
        if (insertResult == -1L) {
            updateActivityRaw(activity)
        }
    }


    @Transaction
    // เพิ่ม ทั้งหมด
    suspend fun insertAll(activities: List<SalesActivity>) {
        insertActivities(activities)
    }


    @Query("DELETE FROM activity_table WHERE is_synced = 1")
    // ลบ ทั้งหมด Synced
    suspend fun deleteAllSynced()

    @Transaction
    // ล้าง And Insert
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
    // ลบ Synced กิจกรรม Not ใน
    suspend fun deleteSyncedActivitiesNotIn(incomingIds: List<String>)

    @Query("DELETE FROM activity_table WHERE appointment_id = :activityId")
    // ลบ กิจกรรม ตาม รหัส
    suspend fun deleteActivityById(activityId: String)

    @Query("DELETE FROM activity_table WHERE project_id = :projectId")
    // ลบ กิจกรรม ตาม โครงการ รหัส
    suspend fun deleteActivitiesByProjectId(projectId: String)

    @Query("DELETE FROM activity_table WHERE cust_id = :customerId")
    // ลบ กิจกรรม ตาม ลูกค้า รหัส
    suspend fun deleteActivitiesByCustomerId(customerId: String)

    @Query("SELECT * FROM activity_table WHERE is_synced = 0")
    // ดึงข้อมูล ที่ยังไม่ซิงก์ กิจกรรม
    suspend fun getUnsyncedActivities(): List<SalesActivity>

    @Query("UPDATE activity_table SET is_synced = :isSynced WHERE appointment_id = :activityId")
    // อัปเดต การซิงก์ สถานะ
    suspend fun updateSyncStatus(activityId: String, isSynced: Boolean)

    @Query("UPDATE activity_table SET location_name = :locationName WHERE appointment_id = :activityId")
    // อัปเดต ตำแหน่ง Name
    suspend fun updateLocationName(activityId: String, locationName: String)

    @Query("UPDATE activity_table SET cust_id = :newCustId WHERE cust_id = :oldCustId")
    // อัปเดต Cust รหัส สำหรับ กิจกรรม
    suspend fun updateCustIdForActivities(oldCustId: String, newCustId: String)

    @Query("UPDATE activity_table SET project_id = :newProjectId WHERE project_id = :oldProjectId")
    // อัปเดต โครงการ รหัส สำหรับ กิจกรรม
    suspend fun updateProjectIdForActivities(oldProjectId: String, newProjectId: String)

    @Query("UPDATE appointment_contact SET appointment_id = :realId WHERE appointment_id = :tempId")
    // เปลี่ยนการอ้างอิงของ นัดหมาย ผู้ติดต่อ รหัส
    suspend fun remapAppointmentContactIds(tempId: String, realId: String)

    @Query("UPDATE activity_plan_item SET appointmentId = :realId WHERE appointmentId = :tempId")
    // เปลี่ยนการอ้างอิงของ แผน รายการ นัดหมาย รหัส
    suspend fun remapPlanItemAppointmentIds(tempId: String, realId: String)

    /** Replaces a server-generated appointment ID and all local references atomically. */
    @Transaction
    // แทนที่ Temporary กิจกรรม
    suspend fun replaceTemporaryActivity(tempId: String, replacement: SalesActivity) {
        insertActivity(replacement)
        remapAppointmentContactIds(tempId, replacement.activityId)
        remapPlanItemAppointmentIds(tempId, replacement.activityId)
        deleteActivityById(tempId)
    }
}
