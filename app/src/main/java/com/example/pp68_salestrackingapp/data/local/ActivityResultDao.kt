package com.example.pp68_salestrackingapp.data.local

import androidx.room.*
import com.example.pp68_salestrackingapp.data.model.ActivityResult
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityResultDao {
    @Query("SELECT * FROM activity_result WHERE appointment_id = :id LIMIT 1")
    suspend fun getResultByActivityId(id: String): ActivityResult?

    @Query("SELECT appointment_id FROM activity_result")
    fun getAllResultIdsFlow(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResult(result: ActivityResult)
}