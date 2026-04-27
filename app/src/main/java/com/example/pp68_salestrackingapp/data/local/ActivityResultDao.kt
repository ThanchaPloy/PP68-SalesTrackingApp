package com.example.pp68_salestrackingapp.data.local

import androidx.room.*
import com.example.pp68_salestrackingapp.data.model.ActivityResult
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityResultDao {
    // เดิม — query ด้วย appointment_id
    @Query("SELECT * FROM activity_result WHERE appointment_id = :id LIMIT 1")
    suspend fun getResultByActivityId(id: String): ActivityResult?

    // ✅ เพิ่มใหม่ — query ด้วย project_id
    @Query("SELECT * FROM activity_result WHERE project_id = :projectId ORDER BY rowid DESC")
    fun getResultsByProjectId(projectId: String): Flow<List<ActivityResult>>

    // ✅ เพิ่มใหม่ — query ทุก result ของ project (รวมที่ผูกผ่าน appointment)
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

    @Query("SELECT appointment_id FROM activity_result WHERE appointment_id IS NOT NULL")
    fun getAllResultIdsFlow(): Flow<List<String>>

    @Query("SELECT * FROM activity_result")
    fun getAllResultsFlow(): Flow<List<ActivityResult>>
}