package com.example.pp68_salestrackingapp.data.local

import androidx.room.*
import com.example.pp68_salestrackingapp.data.model.ActivityResult

@Dao
interface ActivityResultDao {
    @Query("SELECT * FROM activity_result WHERE appointment_id = :id LIMIT 1")
    suspend fun getResultByActivityId(id: String): ActivityResult?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResult(result: ActivityResult)
}