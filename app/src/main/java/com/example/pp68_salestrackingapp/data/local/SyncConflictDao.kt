package com.example.pp68_salestrackingapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.pp68_salestrackingapp.data.model.SyncConflict

@Dao
interface SyncConflictDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(conflict: SyncConflict)

    @Query(
        """SELECT * FROM sync_conflict
           WHERE account_key = :accountKey
           ORDER BY server_seq DESC"""
    )
    suspend fun getForAccount(accountKey: String): List<SyncConflict>

    @Query("SELECT COUNT(*) FROM sync_conflict WHERE account_key = :accountKey")
    suspend fun countForAccount(accountKey: String): Int

    @Query("SELECT COUNT(*) FROM sync_conflict")
    suspend fun countAll(): Int

    @Query(
        """DELETE FROM sync_conflict
           WHERE account_key = :accountKey AND entity_type = :entityType AND entity_id = :entityId"""
    )
    suspend fun delete(accountKey: String, entityType: String, entityId: String)
}
