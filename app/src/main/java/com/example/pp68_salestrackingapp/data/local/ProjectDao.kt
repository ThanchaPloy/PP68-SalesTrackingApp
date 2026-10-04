package com.example.pp68_salestrackingapp.data.local

import androidx.room.*
import androidx.paging.PagingSource
import com.example.pp68_salestrackingapp.data.model.Project
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM project ORDER BY startDate DESC")
    fun getAllProjects(): Flow<List<Project>>

    @Query("SELECT * FROM project WHERE projectName LIKE '%' || :searchQuery || '%'")
    fun searchProjects(searchQuery: String): Flow<List<Project>>

    /**
     * Paging source used only by the main project list.
     *
     * Filtering stays in SQLite so the UI never materializes the entire project table merely to
     * discard most rows. projectId is the final tie-breaker to keep page boundaries deterministic.
     */
    @Query(
        """
        SELECT * FROM project
        WHERE (
            (:tabIndex = 0 AND (projectStatus IS NULL OR projectStatus NOT IN (:closedStatuses)))
            OR (:tabIndex = 1 AND projectStatus IN (:wonStatuses))
            OR (:tabIndex = 2 AND projectStatus IN (:lostStatuses))
        )
        AND (:searchQuery = '' OR projectName LIKE '%' || :searchQuery || '%')
        AND (:applyStatusFilter = 0 OR projectStatus IN (:selectedStatuses))
        AND (:applyScoreFilter = 0 OR UPPER(opportunityScore) IN (:selectedScores))
        ORDER BY startDate DESC, projectId ASC
        """
    )
    fun getProjectsPaging(
        searchQuery: String,
        tabIndex: Int,
        closedStatuses: List<String>,
        wonStatuses: List<String>,
        lostStatuses: List<String>,
        applyStatusFilter: Boolean,
        selectedStatuses: List<String>,
        applyScoreFilter: Boolean,
        selectedScores: List<String>
    ): PagingSource<Int, Project>

    @Query("SELECT * FROM project WHERE custId = :customerId")
    fun getProjectsByCustomer(customerId: String): Flow<List<Project>>

    @Query("DELETE FROM project WHERE custId = :customerId")
    suspend fun deleteProjectsByCustomerId(customerId: String)

    @Query("SELECT * FROM project WHERE projectId = :projectId LIMIT 1")
    fun getProjectByIdFlow(projectId: String): Flow<Project?>

    @Query("SELECT * FROM project WHERE projectId = :projectId LIMIT 1")
    suspend fun getProjectById(projectId: String): Project?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertProjectsRaw(projects: List<Project>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertProjectRaw(project: Project): Long

    @Update
    suspend fun updateProjects(projects: List<Project>)

    @Update
    suspend fun updateProjectRaw(project: Project)

    @Query("SELECT projectId FROM project WHERE is_synced = 0")
    suspend fun getUnsyncedProjectIds(): List<String>

    @Transaction
    suspend fun insertProjects(projects: List<Project>) {
        val insertResults = insertProjectsRaw(projects)
        val updateList = mutableListOf<Project>()
        var unsyncedIds: Set<String>? = null
        for (i in insertResults.indices) {
            if (insertResults[i] == -1L) {
                if (unsyncedIds == null) unsyncedIds = getUnsyncedProjectIds().toSet()
                if (!unsyncedIds.contains(projects[i].projectId)) {
                    updateList.add(projects[i])
                }
            }
        }
        if (updateList.isNotEmpty()) {
            updateProjects(updateList)
        }
    }

    @Transaction
    suspend fun insertProject(project: Project) {
        val insertResult = insertProjectRaw(project)
        if (insertResult == -1L) {
            updateProjectRaw(project)
        }
    }

    @Query("DELETE FROM project WHERE projectId = :projectId")
    suspend fun deleteProjectById(projectId: String)


    @Query("DELETE FROM project WHERE is_synced = 1")
    suspend fun deleteAllSynced()

    @Query("SELECT COUNT(*) FROM project WHERE branchId = :branchId")
    suspend fun getProjectCountByBranch(branchId: String): Int

    @Query("SELECT COUNT(*) FROM project WHERE projectId LIKE :prefix || '%'")
    suspend fun getProjectCountByPrefix(prefix: String): Int

    @Transaction
    suspend fun clearAndInsert(projects: List<Project>) {
        val incomingIds = projects.map { it.projectId }
        if (incomingIds.isNotEmpty()) {
            deleteSyncedProjectsNotIn(incomingIds)
        } else {
            deleteAllSynced()
        }
        if (projects.isNotEmpty()) {
            insertProjects(projects)
        }
    }

    @Query("DELETE FROM project WHERE is_synced = 1 AND projectId NOT IN (:incomingIds)")
    suspend fun deleteSyncedProjectsNotIn(incomingIds: List<String>)

    @Query("SELECT * FROM project WHERE is_synced = 0")
    suspend fun getUnsyncedProjects(): List<Project>

    @Query("UPDATE project SET is_synced = :isSynced WHERE projectId = :projectId")
    suspend fun updateSyncStatus(projectId: String, isSynced: Boolean)

    @Query("UPDATE project SET custId = :newCustId WHERE custId = :oldCustId")
    suspend fun updateCustIdForProjects(oldCustId: String, newCustId: String)

    @Query("UPDATE activity_table SET project_id = :realId WHERE project_id = :tempId")
    suspend fun remapActivityProjectIds(tempId: String, realId: String)

    @Query("UPDATE project_contact SET project_id = :realId WHERE project_id = :tempId")
    suspend fun remapProjectContactIds(tempId: String, realId: String)

    /** Replaces a server-generated project ID and all local references atomically. */
    @Transaction
    suspend fun replaceTemporaryProject(tempId: String, replacement: Project) {
        insertProject(replacement)
        remapActivityProjectIds(tempId, replacement.projectId)
        remapProjectContactIds(tempId, replacement.projectId)
        deleteProjectById(tempId)
    }
}
