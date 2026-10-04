package com.example.pp68_salestrackingapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.pp68_salestrackingapp.data.model.ActivityResultPhoto
import com.example.pp68_salestrackingapp.data.model.AttachmentOutbox

@Dao
interface AttachmentOutboxDao {
    @Query("SELECT * FROM attachment_outbox WHERE owner_id = :ownerId ORDER BY created_at, photo_order LIMIT :limit")
    suspend fun getPending(ownerId: String, limit: Int): List<AttachmentOutbox>

    @Query("SELECT * FROM attachment_outbox WHERE result_id = :resultId ORDER BY photo_order")
    suspend fun getByResultId(resultId: String): List<AttachmentOutbox>

    @Query("SELECT COUNT(*) FROM attachment_outbox WHERE owner_id = :ownerId")
    suspend fun countPending(ownerId: String): Int

    @Query("SELECT local_path FROM attachment_outbox")
    suspend fun getAllReferencedPaths(): List<String>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(items: List<AttachmentOutbox>)

    @Query("UPDATE attachment_outbox SET state = :state, remote_url = :remoteUrl, attempt_count = attempt_count + 1, last_error = NULL, updated_at = :updatedAt WHERE operation_id = :operationId")
    suspend fun markUploaded(operationId: String, state: String, remoteUrl: String, updatedAt: String)

    @Query("UPDATE attachment_outbox SET attempt_count = attempt_count + 1, last_error = :error, updated_at = :updatedAt WHERE operation_id = :operationId")
    suspend fun markAttemptFailed(operationId: String, error: String, updatedAt: String)

    @Query("DELETE FROM attachment_outbox WHERE operation_id = :operationId")
    suspend fun delete(operationId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBoundPhoto(photo: ActivityResultPhoto)

    @Transaction
    suspend fun finalizeBinding(item: AttachmentOutbox, remoteUrl: String) {
        insertBoundPhoto(ActivityResultPhoto(item.resultId, item.photoOrder, remoteUrl))
        delete(item.operationId)
    }
}
