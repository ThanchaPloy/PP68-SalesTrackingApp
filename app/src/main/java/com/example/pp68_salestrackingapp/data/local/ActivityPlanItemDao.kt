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
}