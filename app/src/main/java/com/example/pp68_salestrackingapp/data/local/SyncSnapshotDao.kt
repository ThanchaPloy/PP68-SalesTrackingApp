package com.example.pp68_salestrackingapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.pp68_salestrackingapp.data.model.SyncSnapshotItem

@Dao
interface SyncSnapshotDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<SyncSnapshotItem>)

    @Query(
        """SELECT * FROM sync_snapshot_item
           WHERE account_key = :accountKey AND entity_type = :entityType
           ORDER BY item_key ASC"""
    )
    suspend fun getItems(accountKey: String, entityType: String): List<SyncSnapshotItem>

    @Query("DELETE FROM sync_snapshot_item WHERE account_key = :accountKey")
    suspend fun deleteForAccount(accountKey: String)
}
