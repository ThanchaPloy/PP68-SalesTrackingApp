package com.example.pp68_salestrackingapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.pp68_salestrackingapp.data.model.SyncRejection

@Dao
interface SyncRejectionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rejection: SyncRejection)

    @Query("DELETE FROM sync_rejection WHERE entity_type = :entityType AND entity_id = :entityId")
    suspend fun clear(entityType: String, entityId: String)

    @Query("SELECT entity_id FROM sync_rejection WHERE entity_type = :entityType")
    suspend fun idsOfType(entityType: String): List<String>

    @Query("SELECT * FROM sync_rejection ORDER BY rejected_at DESC")
    suspend fun getAll(): List<SyncRejection>

    @Query("SELECT COUNT(*) FROM sync_rejection")
    suspend fun count(): Int
}
