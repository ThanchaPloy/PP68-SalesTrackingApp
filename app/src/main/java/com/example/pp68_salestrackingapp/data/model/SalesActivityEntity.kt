package com.example.pp68_salestrackingapp.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "activities_table")
data class SalesActivityEntity(
    @PrimaryKey
    val activityId: String,
    val activityType: String,
    val projectName: String?,
    val companyName: String?,
    val contactName: String?,
    val objective: String?,
    val planStatus: String,
    val plannedDate: String,
    val plannedTime: String?,
    val plannedEndTime: String?,
    val weeklyNote: String?,
    val customerId: String?
)