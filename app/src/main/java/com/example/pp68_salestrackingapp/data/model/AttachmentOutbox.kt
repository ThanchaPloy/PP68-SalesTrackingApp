package com.example.pp68_salestrackingapp.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Durable queue for a result photo until upload and server metadata binding both succeed. */
@Entity(
    tableName = "attachment_outbox",
    foreignKeys = [
        ForeignKey(
            entity = ActivityResult::class,
            parentColumns = ["result_id"],
            childColumns = ["result_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("result_id"),
        Index(value = ["owner_id", "state"]),
        Index(value = ["result_id", "photo_order"], unique = true)
    ]
)
data class AttachmentOutbox(
    @PrimaryKey
    @ColumnInfo(name = "operation_id")
    val operationId: String,
    @ColumnInfo(name = "owner_id")
    val ownerId: String,
    @ColumnInfo(name = "result_id")
    val resultId: String,
    @ColumnInfo(name = "photo_order")
    val photoOrder: Int,
    @ColumnInfo(name = "local_path")
    val localPath: String,
    @ColumnInfo(name = "mime_type")
    val mimeType: String,
    @ColumnInfo(name = "sha256")
    val sha256: String,
    @ColumnInfo(name = "size_bytes")
    val sizeBytes: Long,
    @ColumnInfo(name = "state")
    val state: String = STATE_PENDING_UPLOAD,
    @ColumnInfo(name = "remote_url")
    val remoteUrl: String? = null,
    @ColumnInfo(name = "attempt_count")
    val attemptCount: Int = 0,
    @ColumnInfo(name = "last_error")
    val lastError: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: String,
    @ColumnInfo(name = "updated_at")
    val updatedAt: String
) {
    companion object {
        const val STATE_PENDING_UPLOAD = "pending_upload"
        const val STATE_PENDING_BIND = "pending_bind"
    }
}

/** Immutable description returned after a selected image is copied to durable app storage. */
data class StagedAttachment(
    val operationId: String,
    val localPath: String,
    val mimeType: String,
    val sha256: String,
    val sizeBytes: Long
)

/** Ordered photo input used when a new immutable result version is saved. */
data class ResultPhotoInput(
    val photoOrder: Int,
    val remoteUrl: String? = null,
    val staged: StagedAttachment? = null
)
