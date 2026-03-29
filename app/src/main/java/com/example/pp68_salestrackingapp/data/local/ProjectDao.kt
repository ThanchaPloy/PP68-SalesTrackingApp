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

    // Flow version (ใช้ใน UI observe)
    @Query("SELECT * FROM project WHERE projectId = :projectId LIMIT 1")
    fun getProjectByIdFlow(projectId: String): Flow<Project?>

    // Suspend version (ใช้ใน Repository ดึงครั้งเดียว)
    @Query("SELECT * FROM project WHERE projectId = :projectId LIMIT 1")
    suspend fun getProjectById(projectId: String): Project?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProjects(projects: List<Project>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: Project)

    @Update
    suspend fun updateProject(project: Project)

    @Query("DELETE FROM project")
    suspend fun deleteAllProjects()

    // ✅ เปลี่ยนจากลบแล้วลงใหม่ เป็นแค่การ Upsert เพื่อป้องกันข้อมูลที่ยังไม่ได้ Sync หาย
    @Transaction
    suspend fun clearAndInsert(projects: List<Project>) {
        insertProjects(projects)
    }
}
