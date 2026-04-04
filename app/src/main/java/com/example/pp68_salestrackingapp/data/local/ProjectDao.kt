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

    @Query("DELETE FROM project WHERE projectId = :projectId")
    suspend fun deleteProjectById(projectId: String)

    @Query("DELETE FROM project")
    suspend fun deleteAllProjects()

    // ✅ เปลี่ยนเป็นลบข้อมูลเก่าก่อนเพื่อป้องกันข้อมูลค้างจาก User อื่น หรือโครงการที่ไม่อยู่ในความดูแลแล้ว
    @Transaction
    suspend fun clearAndInsert(projects: List<Project>) {
        deleteAllProjects()
        if (projects.isNotEmpty()) {
            insertProjects(projects)
        }
    }
}
