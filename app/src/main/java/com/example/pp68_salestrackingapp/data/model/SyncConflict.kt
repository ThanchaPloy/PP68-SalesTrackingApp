package com.example.pp68_salestrackingapp.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * A server change that was intentionally not applied because the same local row still has an
 * unsent edit. The server payload is retained so a later conflict-resolution flow does not need
 * to guess which revision collided with the local work.
 */
@Entity(
    tableName = "sync_conflict",
    primaryKeys = ["account_key", "entity_type", "entity_id"]
)
data class SyncConflict(
    @ColumnInfo(name = "account_key") val accountKey: String,
    @ColumnInfo(name = "entity_type") val entityType: String,
    @ColumnInfo(name = "entity_id") val entityId: String,
    val operation: String,
    @ColumnInfo(name = "server_revision") val serverRevision: Long,
    @ColumnInfo(name = "server_seq") val serverSeq: Long,
    @ColumnInfo(name = "server_payload_json") val serverPayloadJson: String? = null,
    @ColumnInfo(name = "detected_at_epoch_ms") val detectedAtEpochMs: Long = System.currentTimeMillis()
)
