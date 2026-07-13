package com.example.pp68_salestrackingapp.data.local

import androidx.room.*
import com.example.pp68_salestrackingapp.data.model.ActivityResult
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityResultDao {
    // ✅ เอาเฉพาะ version ล่าสุด (is_latest = 1) — ใช้ดูค่าปัจจุบันของบันทึกผล
    @Query("SELECT * FROM activity_result WHERE appointment_id = :id AND is_latest = 1 LIMIT 1")
    suspend fun getResultByActivityId(id: String): ActivityResult?

    // ✅ ดึง version ใดก็ได้ตาม id ตรงๆ (ใช้เปิดดูประวัติ version เก่า) ไม่กรอง is_latest
    @Query("SELECT * FROM activity_result WHERE result_id = :resultId LIMIT 1")
    suspend fun getResultById(resultId: String): ActivityResult?

    @Query("SELECT * FROM activity_result WHERE project_id = :projectId AND is_latest = 1 ORDER BY rowid DESC")
    fun getResultsByProjectId(projectId: String): Flow<List<ActivityResult>>

    @Query("""
        SELECT ar.* FROM activity_result ar
        LEFT JOIN activity_table a ON ar.appointment_id = a.appointment_id
        WHERE (ar.project_id = :projectId
           OR a.project_id = :projectId)
           AND ar.is_latest = 1
        ORDER BY ar.rowid DESC
    """)
    fun getAllResultsByProject(projectId: String): Flow<List<ActivityResult>>

    // ✅ ประวัติ version ทั้งหมดของบันทึกผลกลุ่มเดียวกัน เรียงใหม่สุดก่อน
    @Query("SELECT * FROM activity_result WHERE result_group_id = :resultGroupId ORDER BY version DESC")
    fun getVersionHistory(resultGroupId: String): Flow<List<ActivityResult>>

    @Query("UPDATE activity_result SET is_latest = 0 WHERE result_id = :resultId")
    suspend fun markNotLatest(resultId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResult(result: ActivityResult)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(results: List<ActivityResult>)

    @Query("DELETE FROM activity_result")
    suspend fun deleteAll()

    @Query("DELETE FROM activity_result WHERE is_synced = 1")
    suspend fun deleteAllSynced()

    @Transaction
    suspend fun clearAndInsert(results: List<ActivityResult>) {
        deleteAllSynced()   // คง row is_synced=0 (ยังไม่ได้ส่งขึ้น server) ไว้
        if (results.isNotEmpty()) insertAll(results)
    }

    @Query("SELECT appointment_id FROM activity_result WHERE appointment_id IS NOT NULL AND is_latest = 1")
    fun getAllResultIdsFlow(): Flow<List<String>>

    // ✅ ใช้โดย export/รายงาน — คืนเฉพาะ version ล่าสุดของแต่ละบันทึกเสมอ
    @Query("SELECT * FROM activity_result WHERE is_latest = 1")
    fun getAllResultsFlow(): Flow<List<ActivityResult>>

    @Query("SELECT * FROM activity_result WHERE is_synced = 0")
    suspend fun getUnsyncedResults(): List<ActivityResult>

    @Query("UPDATE activity_result SET is_synced = :isSynced WHERE result_id = :resultId")
    suspend fun updateSyncStatus(resultId: String, isSynced: Boolean)

    @Query("DELETE FROM activity_result WHERE result_id = :resultId")
    suspend fun deleteResultById(resultId: String)
}
