package com.example.pp68_salestrackingapp.data.local

import androidx.room.*
import com.example.pp68_salestrackingapp.data.model.ActivityResult
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityResultDao {
    @Query("SELECT * FROM activity_result WHERE appointment_id = :id LIMIT 1")
    suspend fun getResultByActivityId(id: String): ActivityResult?

    @Query("SELECT * FROM activity_result WHERE result_id = :resultId LIMIT 1")
    suspend fun getResultById(resultId: String): ActivityResult?

    @Query("SELECT * FROM activity_result WHERE project_id = :projectId ORDER BY rowid DESC")
    fun getResultsByProjectId(projectId: String): Flow<List<ActivityResult>>

    @Query("""
        SELECT ar.* FROM activity_result ar
        LEFT JOIN activity_table a ON ar.appointment_id = a.appointment_id
        WHERE ar.project_id = :projectId 
           OR a.project_id = :projectId
        ORDER BY ar.rowid DESC
    """)
    fun getAllResultsByProject(projectId: String): Flow<List<ActivityResult>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResult(result: ActivityResult)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(results: List<ActivityResult>)

    @Query("DELETE FROM activity_result")
    suspend fun deleteAll()

    @Transaction
    suspend fun clearAndInsert(results: List<ActivityResult>) {
        deleteAll()
        insertAll(results)
    }

    @Query("SELECT appointment_id FROM activity_result WHERE appointment_id IS NOT NULL")
    fun getAllResultIdsFlow(): Flow<List<String>>

    @Query("SELECT * FROM activity_result")
    fun getAllResultsFlow(): Flow<List<ActivityResult>>

    @Query("SELECT * FROM activity_result WHERE is_synced = 0")
    suspend fun getUnsyncedResults(): List<ActivityResult>

    @Query("UPDATE activity_result SET is_synced = :isSynced WHERE result_id = :resultId")
    suspend fun updateSyncStatus(resultId: String, isSynced: Boolean)

    @Query("DELETE FROM activity_result WHERE result_id = :resultId")
    suspend fun deleteResultById(resultId: String)
}
