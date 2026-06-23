package com.example.pp68_salestrackingapp.data.local

import androidx.room.*
import com.example.pp68_salestrackingapp.data.model.Project
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM project ORDER BY startDate DESC")
    fun getAllProjects(): Flow<List<Project>>

    @Query("SELECT * FROM project WHERE projectName LIKE '%' || :searchQuery || '%'")
    fun searchProjects(searchQuery: String): Flow<List<Project>>

    @Query("SELECT * FROM project WHERE custId = :customerId")
    fun getProjectsByCustomer(customerId: String): Flow<List<Project>>

    @Query("SELECT * FROM project WHERE projectId = :projectId LIMIT 1")
    fun getProjectByIdFlow(projectId: String): Flow<Project?>

    @Query("SELECT * FROM project WHERE projectId = :projectId LIMIT 1")
    suspend fun getProjectById(projectId: String): Project?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProjects(projects: List<Project>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: Project)

    @Update
    suspend fun updateProject(project: Project)

    @Query("DELETE FROM project WHERE projectId = :projectId")
    suspend fun deleteProjectById(projectId: String)

    @Query("DELETE FROM project")
    suspend fun deleteAllProjects()

    @Query("DELETE FROM project WHERE is_synced = 1")
    suspend fun deleteAllSynced()

    @Query("SELECT COUNT(*) FROM project WHERE branchId = :branchId")
    suspend fun getProjectCountByBranch(branchId: String): Int

    @Query("SELECT COUNT(*) FROM project WHERE projectId LIKE :prefix || '%'")
    suspend fun getProjectCountByPrefix(prefix: String): Int

    @Transaction
    suspend fun clearAndInsert(projects: List<Project>) {
        deleteAllSynced()   // คง row is_synced=0 (ออฟไลน์) ไว้
        if (projects.isNotEmpty()) {
            insertProjects(projects)
        }
    }

    @Query("SELECT * FROM project WHERE is_synced = 0")
    suspend fun getUnsyncedProjects(): List<Project>

    @Query("UPDATE project SET is_synced = :isSynced WHERE projectId = :projectId")
    suspend fun updateSyncStatus(projectId: String, isSynced: Boolean)
}
