package com.example.pp68_salestrackingapp.data.repository

import com.example.pp68_salestrackingapp.data.local.ProjectDao
import com.example.pp68_salestrackingapp.data.model.Project
import com.example.pp68_salestrackingapp.data.model.ProjectMemberInsertDto
import com.example.pp68_salestrackingapp.data.remote.ApiService
import com.example.pp68_salestrackingapp.data.remote.FirebaseRealtimeService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ProjectRepository @Inject constructor(
    private val apiService: ApiService,
    private val projectDao: ProjectDao,
    private val firebaseService: FirebaseRealtimeService
) {
    fun getAllProjectsFlow(): Flow<List<Project>> = projectDao.getAllProjects()
    fun searchProjectsFlow(query: String): Flow<List<Project>> =
        projectDao.searchProjects("%$query%")

    suspend fun refreshProjects(userId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val memberResp = apiService.getMyProjectIds(userId = "eq.$userId")
                if (!memberResp.isSuccessful || memberResp.body().isNullOrEmpty()) {
                    projectDao.clearAndInsert(emptyList())
                    return@withContext Result.success(Unit)
                }
                val projectIds = memberResp.body()!!.map { it.projectId }
                val idsParam   = "in.(${projectIds.joinToString(",")})"
                val projectResp = apiService.getProjectsByIds(projectIds = idsParam)
                if (projectResp.isSuccessful && projectResp.body() != null) {
                    projectDao.clearAndInsert(projectResp.body()!!)
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("โหลด Project ไม่สำเร็จ: HTTP ${projectResp.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(Exception("Network Error: ${e.message}"))
            }
        }
    }

    suspend fun getProjectById(projectId: String): Result<Project> {
        return withContext(Dispatchers.IO) {
            try {
                val local = projectDao.getProjectById(projectId)
                if (local != null) return@withContext Result.success(local)
                val response = apiService.getProjectById("eq.$projectId")
                val body     = response.body()
                if (response.isSuccessful && !body.isNullOrEmpty()) {
                    projectDao.insertProject(body.first())
                    Result.success(body.first())
                } else {
                    Result.failure(Exception("ไม่พบข้อมูล Project"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun createProject(project: Project): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.addProject(project)
                projectDao.insertProject(project)
                if (response.isSuccessful) Result.success(Unit)
                else Result.failure(Exception("HTTP ${response.code()}: ${response.errorBody()?.string()}"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun updateProject(project: Project): kotlin.Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                // ✅ คำนวณ progress_pct จาก status
                val progressPct = when (project.projectStatus) {
                    "Lead"            -> 10
                    "New Project"     -> 20
                    "Quotation"       -> 40
                    "Bidding"         -> 50
                    "Make a Decision" -> 70
                    "Assured"         -> 80
                    "PO"              -> 100
                    else              -> 0
                }

                val updates = mapOf(
                    "project_name"   to project.projectName,
                    "project_status" to (project.projectStatus ?: ""),
                    "expected_value" to (project.expectedValue?.toString() ?: ""),
                    "branch_id"      to (project.branchId ?: ""),
                    "progress_pct"   to progressPct.toString()  // ✅ เพิ่ม
                )
                val response = apiService.updateProject("eq.${project.projectId}", updates)
                projectDao.insertProject(project.copy(progressPct = progressPct))

                if (response.isSuccessful) {
                    firebaseService.updateProjectStatus(
                        projectId   = project.projectId,
                        newStatus   = project.projectStatus ?: "",
                        projectName = project.projectName,
                        updatedBy   = ""
                    )
                    kotlin.Result.success(Unit)
                } else {
                    kotlin.Result.failure(Exception("HTTP ${response.code()}"))
                }
            } catch (e: Exception) {
                kotlin.Result.failure(e)
            }
        }
    }

    suspend fun updateProjectFields(
        projectId: String,
        fields: Map<String, String>
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.updateProject("eq.$projectId", fields)
                if (response.isSuccessful) {
                    // ✅ แจ้ง Firebase ถ้ามีการเปลี่ยน status
                    fields["project_status"]?.let { newStatus ->
                        val project = projectDao.getProjectById(projectId)
                        firebaseService.updateProjectStatus(
                            projectId   = projectId,
                            newStatus   = newStatus,
                            projectName = project?.projectName ?: projectId,
                            updatedBy   = ""
                        )
                        firebaseService.pushStatusChangeEvent(
                            projectId   = projectId,
                            projectName = project?.projectName ?: projectId,
                            oldStatus   = project?.projectStatus ?: "",
                            newStatus   = newStatus,
                            updatedBy   = ""
                        )
                    }
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("อัปเดต Project ไม่สำเร็จ: HTTP ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getBranches(): Result<List<Pair<String, String>>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getBranches()
                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!.map { it.branchId to it.branchName })
                } else Result.success(emptyList())
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getMembersByBranch(branchId: String): Result<List<Pair<String, String>>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getUsersByBranch("eq.$branchId")
                if (response.isSuccessful && response.body() != null) {
                    Result.success(
                        response.body()!!.map { it.userId to (it.fullName ?: it.userId) }
                    )
                } else Result.success(emptyList())
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun addProjectMembers(
        projectId: String,
        userIds:   List<String>,
        role:      String = "support"
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val members = userIds.map { userId ->
                    ProjectMemberInsertDto(
                        projectId = projectId,
                        userId    = userId,
                        saleRole  = role
                    )
                }
                val response = apiService.addProjectMembers(members)
                if (response.isSuccessful) Result.success(Unit)
                else Result.failure(Exception("HTTP ${response.code()}"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun countProjectsByPrefix(prefix: String): Int {
        return withContext(Dispatchers.IO) {
            try {
                projectDao.getAllProjects().first()
                    .count { it.projectNumber?.startsWith(prefix) == true }
            } catch (e: Exception) { 0 }
        }
    }
}
