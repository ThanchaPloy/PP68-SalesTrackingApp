package com.example.pp68_salestrackingapp.data.repository

import com.example.pp68_salestrackingapp.data.local.ProjectDao
import com.example.pp68_salestrackingapp.data.model.ContactPerson
import com.example.pp68_salestrackingapp.data.model.Project
import com.example.pp68_salestrackingapp.data.model.ProjectContact
import com.example.pp68_salestrackingapp.data.model.ProjectMemberInsertDto
import com.example.pp68_salestrackingapp.data.remote.ApiService
import com.example.pp68_salestrackingapp.data.remote.FirebaseRealtimeService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate
import javax.inject.Inject

class ProjectRepository @Inject constructor(
    private val apiService: ApiService,
    private val projectDao: ProjectDao,
    private val firebaseService: FirebaseRealtimeService
) {
    fun getAllProjectsFlow(): Flow<List<Project>> = projectDao.getAllProjects()
    fun searchProjectsFlow(query: String): Flow<List<Project>> =
        projectDao.searchProjects("%$query%")

    fun getProjectByIdFlow(projectId: String): Flow<Project?> = projectDao.getProjectByIdFlow(projectId)

    suspend fun refreshProjects(userId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val memberResp = apiService.getMyProjectIds(userId = "eq.$userId")
                if (!memberResp.isSuccessful || memberResp.body().isNullOrEmpty()) {
                    projectDao.clearAndInsert(emptyList())
                    return@withContext Result.success(Unit)
                }

                val memberData = memberResp.body()!!
                val projectIds = memberData.map { it.projectId }

                val idsParam   = "in.(${projectIds.joinToString(",")})"
                val projectResp = apiService.getProjectsByIds(projectIds = idsParam)

                if (projectResp.isSuccessful && projectResp.body() != null) {
                    val projects = projectResp.body()!!
                    projectDao.clearAndInsert(projects)
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
                if (response.isSuccessful && !response.body().isNullOrEmpty()) {
                    val project = response.body()!!.first()
                    projectDao.insertProject(project)
                    Result.success(project)
                } else {
                    Result.failure(Exception("ไม่พบข้อมูล Project"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun syncProject(projectId: String): Result<Project> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getProjectById("eq.$projectId")
                if (response.isSuccessful && !response.body().isNullOrEmpty()) {
                    val project = response.body()!!.first()
                    projectDao.insertProject(project)
                    Result.success(project)
                } else {
                    Result.failure(Exception("Sync ล้มเหลว: ไม่พบข้อมูลบน Server"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    // ✅ TC-FIX: ปรับปรุงให้แสดงชื่อ ID หากหาชื่อจริงไม่พบ เพื่อป้องกันข้อมูล "หาย" ใน UI
    suspend fun getProjectMembersDetailed(projectId: String): List<Pair<String, String>> {
        return withContext(Dispatchers.IO) {
            try {
                val resp = apiService.getProjectMembers("eq.$projectId")
                if (!resp.isSuccessful) return@withContext emptyList()

                val members = resp.body() ?: return@withContext emptyList()
                val userIds = members.mapNotNull { it.userId?.trim() }.filter { it.isNotBlank() }
                if (userIds.isEmpty()) return@withContext emptyList()

                // ✅ Batch call ครั้งเดียวเพื่อดึงข้อมูล User ทั้งทีม
                val idsParam = "in.(${userIds.joinToString(",")})"
                val uResp = apiService.getUsersByIds(userIds = idsParam)
                
                if (uResp.isSuccessful && uResp.body() != null) {
                    val nameMap = uResp.body()!!.associate { it.userId.trim() to (it.fullName ?: it.userId) }
                    userIds.map { id -> id to (nameMap[id] ?: id) }
                } else {
                    // หากดึงชื่อไม่ได้ ให้แสดง ID ไปก่อน ดีกว่าแสดงเป็นรายการว่าง
                    userIds.map { it to it }
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    suspend fun createProject(project: Project, userId: String): Result<Project> {
        return withContext(Dispatchers.IO) {
            val generatedId = generateNewProjectNumber(project.branchId ?: "")
            val projectToSave = project.copy(
                projectId = generatedId
            )
            // ยังไม่ insert local ทันที เพื่อให้ API เป็น source of truth
            
            try {
                val response = apiService.addProject(projectToSave)
                if (response.isSuccessful) {
                    // สร้างเจ้าของโครงการในตาราง member
                    val member = ProjectMemberInsertDto(
                        projectId = generatedId,
                        userId = userId,
                        saleRole = "owner"
                    )
                    apiService.addProjectMembers(listOf(member))
                    
                    projectDao.insertProject(projectToSave)
                    Result.success(projectToSave)
                } else {
                    val errBody = response.errorBody()?.string() ?: ""
                    Result.failure(Exception("Server Rejected: $errBody"))
                }
            } catch (e: Exception) {
                if (e is IOException) {
                    projectDao.insertProject(projectToSave)
                    Result.success(projectToSave)
                }
                else Result.failure(e)
            }
        }
    }

    private suspend fun generateNewProjectNumber(branchId: String): String {
        return try {
            val bb = branchId.take(2).uppercase().ifBlank { "PJ" }
            val now = LocalDate.now()
            val beYear = (now.year + 543) % 100
            val yy = "%02d".format(beYear)
            val mm = "%02d".format(now.monthValue)
            val prefix = "$bb$yy$mm"
            val count = projectDao.getProjectCountByPrefix(prefix)
            val xxx = "%03d".format(count + 1)
            "$prefix$xxx"
        } catch (e: Exception) {
            "PJ" + System.currentTimeMillis().toString().takeLast(7)
        }
    }

    suspend fun updateProject(project: Project, updatedBy: String = ""): kotlin.Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val updates = mutableMapOf<String, Any?>(
                    "project_name"      to project.projectName,
                    "project_status"    to project.projectStatus,
                    "expected_value"    to project.expectedValue,
                    "branch_id"         to project.branchId,
                    "billing_branch_id" to project.billingBranchId,
                    "opportunity_score" to project.opportunityScore,
                    "loss_reason"       to project.lossReason,
                    "start_date"        to project.startDate,
                    "closing_date"      to project.closingDate,
                    "progress_pct"      to project.progressPct,
                    "updated_at"        to java.time.Instant.now().toString()
                )
                project.projectLat?.let { updates["project_lat"] = it }
                project.projectLong?.let { updates["project_long"] = it }

                val response = apiService.updateProject("eq.${project.projectId}", updates)
                if (response.isSuccessful) {
                    projectDao.insertProject(project)
                    firebaseService.updateProjectStatus(
                        projectId   = project.projectId,
                        newStatus   = project.projectStatus ?: "",
                        projectName = project.projectName,
                        updatedBy   = updatedBy
                    )
                    kotlin.Result.success(Unit)
                } else {
                    val errBody = response.errorBody()?.string() ?: ""
                    kotlin.Result.failure(Exception("Server Rejected: $errBody"))
                }
            } catch (e: Exception) {
                if (e is IOException) {
                    projectDao.insertProject(project)
                    kotlin.Result.success(Unit)
                }
                else kotlin.Result.failure(e)
            }
        }
    }

    suspend fun deleteProject(projectId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                apiService.deleteProjectMembers("eq.$projectId")
                apiService.deleteProjectContacts("eq.$projectId")
                val response = apiService.deleteProject("eq.$projectId")
                if (response.isSuccessful) {
                    projectDao.deleteProjectById(projectId)
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("HTTP ${response.code()}"))
                }
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
                // ลบสมาชิกเดิมออกก่อน (Sync สมาชิกใหม่เข้าไปแทน)
                apiService.deleteProjectMembers("eq.$projectId")
                if (userIds.isEmpty()) return@withContext Result.success(Unit)

                val members = userIds.map { userId ->
                    ProjectMemberInsertDto(
                        projectId = projectId,
                        userId    = userId.trim(),
                        saleRole  = role
                    )
                }
                val response = apiService.addProjectMembers(members)
                if (response.isSuccessful) Result.success(Unit)
                else {
                    val err = response.errorBody()?.string() ?: "Unknown error"
                    Result.failure(Exception("Failed to add members: $err"))
                }
            } catch (e: Exception) { Result.failure(e) }
        }
    }

    suspend fun saveProjectContacts(projectId: String, contactIds: List<String>): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                apiService.deleteProjectContacts("eq.$projectId")
                if (contactIds.isNotEmpty()) {
                    val contacts = contactIds.map { ProjectContact(projectId, it.trim()) }
                    val response = apiService.addProjectContacts(contacts)
                    if (response.isSuccessful) Result.success(Unit)
                    else {
                        val err = response.errorBody()?.string() ?: "Unknown error"
                        Result.failure(Exception("Failed to add contacts: $err"))
                    }
                } else Result.success(Unit)
            } catch (e: Exception) { Result.failure(e) }
        }
    }

    suspend fun getProjectContacts(projectId: String): Result<List<ContactPerson>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getProjectContacts("eq.$projectId")
                if (response.isSuccessful && response.body() != null) {
                    val contacts = response.body()!!.map { row ->
                        ContactPerson(
                            contactId = row.contactId,
                            custId    = "",
                            fullName  = row.contactPerson?.fullName
                        )
                    }
                    Result.success(contacts)
                } else {
                    Result.success(emptyList())
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun countProjectsByPrefix(prefix: String): Int {
        return projectDao.getProjectCountByPrefix(prefix)
    }

    suspend fun getMembersByBranch(branchId: String): Result<List<Pair<String, String>>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getUsersByBranch("eq.$branchId")
                if (response.isSuccessful && response.body() != null) {
                    val pairs = response.body()!!.map { it.userId to (it.fullName ?: it.userId) }
                    Result.success(pairs)
                } else {
                    Result.failure(Exception("Failed to fetch members: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getBranches(): Result<List<Pair<String, String>>> {
        return try {
            val response = apiService.getBranches()
            if (response.isSuccessful) {
                val list = response.body()?.map { it.branchId to it.branchName } ?: emptyList()
                Result.success(list)
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Result.success(emptyList())
        }
    }

    suspend fun updateProjectFields(
        projectId: String,
        fields: Map<String, Any?>
    ): Result<Unit> {
        return try {
            val response = apiService.updateProject("eq.$projectId", fields)
            if (response.isSuccessful) {
                val newStatus = fields["project_status"] as? String
                if (newStatus != null) {
                    val project = projectDao.getProjectById(projectId)
                    project?.let {
                        firebaseService.updateProjectStatus(
                            projectId, newStatus, it.projectName, ""
                        )
                    }
                }
                Result.success(Unit)
            } else {
                Result.failure(Exception("API Error: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
