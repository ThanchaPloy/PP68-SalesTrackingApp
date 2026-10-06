package com.example.pp68_salestrackingapp.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * Durable translation from a locally generated ID to the ID assigned by the server.
 *
 * The mapping closes the race where a screen still holds a TEMP project ID while the sync worker
 * has already replaced that project with its server ID. It intentionally contains IDs only and no
 * business or customer data.
 */
@Entity(
    tableName = "local_id_mapping",
    primaryKeys = ["entity_type", "temp_id"],
    indices = [Index(value = ["entity_type", "real_id"])]
)
data class LocalIdMapping(
    @ColumnInfo(name = "entity_type") val entityType: String,
    @ColumnInfo(name = "temp_id") val tempId: String,
    @ColumnInfo(name = "real_id") val realId: String,
    @ColumnInfo(name = "created_at_epoch_ms") val createdAtEpochMs: Long = System.currentTimeMillis()
) {
    companion object {
        const val ENTITY_CUSTOMER = "customer"
        const val ENTITY_CONTACT = "contact"
        const val ENTITY_PROJECT = "project"
        const val ENTITY_ACTIVITY = "activity"
        const val ENTITY_RESULT = "result"
    }
}
