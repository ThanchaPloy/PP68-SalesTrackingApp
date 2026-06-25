package com.example.pp68_salestrackingapp.data.repository

import com.example.pp68_salestrackingapp.data.local.ProjectContactDao
import com.example.pp68_salestrackingapp.data.local.ProjectDao
import com.example.pp68_salestrackingapp.data.local.ProjectSalesMemberDao
import com.example.pp68_salestrackingapp.data.model.ContactPerson
import com.example.pp68_salestrackingapp.data.model.Project
import com.example.pp68_salestrackingapp.data.model.ProjectContact
import com.example.pp68_salestrackingapp.data.model.ProjectMemberInsertDto
import com.example.pp68_salestrackingapp.data.model.ProjectSalesMember
import com.example.pp68_salestrackingapp.data.remote.ApiService
import com.example.pp68_salestrackingapp.data.remote.FirebaseRealtimeService
import com.example.pp68_salestrackingapp.utils.SyncManager
import android.util.Log
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
    private val projectContactDao: ProjectContactDao,
    private val projectSalesMemberDao: ProjectSalesMemberDao,
    private val firebaseService: FirebaseRealtimeService,
    private val syncManager: SyncManager
) {
    fun getAllProjectsFlow(): Flow<List<Project>> = projectDao.getAllProjects()
    fun searchProjectsFlow(query: String): Flow<List<Project>> =
        projectDao.searchProjects("%$query%")

    fun getProjectByIdFlow(projectId: String): Flow<Project?> = projectDao.getProjectByIdFlow(projectId)

    suspend fun refreshProjects(userId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val memberResp = apiService.getMyProjectIds(userId = "eq.$userId")
                val memberIds = if (memberResp.isSuccessful) memberResp.body()?.map { it.projectId } ?: emptyList() else emptyList()

                val creatorResp = apiService.getProjectsByCreator(userId = "eq.$userId")
                val creatorProjects = if (creatorResp.isSuccessful) creatorResp.body() ?: emptyList() else emptyList()

                val allIds = (memberIds + creatorProjects.map { it.projectId }).distinct()

                // ponytail: never clear local cache on empty — missing records would silently wipe all local data
                if (allIds.isEmpty()) return@withContext Result.success(Unit)

                val memberProjects = if (memberIds.isNotEmpty()) {
                    val r = apiService.getProjectsByIds(projectIds = "in.(${memberIds.joinToString(",")})")
                    if (r.isSuccessful) r.body() ?: emptyList() else emptyList()
                } else emptyList()

                val merged = (memberProjects + creatorProjects).distinctBy { it.projectId }
                    .map { it.copy(isSynced = true) }
                if (merged.isNotEmpty()) projectDao.clearAndInsert(merged)
                Result.success(Unit)
            } catch (e: IOException) {
                Result.success(Unit) // offline — Room data still valid
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun createProject(project: Project, userId: String): Result<Project> {
        return withContext(Dispatchers.IO) {
            val today = java.time.LocalDate.now().toString()
            val tempId = "TEMP-${java.util.UUID.randomUUID().toString().take(8).uppercase()}"
            val tempProject = project.copy(projectId = tempId, isSynced = false, createdAt = project.createdAt ?: today)
            projectDao.insertProject(tempProject)
            try {
                val body = mutableMapOf<String, Any?>(
                    "customer_code"           to project.custId,
                    "customer_name"           to project.customerName,
                    "project_name"            to project.projectName,
                    "branch_code"             to project.branchId,
                    "billing_branch_id"       to project.billingBranchId,
                    "expected_value"          to project.expectedValue,
                    "project_status"          to project.projectStatus,
                    "start_date"              to project.startDate,
                    "closing_date"            to project.closingDate,
                    "desired_completion_date" to project.desiredCompletionDate,
                    "project_lat"             to project.projectLat,
                    "project_long"            to project.projectLong,
                    "opportunity_score"       to project.opportunityScore,
                    "remark"                  to project.remark,
                    "create_by"               to project.createBy,
                    "created_at"              to (project.createdAt ?: today)
                ).filterValues { it != null }
                Log.d("ProjectRepo", "POST body: $body")
                val response = apiService.addProject(body)
                Log.d("ProjectRepo", "POST project → HTTP ${response.code()}")
                if (response.isSuccessful) {
                    val realId = response.body()?.firstOrNull()?.projectId
                    Log.d("ProjectRepo", "realId=$realId tempId=$tempId")
                    val finalProject = if (realId != null && realId != tempId) {
                        projectDao.deleteProjectById(tempId)
                        val real = tempProject.copy(projectId = realId, isSynced = true)
                        projectDao.insertProject(real)
                        real
                    } else {
                        projectDao.updateSyncStatus(tempId, true)
                        tempProject
                    }
                    apiService.addProjectMembers(listOf(ProjectMemberInsertDto(finalProject.projectId, userId, "owner")))
                    Result.success(finalProject)
                } else {
                    val err = response.errorBody()?.string()
                    Log.e("ProjectRepo", "POST failed ${response.code()}: $err")
                    syncManager.scheduleSync()
                    Result.success(tempProject)
                }
            } catch (e: IOException) {
                syncManager.scheduleSync()
                Result.success(tempProject)
            } catch (e: Exception) { Result.failure(e) }
        }
    }

    suspend fun updateProject(project: Project, updatedBy: String = ""): kotlin.Result<Unit> {
        return withContext(Dispatchers.IO) {
            val localProject = project.copy(isSynced = false)
            projectDao.insertProject(localProject)
            try {
                val updates = mutableMapOf<String, Any?>(
                    "project_name" to project.projectName,
                    "project_status" to project.projectStatus,
                    "expected_value" to project.expectedValue,
                    "branch_code" to project.branchId,
                    "billing_branch_id" to project.billingBranchId,
                    "opportunity_score" to project.opportunityScore,
                    "loss_reason" to project.lossReason,
                    "start_date" to project.startDate,
                    "closing_date" to project.closingDate,
                    "progress_pct" to project.progressPct,
                    "updated_at" to java.time.Instant.now().toString()
                )
                project.projectLat?.let { updates["project_lat"] = it }
                project.projectLong?.let { updates["project_long"] = it }

                val response = apiService.updateProject("eq.${project.projectId}", updates)
                if (response.isSuccessful) {
                    projectDao.updateSyncStatus(project.projectId, true)
                    firebaseService.updateProjectStatus(project.projectId, project.projectStatus ?: "", project.projectName, updatedBy)
                    kotlin.Result.success(Unit)
                } else {
                    syncManager.scheduleSync()
                    kotlin.Result.success(Unit)
                }
            } catch (e: Exception) {
                syncManager.scheduleSync()
                if (e is IOException) kotlin.Result.success(Unit) else kotlin.Result.failure(e)
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
                } else Result.failure(Exception("HTTP ${response.code()}"))
            } catch (e: Exception) { Result.failure(e) }
        }
    }

    suspend fun addProjectMembers(projectId: String, userIds: List<String>, role: String = "support"): Result<Unit> {
        return withContext(Dispatchers.IO) {
            projectSalesMemberDao.deleteByProject(projectId)
            if (userIds.isNotEmpty()) {
                projectSalesMemberDao.insertAll(userIds.map { ProjectSalesMember(projectId, it.trim(), role) })
            }
            try {
                apiService.deleteProjectMembers("eq.$projectId")
                if (userIds.isNotEmpty()) {
                    apiService.addProjectMembers(userIds.map { ProjectMemberInsertDto(projectId, it.trim(), role) })
                }
            } catch (_: Exception) { }
            Result.success(Unit)
        }
    }

    suspend fun saveProjectContacts(projectId: String, contactIds: List<String>): Result<Unit> {
        return withContext(Dispatchers.IO) {
            // บันทึก Room ก่อนเสมอ
            projectContactDao.deleteByProject(projectId)
            if (contactIds.isNotEmpty()) {
                val rows = contactIds.map { ProjectContact(projectId, it.trim()) }
                projectContactDao.insertAll(rows)
            }
            // sync API
            try {
                apiService.deleteProjectContacts("eq.$projectId")
                if (contactIds.isNotEmpty()) {
                    val rows = contactIds.map { ProjectContact(projectId, it.trim()) }
                    apiService.addProjectContacts(rows)
                }
            } catch (_: Exception) { /* offline — SyncWorker จะ retry */ }
            Result.success(Unit)
        }
    }

    suspend fun getProjectContacts(projectId: String): Result<List<ContactPerson>> {
        return withContext(Dispatchers.IO) {
            // อัพเดท Room จาก API ก่อน (ถ้าทำได้)
            try {
                val response = apiService.getProjectContacts("eq.$projectId")
                if (response.isSuccessful && response.body() != null) {
                    val rows = response.body()!!.map { ProjectContact(projectId, it.contactId) }
                    projectContactDao.deleteByProject(projectId)
                    if (rows.isNotEmpty()) projectContactDao.insertAll(rows)
                }
            } catch (_: Exception) { /* offline */ }
            // อ่านจาก Room เสมอ
            val ids = projectContactDao.getContactIdsByProject(projectId)
            Result.success(ids.map { ContactPerson(contactId = it, custId = "") })
        }
    }

    suspend fun getProjectMembersDetailed(projectId: String): List<Pair<String, String>> {
        return withContext(Dispatchers.IO) {
            // ponytail: read Room only — API emp_code column stores user_ids (USR-XXX), not real
            // emp_codes; pulling from API would overwrite our local emp_code chip selections
            val ids = projectSalesMemberDao.getMemberIdsByProject(projectId)
            ids.map { it to it }   // names resolved at display time via teamMemberOptions
        }
    }

    suspend fun updateProjectFields(projectId: String, fields: Map<String, Any?>): Result<Unit> {
        return try {
            val response = apiService.updateProject("eq.$projectId", fields)
            if (response.isSuccessful) {
                val newStatus = fields["project_status"] as? String
                if (newStatus != null) {
                    val project = projectDao.getProjectById(projectId)
                    project?.let { firebaseService.updateProjectStatus(projectId, newStatus, it.projectName, "") }
                }
                Result.success(Unit)
            } else Result.failure(Exception("API Error: ${response.code()}"))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun countProjectsByPrefix(prefix: String): Int = projectDao.getProjectCountByPrefix(prefix)

    suspend fun getBranchMembersRpc(empCode: String): Result<List<Pair<String, String>>> {
        return withContext(Dispatchers.IO) {
            try {
                val resp = apiService.getBranchMembers(mapOf("p_emp_code" to empCode))
                if (!resp.isSuccessful) return@withContext Result.failure(Exception("HTTP ${resp.code()}"))
                val result = (resp.body() ?: emptyList()).mapNotNull { row ->
                    val code = row["emp_code"]?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val name = row["emp_name"]?.trim()?.takeIf { it.isNotBlank() } ?: code
                    code to name
                }
                Result.success(result)
            } catch (e: Exception) { Result.failure(e) }
        }
    }

    suspend fun getMembersByBranch(branchId: String): Result<List<Pair<String, String>>> {
        return withContext(Dispatchers.IO) {
            try {
                val empResp = apiService.getEmployeeCodesByBranch(
                    branchCode = "eq.$branchId",
                    select = "emp_code,emp_name"
                )
                if (!empResp.isSuccessful || empResp.body().isNullOrEmpty()) {
                    return@withContext Result.failure(Exception("No employees found for branch $branchId"))
                }
                val employees = empResp.body()!!
                val result = employees.mapNotNull { row ->
                    val code = row["emp_code"]?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val name = row["emp_name"]?.trim()?.takeIf { it.isNotBlank() } ?: code
                    code to name
                }
                if (result.isEmpty()) Result.failure(Exception("No emp_codes"))
                else Result.success(result)
            } catch (e: Exception) { Result.failure(e) }
        }
    }

    suspend fun getBranches(): Result<List<Pair<String, String>>> {
        return try {
            val response = apiService.getBranches()
            if (response.isSuccessful) Result.success(response.body()?.map { it.branchId to it.branchName } ?: emptyList())
            else Result.success(emptyList())
        } catch (e: Exception) { Result.success(emptyList()) }
    }

    suspend fun getProjectById(projectId: String): Result<Project> {
        return withContext(Dispatchers.IO) {
            try {
                val local = projectDao.getProjectById(projectId)
                if (local != null) return@withContext Result.success(local)
                val response = apiService.getProjectById("eq.$projectId")
                if (response.isSuccessful && !response.body().isNullOrEmpty()) {
                    val project = response.body()!!.first().copy(isSynced = true)
                    projectDao.insertProject(project)
                    Result.success(project)
                } else Result.failure(Exception("ไม่พบข้อมูล Project"))
            } catch (e: Exception) { Result.failure(e) }
        }
    }
}
