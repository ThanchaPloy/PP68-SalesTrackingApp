package com.example.pp68_salestrackingapp.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity

/** Temporary, durable shadow snapshot. Visible business tables are swapped only after all pages arrive. */
@Entity(
    tableName = "sync_snapshot_item",
    primaryKeys = ["account_key", "entity_type", "item_key"]
)
data class SyncSnapshotItem(
    @ColumnInfo(name = "account_key") val accountKey: String,
    @ColumnInfo(name = "entity_type") val entityType: String,
    @ColumnInfo(name = "item_key") val itemKey: String,
    @ColumnInfo(name = "payload_json") val payloadJson: String
)
