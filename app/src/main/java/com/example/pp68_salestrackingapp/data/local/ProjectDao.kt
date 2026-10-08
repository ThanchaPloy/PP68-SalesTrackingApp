package com.example.pp68_salestrackingapp.data.local

import androidx.room.*
import androidx.paging.PagingSource
import com.example.pp68_salestrackingapp.data.model.Project
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    /** โครงการของผู้ใช้คนนี้เท่านั้น — คอลัมน์เจ้าของคือ create_by ไม่ใช่ user_id (ซึ่งคือ requestBy) */
    @Query("SELECT * FROM project WHERE create_by = :ownerId ORDER BY startDate DESC")
    fun getProjectsOwnedBy(ownerId: String): Flow<List<Project>>

    @Query("SELECT * FROM project ORDER BY startDate DESC")
    // ดึงข้อมูล ทั้งหมด โครงการ
    fun getAllProjects(): Flow<List<Project>>

    @Query("SELECT * FROM project WHERE projectName LIKE '%' || :searchQuery || '%'")
    // ค้นหา โครงการ
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
        WHERE create_by = :ownerId
        AND (
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
    // ดึงข้อมูล โครงการ Paging
    fun getProjectsPaging(
        ownerId: String,
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
    // ดึงข้อมูล โครงการ ตาม ลูกค้า
    fun getProjectsByCustomer(customerId: String): Flow<List<Project>>

    @Query("DELETE FROM project WHERE custId = :customerId")
    // ลบ โครงการ ตาม ลูกค้า รหัส
    suspend fun deleteProjectsByCustomerId(customerId: String)

    @Query("SELECT * FROM project WHERE projectId = :projectId LIMIT 1")
    // ดึงข้อมูล โครงการ ตาม รหัส กระแสข้อมูล
    fun getProjectByIdFlow(projectId: String): Flow<Project?>

    @Query("SELECT * FROM project WHERE projectId = :projectId LIMIT 1")
    // ดึงข้อมูล โครงการ ตาม รหัส
    suspend fun getProjectById(projectId: String): Project?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    // เพิ่ม โครงการ Raw
    suspend fun insertProjectsRaw(projects: List<Project>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    // เพิ่ม โครงการ Raw
    suspend fun insertProjectRaw(project: Project): Long

    @Update
    // อัปเดต โครงการ
    suspend fun updateProjects(projects: List<Project>)

    @Update
    // อัปเดต โครงการ Raw
    suspend fun updateProjectRaw(project: Project)

    @Query("SELECT projectId FROM project WHERE is_synced = 0")
    // ดึงข้อมูล ที่ยังไม่ซิงก์ โครงการ รหัส
    suspend fun getUnsyncedProjectIds(): List<String>

    @Transaction
    // เพิ่ม โครงการ
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
    // เพิ่ม โครงการ
    suspend fun insertProject(project: Project) {
        val insertResult = insertProjectRaw(project)
        if (insertResult == -1L) {
            updateProjectRaw(project)
        }
    }

    @Query("DELETE FROM project WHERE projectId = :projectId")
    // ลบ โครงการ ตาม รหัส
    suspend fun deleteProjectById(projectId: String)


    @Query("DELETE FROM project WHERE is_synced = 1")
    // ลบ ทั้งหมด Synced
    suspend fun deleteAllSynced()

    @Query("SELECT COUNT(*) FROM project WHERE branchId = :branchId")
    // ดึงข้อมูล โครงการ Count ตาม สาขา
    suspend fun getProjectCountByBranch(branchId: String): Int

    @Query("SELECT COUNT(*) FROM project WHERE projectId LIKE :prefix || '%'")
    // ดึงข้อมูล โครงการ Count ตาม Prefix
    suspend fun getProjectCountByPrefix(prefix: String): Int

    @Transaction
    // ล้าง And Insert
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
    // ลบ Synced โครงการ Not ใน
    suspend fun deleteSyncedProjectsNotIn(incomingIds: List<String>)

    @Query("SELECT * FROM project WHERE is_synced = 0")
    // ดึงข้อมูล ที่ยังไม่ซิงก์ โครงการ
    suspend fun getUnsyncedProjects(): List<Project>

    @Query("UPDATE project SET is_synced = :isSynced WHERE projectId = :projectId")
    // อัปเดต การซิงก์ สถานะ
    suspend fun updateSyncStatus(projectId: String, isSynced: Boolean)

    @Query("UPDATE project SET custId = :newCustId WHERE custId = :oldCustId")
    // อัปเดต Cust รหัส สำหรับ โครงการ
    suspend fun updateCustIdForProjects(oldCustId: String, newCustId: String)

}
