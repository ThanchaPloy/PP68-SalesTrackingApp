package com.example.pp68_salestrackingapp.data.model

import androidx.room.Entity

@Entity(tableName = "activity_plan_item")
data class ActivityPlanItem(
    @androidx.room.PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val appointmentId: String,
    val masterId: Int,
    val actName: String?,
    val isDone: Boolean = false
)