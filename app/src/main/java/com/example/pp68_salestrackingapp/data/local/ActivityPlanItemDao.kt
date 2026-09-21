package com.example.pp68_salestrackingapp.data.local

import androidx.room.*
import com.example.pp68_salestrackingapp.data.model.ActivityPlanItem

@Dao
interface ActivityPlanItemDao {

    @Query("SELECT * FROM activity_plan_item WHERE appointmentId = :appointmentId")
    suspend fun getPlanItemsByAppointmentId(appointmentId: String): List<ActivityPlanItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlanItems(items: List<ActivityPlanItem>)

    @Query("DELETE FROM activity_plan_item WHERE appointmentId = :appointmentId")
    suspend fun deletePlanItemsByAppointmentId(appointmentId: String)

    @Query("UPDATE activity_plan_item SET isDone = :isDone WHERE appointmentId = :appointmentId AND masterId = :masterId")
    suspend fun updateItemStatus(appointmentId: String, masterId: Int, isDone: Boolean)

    @Query("UPDATE activity_plan_item SET appointmentId = :newId WHERE appointmentId = :oldId")
    suspend fun updateAppointmentId(oldId: String, newId: String)

    @Query("SELECT DISTINCT appointmentId FROM activity_plan_item WHERE is_synced = 0")
    suspend fun getUnsyncedAppointmentIds(): List<String>

    @Query("UPDATE activity_plan_item SET is_synced = :isSynced WHERE appointmentId = :appointmentId")
    suspend fun updateSyncStatusByAppointment(appointmentId: String, isSynced: Boolean)

    @Query("UPDATE activity_plan_item SET is_synced = 0 WHERE appointmentId = :appointmentId AND masterId = :masterId")
    suspend fun markItemUnsynced(appointmentId: String, masterId: Int)
}