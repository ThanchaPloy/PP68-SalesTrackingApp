package com.example.pp68_salestrackingapp.data.local

import androidx.room.*
import androidx.paging.PagingSource
import com.example.pp68_salestrackingapp.data.model.ActivityResult
import com.example.pp68_salestrackingapp.data.model.ActivityResultPhoto
import com.example.pp68_salestrackingapp.data.model.AttachmentOutbox
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityResultDao {
    @Query("SELECT * FROM attachment_outbox WHERE result_id = :resultId ORDER BY photo_order")
    suspend fun getAttachmentOutboxByResultId(resultId: String): List<AttachmentOutbox>

    // ✅ เอาเฉพาะ version ล่าสุด (is_latest = 1) — ใช้ดูค่าปัจจุบันของบันทึกผล
    // ORDER BY version สำคัญ: ถ้าข้อมูลที่ดึงจาก server มีหลายแถวเป็น latest พร้อมกัน
    // (เคยเกิดได้จากทาง outbox) LIMIT 1 เฉย ๆ จะคืนแถวไหนก็ได้ รวมถึงเวอร์ชันเก่า
    @Query("SELECT * FROM activity_result WHERE appointment_id = :id AND is_latest = 1 ORDER BY version DESC LIMIT 1")
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
    @Query("SELECT * FROM activity_result WHERE result_group_id = :resultGroupId ORDER BY version DESC, result_id ASC")
    fun getVersionHistory(resultGroupId: String): Flow<List<ActivityResult>>

    @Query("""
        SELECT * FROM activity_result
        WHERE result_group_id = :resultGroupId
        ORDER BY version DESC, result_id ASC
    """)
    fun getVersionHistoryPaging(resultGroupId: String): PagingSource<Int, ActivityResult>

    @Query("UPDATE activity_result SET is_latest = 0 WHERE result_id = :resultId")
    suspend fun markNotLatest(resultId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResult(result: ActivityResult)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResultPhotos(photos: List<ActivityResultPhoto>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAttachmentOutbox(items: List<AttachmentOutbox>)

    /** The result and every durable attachment pointer must become visible as one local commit. */
    @Transaction
    suspend fun insertResultWithAttachments(
        previousResultId: String?,
        result: ActivityResult,
        remotePhotos: List<ActivityResultPhoto>,
        attachments: List<AttachmentOutbox>
    ) {
        previousResultId?.let { markNotLatest(it) }
        insertResult(result)
        if (remotePhotos.isNotEmpty()) insertResultPhotos(remotePhotos)
        if (attachments.isNotEmpty()) insertAttachmentOutbox(attachments)
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllRaw(results: List<ActivityResult>): List<Long>

    @Update
    suspend fun updateResults(results: List<ActivityResult>)

    @Query("SELECT result_id FROM activity_result WHERE is_synced = 0")
    suspend fun getUnsyncedResultIds(): List<String>

    @Transaction
    suspend fun insertAll(results: List<ActivityResult>) {
        val insertResults = insertAllRaw(results)
        val updateList = mutableListOf<ActivityResult>()
        var unsyncedIds: Set<String>? = null
        for (i in insertResults.indices) {
            if (insertResults[i] == -1L) {
                if (unsyncedIds == null) unsyncedIds = getUnsyncedResultIds().toSet()
                if (!unsyncedIds.contains(results[i].resultId)) {
                    updateList.add(results[i])
                }
            }
        }
        if (updateList.isNotEmpty()) {
            updateResults(updateList)
        }
    }


    @Query("DELETE FROM activity_result WHERE is_synced = 1")
    suspend fun deleteAllSynced()

    @Transaction
    suspend fun clearAndInsert(results: List<ActivityResult>) {
        val incomingIds = results.map { it.resultId }
        if (incomingIds.isNotEmpty()) {
            deleteSyncedResultsNotIn(incomingIds)
        } else {
            deleteAllSynced()
        }
        if (results.isNotEmpty()) insertAll(results)
    }

    @Query("DELETE FROM activity_result WHERE is_synced = 1 AND result_id NOT IN (:incomingIds)")
    suspend fun deleteSyncedResultsNotIn(incomingIds: List<String>)

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

    @Query("UPDATE activity_result_photo SET result_id = :realId WHERE result_id = :tempId")
    suspend fun remapResultPhotoIds(tempId: String, realId: String)

    @Query("UPDATE attachment_outbox SET result_id = :realId WHERE result_id = :tempId")
    suspend fun remapAttachmentResultIds(tempId: String, realId: String)

    /** Inserts the real result before moving photos, preventing FK cascade from deleting them. */
    @Transaction
    suspend fun replaceTemporaryResult(tempId: String, replacement: ActivityResult) {
        insertResult(replacement)
        remapResultPhotoIds(tempId, replacement.resultId)
        remapAttachmentResultIds(tempId, replacement.resultId)
        deleteResultById(tempId)
    }
}
