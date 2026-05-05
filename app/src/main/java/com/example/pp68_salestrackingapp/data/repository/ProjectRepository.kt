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
                    val projects = projectResp.body()!!.map { project ->
                        val myNumber = memberData.find { it.projectId == project.projectId }?.projectNumber
                        project.copy(projectNumber = myNumber)
                    }
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

    suspend fun getProjectMembersDetailed(projectId: String): List<Pair<String, String>> {
        return withContext(Dispatchers.IO) {
            try {
                val resp = apiService.getProjectMembers("eq.$projectId")
                if (resp.isSuccessful) {
                    val members = resp.body() ?: emptyList()
                    members.mapNotNull { m ->
                        val uResp = apiService.getUserById("eq.${m.userId}")
                        if (uResp.isSuccessful) {
                            val user = uResp.body()?.firstOrNull()
                            if (user != null) {
                                user.userId to (user.fullName ?: user.userId)
                            } else null
                        } else null
                    }
                } else emptyList()
            } catch (e: Exception) { emptyList() }
        }
    }

    suspend fun createProject(project: Project, userId: String): Result<Project> {
        return withContext(Dispatchers.IO) {
            val generatedNumber = generateNewProjectNumber(project.branchId ?: "")
            val projectWithNumber = project.copy(projectNumber = generatedNumber)
            projectDao.insertProject(projectWithNumber)

            try {
                // ✅ แก้ไข: ส่ง projectNumber ไปด้วยเพื่อให้บันทึกลงคอลัมน์ project_number ในตาราง project
                val response = apiService.addProject(projectWithNumber)
                if (response.isSuccessful) {
                    val member = ProjectMemberInsertDto(
                        projectId = projectWithNumber.projectId,
                        userId = userId,
                        saleRole = "owner",
                        projectNumber = generatedNumber
                    )
                    apiService.addProjectMembers(listOf(member))
                    Result.success(projectWithNumber)
                } else {
                    val errBody = response.errorBody()?.string() ?: ""
                    Result.failure(Exception("Server Rejected: $errBody"))
                }
            } catch (e: Exception) {
                if (e is IOException) Result.success(projectWithNumber)
                else Result.failure(e)
            }
        }
    }

    private suspend fun generateNewProjectNumber(branchId: String): String {
        return try {
            val prefix = branchId.take(2).uppercase().ifBlank { "PJ" }
            val now = LocalDate.now()
            val beYear = (now.year + 543) % 100
            val yearStr = "%02d".format(beYear)
            val monthStr = "%02d".format(now.monthValue)
            val count = projectDao.getProjectCountByBranch(branchId)
            val seqStr = "%03d".format(count + 1)
            "$prefix-$yearStr-$monthStr-$seqStr"
        } catch (e: Exception) {
            "PJ-" + System.currentTimeMillis().toString().takeLast(7)
        }
    }

    suspend fun updateProject(project: Project, updatedBy: String = ""): kotlin.Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
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

                apiService.setAppContext(
                    mapOf("user_id" to updatedBy.ifBlank { "unknown" }, "source" to "edit_project")
                )

                val updates = mutableMapOf<String, Any?>(
                    "project_name"      to project.projectName,
                    "project_status"    to project.projectStatus,
                    "expected_value"    to project.expectedValue,
                    "branch_id"         to project.branchId,
                    "billing_branch_id" to project.billingBranchId,
                    "loss_reason"       to project.lossReason,
                    "start_date"        to project.startDate,
                    "closing_date"      to project.closingDate,
                    "progress_pct"      to progressPct,
                )
                project.projectLat?.let { updates["project_lat"] = it }
                project.projectLong?.let { updates["project_long"] = it }
                
                projectDao.insertProject(project.copy(progressPct = progressPct))
                
                val response = apiService.updateProject("eq.${project.projectId}", updates)
                if (response.isSuccessful) {
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
                if (e is IOException) kotlin.Result.success(Unit)
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

    suspend fun updateProjectFields(
        projectId: String,
        fields:    Map<String, String>,
        updatedBy: String = ""
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {

                apiService.setAppContext(
                    mapOf("user_id" to updatedBy.ifBlank { "unknown" }, "source" to "activity_result")
                )

                val response = apiService.updateProject("eq.$projectId", fields)
                if (response.isSuccessful) {
                    fields["project_status"]?.let { newStatus ->
                        val project = projectDao.getProjectById(projectId)
                        firebaseService.updateProjectStatus(
                            projectId   = projectId,
                            newStatus   = newStatus,
                            projectName = project?.projectName ?: projectId,
                            updatedBy   = updatedBy
                        )
                    }
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("HTTP ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getBranches(): Result<List<com.example.pp68_salestrackingapp.data.model.Branch>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getBranches()
                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else Result.success(emptyList())
            } catch (e: Exception) { Result.failure(e) }
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
            } catch (e: Exception) { Result.success(emptyList()) }
        }
    }

    suspend fun addProjectMembers(
        projectId: String,
        userIds:   List<String>,
        role:      String = "support",
        projectNumber: String? = null
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                apiService.deleteProjectMembers("eq.$projectId")
                if (userIds.isEmpty()) return@withContext Result.success(Unit)

                val members = userIds.map { userId ->
                    ProjectMemberInsertDto(
                        projectId = projectId,
                        userId    = userId.trim(),
                        saleRole  = role,
                        projectNumber = projectNumber
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
                    // ✅ ดึง contactId จาก ProjectContactResponse row โดยตรง
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
        return projectDao.getAllProjects().first().count { it.projectNumber?.startsWith(prefix) == true }
    }
}
