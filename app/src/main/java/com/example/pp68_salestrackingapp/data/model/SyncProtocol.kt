package com.example.pp68_salestrackingapp.data.model

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

data class SyncCursorResponse(val cursor: Long)

data class SyncChangeEvent(
    val seq: Long,
    @SerializedName("entity_type") val entityType: String,
    @SerializedName("entity_id") val entityId: String,
    val operation: String,
    @SerializedName("server_revision") val serverRevision: Long,
    @SerializedName("changed_at") val changedAt: String,
    val payload: JsonObject? = null
)

data class SyncChangePage(
    val items: List<SyncChangeEvent>,
    @SerializedName("next_cursor") val nextCursor: Long,
    @SerializedName("has_more") val hasMore: Boolean
)

data class SyncSnapshotPage(
    val items: List<JsonObject>,
    @SerializedName("next_after") val nextAfter: String? = null,
    @SerializedName("has_more") val hasMore: Boolean
)
