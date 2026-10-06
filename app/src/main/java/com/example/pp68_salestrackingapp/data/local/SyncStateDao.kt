package com.example.pp68_salestrackingapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.pp68_salestrackingapp.data.model.SyncState

@Dao
interface SyncStateDao {
    @Query("SELECT * FROM sync_state WHERE account_key = :accountKey AND stream = :stream LIMIT 1")
    suspend fun get(accountKey: String, stream: String = SyncState.MAIN_STREAM): SyncState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: SyncState)

    @Query("DELETE FROM sync_state WHERE account_key = :accountKey")
    suspend fun deleteForAccount(accountKey: String)
}
