package com.example.pp68_salestrackingapp.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "sync_state",
    primaryKeys = ["account_key", "stream"]
)
data class SyncState(
    @ColumnInfo(name = "account_key") val accountKey: String,
    val stream: String = MAIN_STREAM,
    val cursor: Long = 0L,
    @ColumnInfo(name = "snapshot_cursor") val snapshotCursor: Long? = null,
    @ColumnInfo(name = "bootstrap_status") val bootstrapStatus: String = STATUS_NOT_STARTED,
    @ColumnInfo(name = "updated_at_epoch_ms") val updatedAtEpochMs: Long = System.currentTimeMillis()
) {
    companion object {
        const val MAIN_STREAM = "main"
        const val STATUS_NOT_STARTED = "NOT_STARTED"
        const val STATUS_SNAPSHOT_IN_PROGRESS = "SNAPSHOT_IN_PROGRESS"
        const val STATUS_READY = "READY"
    }
}
