package com.example.pp68_salestrackingapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.pp68_salestrackingapp.data.model.ProjectContact

@Dao
interface ProjectContactDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(contacts: List<ProjectContact>)

    @Query("DELETE FROM project_contact WHERE project_id = :projectId")
    suspend fun deleteByProject(projectId: String)

    @Query("SELECT contact_id FROM project_contact WHERE project_id = :projectId")
    suspend fun getContactIdsByProject(projectId: String): List<String>
}
