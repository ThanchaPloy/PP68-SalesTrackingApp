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
    // ดึงข้อมูล ไฟล์แนบ Outbox ตาม ผลการขาย รหัส
    suspend fun getAttachmentOutboxByResultId(resultId: String): List<AttachmentOutbox>

    // ✅ เอาเฉพาะ version ล่าสุด (is_latest = 1) — ใช้ดูค่าปัจจุบันของบันทึกผล
    // ORDER BY version สำคัญ: ถ้าข้อมูลที่ดึงจาก server มีหลายแถวเป็น latest พร้อมกัน
    // (เคยเกิดได้จากทาง outbox) LIMIT 1 เฉย ๆ จะคืนแถวไหนก็ได้ รวมถึงเวอร์ชันเก่า
    @Query("SELECT * FROM activity_result WHERE appointment_id = :id AND is_latest = 1 ORDER BY version DESC LIMIT 1")
    // ดึงข้อมูล ผลการขาย ตาม กิจกรรม รหัส
    suspend fun getResultByActivityId(id: String): ActivityResult?

    // ✅ ดึง version ใดก็ได้ตาม id ตรงๆ (ใช้เปิดดูประวัติ version เก่า) ไม่กรอง is_latest
    @Query("SELECT * FROM activity_result WHERE result_id = :resultId LIMIT 1")
    // ดึงข้อมูล ผลการขาย ตาม รหัส
    suspend fun getResultById(resultId: String): ActivityResult?

    @Query("SELECT * FROM activity_result WHERE project_id = :projectId AND is_latest = 1 ORDER BY rowid DESC")
    // ดึงข้อมูล ผลการขาย ตาม โครงการ รหัส
    fun getResultsByProjectId(projectId: String): Flow<List<ActivityResult>>

    @Query("""
        SELECT ar.* FROM activity_result ar
        LEFT JOIN activity_table a ON ar.appointment_id = a.appointment_id
        WHERE (ar.project_id = :projectId
           OR a.project_id = :projectId)
           AND ar.is_latest = 1
        ORDER BY ar.rowid DESC
    """)
    // ดึงข้อมูล ทั้งหมด ผลการขาย ตาม โครงการ
    fun getAllResultsByProject(projectId: String): Flow<List<ActivityResult>>

    // ✅ ประวัติ version ทั้งหมดของบันทึกผลกลุ่มเดียวกัน เรียงใหม่สุดก่อน
    @Query("SELECT * FROM activity_result WHERE result_group_id = :resultGroupId ORDER BY version DESC, result_id ASC")
    // ดึงข้อมูล เวอร์ชัน History
    fun getVersionHistory(resultGroupId: String): Flow<List<ActivityResult>>

    @Query("""
        SELECT * FROM activity_result
        WHERE result_group_id = :resultGroupId
        ORDER BY version DESC, result_id ASC
    """)
    // ดึงข้อมูล เวอร์ชัน History Paging
    fun getVersionHistoryPaging(resultGroupId: String): PagingSource<Int, ActivityResult>

    @Query("UPDATE activity_result SET is_latest = 0 WHERE result_id = :resultId")
    // กำหนดสถานะของ Not Latest
    suspend fun markNotLatest(resultId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    // เพิ่ม ผลการขาย
    suspend fun insertResult(result: ActivityResult)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    // เพิ่ม ผลการขาย รูปภาพ
    suspend fun insertResultPhotos(photos: List<ActivityResultPhoto>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    // เพิ่ม ไฟล์แนบ Outbox
    suspend fun insertAttachmentOutbox(items: List<AttachmentOutbox>)

    /** The result and every durable attachment pointer must become visible as one local commit. */
    @Transaction
    // บันทึกผลการขายและไฟล์แนบภายใน transaction เดียวกัน
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
    // เพิ่ม ทั้งหมด Raw
    suspend fun insertAllRaw(results: List<ActivityResult>): List<Long>

    @Update
    // อัปเดต ผลการขาย
    suspend fun updateResults(results: List<ActivityResult>)

    @Query("SELECT result_id FROM activity_result WHERE is_synced = 0")
    // ดึงข้อมูล ที่ยังไม่ซิงก์ ผลการขาย รหัส
    suspend fun getUnsyncedResultIds(): List<String>

    @Transaction
    // เพิ่ม ทั้งหมด
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
    // ลบ ทั้งหมด Synced
    suspend fun deleteAllSynced()

    @Transaction
    // ล้าง And Insert
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
    // ลบ Synced ผลการขาย Not ใน
    suspend fun deleteSyncedResultsNotIn(incomingIds: List<String>)

    @Query("SELECT appointment_id FROM activity_result WHERE appointment_id IS NOT NULL AND is_latest = 1")
    // ดึงข้อมูล ทั้งหมด ผลการขาย รหัส กระแสข้อมูล
    fun getAllResultIdsFlow(): Flow<List<String>>

    // ✅ ใช้โดย export/รายงาน — คืนเฉพาะ version ล่าสุดของแต่ละบันทึกเสมอ
    /** ผลการขายของผู้ใช้คนนี้เท่านั้น ใช้กับรายงานและสถิติ */
    @Query("SELECT * FROM activity_result WHERE is_latest = 1 AND created_by = :ownerId")
    fun getResultsOwnedBy(ownerId: String): Flow<List<ActivityResult>>

    @Query("SELECT * FROM activity_result WHERE is_latest = 1")
    // ดึงข้อมูล ทั้งหมด ผลการขาย กระแสข้อมูล
    fun getAllResultsFlow(): Flow<List<ActivityResult>>

    @Query("SELECT * FROM activity_result WHERE is_synced = 0")
    // ดึงข้อมูล ที่ยังไม่ซิงก์ ผลการขาย
    suspend fun getUnsyncedResults(): List<ActivityResult>

    @Query("UPDATE activity_result SET is_synced = :isSynced WHERE result_id = :resultId")
    // อัปเดต การซิงก์ สถานะ
    suspend fun updateSyncStatus(resultId: String, isSynced: Boolean)

    @Query("DELETE FROM activity_result WHERE result_id = :resultId")
    // ลบ ผลการขาย ตาม รหัส
    suspend fun deleteResultById(resultId: String)

    @Query("UPDATE activity_result_photo SET result_id = :realId WHERE result_id = :tempId")
    // เปลี่ยนการอ้างอิงของ ผลการขาย รูปภาพ รหัส
    suspend fun remapResultPhotoIds(tempId: String, realId: String)

    @Query("UPDATE attachment_outbox SET result_id = :realId WHERE result_id = :tempId")
    // เปลี่ยนการอ้างอิงของ ไฟล์แนบ ผลการขาย รหัส
    suspend fun remapAttachmentResultIds(tempId: String, realId: String)

    /** Inserts the real result before moving photos, preventing FK cascade from deleting them. */
    @Transaction
    // แทนที่ Temporary ผลการขาย
    suspend fun replaceTemporaryResult(tempId: String, replacement: ActivityResult) {
        insertResult(replacement)
        remapResultPhotoIds(tempId, replacement.resultId)
        remapAttachmentResultIds(tempId, replacement.resultId)
        deleteResultById(tempId)
    }
}
