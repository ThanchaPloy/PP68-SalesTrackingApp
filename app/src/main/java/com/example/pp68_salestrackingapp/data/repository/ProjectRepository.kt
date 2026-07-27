package com.example.pp68_salestrackingapp.data.repository

import com.example.pp68_salestrackingapp.data.local.ContactDao
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
    private val contactDao: ContactDao,
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
                val cleanUserId = userId.removePrefix("eq.")
                val memberResp = apiService.getMyProjectIds(userId = cleanUserId)
                val memberIds = if (memberResp.isSuccessful) memberResp.body()?.mapNotNull { it.projectId } ?: emptyList() else emptyList()

                val creatorResp = apiService.getProjectsByCreator(userId = cleanUserId)
                val creatorProjects = if (creatorResp.isSuccessful) creatorResp.body() ?: emptyList() else emptyList()

                val allIds = (memberIds + creatorProjects.map { it.projectId }).distinct()

                // ponytail: never clear local cache on empty — missing records would silently wipe all local data
                if (allIds.isEmpty()) return@withContext Result.success(Unit)

                val memberProjects = if (allIds.isNotEmpty()) {
                    val r = apiService.getProjectsByIds(projectIds = "in.(${allIds.joinToString(",")})")
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
                        val real = tempProject.copy(projectId = realId, isSynced = true)
                        projectDao.insertProject(real)
                        projectContactDao.updateProjectId(tempId, realId)
                        projectSalesMemberDao.updateProjectId(tempId, realId)
                        projectDao.deleteProjectById(tempId)
                        real
                    } else {
                        projectDao.updateSyncStatus(tempId, true)
                        tempProject
                    }
                    try {
                        apiService.addProjectMembers(listOf(ProjectMemberInsertDto(finalProject.projectId, userId, "owner")))
                    } catch (e: Exception) {
                        // ✅ โครงการสร้างสำเร็จแล้ว — ห้ามให้ error ตรงนี้ไปเปลี่ยนผลลัพธ์เป็น tempProject (id เก่าที่ถูกลบไปแล้ว)
                        Log.e("ProjectRepo", "addProjectMembers (owner) failed for ${finalProject.projectId}: ${e.message}")
                        syncManager.scheduleSync()
                    }
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
                    "customer_code" to project.custId,
                    "customer_name" to project.customerName,
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
                ).filterValues { it != null }.toMutableMap()
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
            Log.d("ProjectRepo", "addProjectMembers started. projectId=$projectId, userIds=$userIds")
            projectSalesMemberDao.deleteByProject(projectId)
            if (userIds.isNotEmpty()) {
                val localRows = userIds.map { ProjectSalesMember(projectId, it.trim(), role) }
                projectSalesMemberDao.insertAll(localRows)
                Log.d("ProjectRepo", "Inserted local members: ${localRows.size} rows")
            }
            if (projectId.startsWith("TEMP-")) {
                return@withContext Result.success(Unit)
            }
            try {
                val delResp = apiService.deleteProjectMembers("eq.$projectId")
                Log.d("ProjectRepo", "deleteProjectMembers API status: ${delResp.code()}")
                if (!delResp.isSuccessful) {
                    val errMsg = delResp.errorBody()?.string() ?: ""
                    return@withContext Result.failure(Exception("ลบสมาชิกเก่าล้มเหลว: HTTP ${delResp.code()} $errMsg"))
                }
                if (userIds.isNotEmpty()) {
                    val remoteRows = userIds.map { ProjectMemberInsertDto(projectId, it.trim(), role) }
                    val addResp = apiService.addProjectMembers(remoteRows)
                    Log.d("ProjectRepo", "addProjectMembers API status: ${addResp.code()}")
                    if (!addResp.isSuccessful) {
                        // ponytail: backend's POST /project_sales_member always 500s on the
                        // response step even though the row is actually committed (confirmed
                        // by direct curl testing against api-ploy.cskmitl.com) — verify the
                        // real DB state instead of trusting the broken status code
                        val verifyResp = apiService.getProjectMembers("eq.$projectId")
                        val actualIds = if (verifyResp.isSuccessful) {
                            verifyResp.body()?.mapNotNull { it.userId?.trim() }?.toSet() ?: emptySet()
                        } else emptySet()
                        val expectedIds = userIds.map { it.trim() }.toSet()
                        if (actualIds != expectedIds) {
                            val errMsg = addResp.errorBody()?.string() ?: ""
                            return@withContext Result.failure(Exception("บันทึกสมาชิกหลักล้มเหลว: HTTP ${addResp.code()} $errMsg"))
                        }
                        Log.w("ProjectRepo", "addProjectMembers got HTTP ${addResp.code()} but DB write verified OK — treating as success")
                    }
                }
                Result.success(Unit)
            } catch (e: IOException) {
                Log.w("ProjectRepo", "addProjectMembers offline: ${e.message}")
                Result.success(Unit) // Offline fallback
            } catch (e: Exception) {
                Log.e("ProjectRepo", "addProjectMembers failed: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    suspend fun saveProjectContacts(projectId: String, contactIds: List<String>): Result<Unit> {
        return withContext(Dispatchers.IO) {
            Log.d("ProjectRepo", "saveProjectContacts started. projectId=$projectId, contactIds=$contactIds")
            // บันทึก Room ก่อนเสมอ
            projectContactDao.deleteByProject(projectId)
            if (contactIds.isNotEmpty()) {
                val rows = contactIds.map { ProjectContact(projectId, it.trim()) }
                projectContactDao.insertAll(rows)
                Log.d("ProjectRepo", "Inserted local contacts: ${rows.size} rows")
            }
            if (projectId.startsWith("TEMP-")) {
                return@withContext Result.success(Unit)
            }
            // sync API
            try {
                val delResp = apiService.deleteProjectContacts("eq.$projectId")
                Log.d("ProjectRepo", "deleteProjectContacts API status: ${delResp.code()}")
                if (!delResp.isSuccessful) {
                    val errMsg = delResp.errorBody()?.string() ?: ""
                    return@withContext Result.failure(Exception("ลบผู้ติดต่อเก่าล้มเหลว: HTTP ${delResp.code()} $errMsg"))
                }
                if (contactIds.isNotEmpty()) {
                    val rows = contactIds.map { ProjectContact(projectId, it.trim()) }
                    val addResp = apiService.addProjectContacts(rows)
                    Log.d("ProjectRepo", "addProjectContacts API status: ${addResp.code()}")
                    if (!addResp.isSuccessful) {
                        val errMsg = addResp.errorBody()?.string() ?: ""
                        return@withContext Result.failure(Exception("บันทึกผู้ติดต่อหลักล้มเหลว: HTTP ${addResp.code()} $errMsg"))
                    }
                }
                Result.success(Unit)
            } catch (e: IOException) {
                Log.w("ProjectRepo", "saveProjectContacts offline: ${e.message}")
                Result.success(Unit) // Offline fallback
            } catch (e: Exception) {
                Log.e("ProjectRepo", "saveProjectContacts failed: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    suspend fun getProjectContacts(projectId: String): Result<List<ContactPerson>> {
        return withContext(Dispatchers.IO) {
            // อัพเดท Room จาก API ก่อน (ถ้าทำได้)
            if (!projectId.startsWith("TEMP-")) {
                try {
                    val response = apiService.getProjectContacts("eq.$projectId")
                    if (response.isSuccessful && response.body() != null) {
                        val rows = response.body()!!.map { ProjectContact(projectId, it.contactId) }
                        projectContactDao.deleteByProject(projectId)
                        if (rows.isNotEmpty()) {
                            val contactIds = rows.map { it.contact_id }.distinct()
                            if (contactIds.isNotEmpty()) {
                                val contactResp = apiService.getContactsByIds("in.(${contactIds.joinToString(",")})")
                                if (contactResp.isSuccessful && contactResp.body() != null) {
                                    contactDao.insertAll(contactResp.body()!!.map { it.copy(isSynced = true) })
                                }
                            }
                            projectContactDao.insertAll(rows)
                        }
                    }
                } catch (_: Exception) { /* offline */ }
            }
            // อ่านจาก Room เสมอ — ดึงรายละเอียดผู้ติดต่อเต็มๆ ไม่ใช่แค่ id
            val ids = projectContactDao.getContactIdsByProject(projectId)
            Result.success(ids.mapNotNull { contactDao.getContactById(it) })
        }
    }

    suspend fun getProjectMembersDetailed(projectId: String): List<Pair<String, String>> {
        return withContext(Dispatchers.IO) {
            // อัพเดท Room จาก API ก่อน (ถ้าทำได้) — เดิม select ผิดคอลัมน์ (emp_code,sales_role
            // ไม่มีจริง จริงคือ user_id,role) ทำให้ได้ list ว่างเสมอ จึงเคย bypass มาอ่าน Room อย่างเดียว
            if (!projectId.startsWith("TEMP-")) {
                try {
                    val resp = apiService.getProjectMembers("eq.$projectId")
                    if (resp.isSuccessful) {
                        val rows = (resp.body() ?: emptyList()).mapNotNull { m ->
                            m.userId?.trim()?.takeIf { it.isNotBlank() }?.let { ProjectSalesMember(projectId, it, m.saleRole ?: "support") }
                        }
                        projectSalesMemberDao.deleteByProject(projectId)
                        if (rows.isNotEmpty()) projectSalesMemberDao.insertAll(rows)
                    }
                } catch (_: Exception) { /* offline — ใช้ค่าที่มีใน Room */ }
            }
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
                val empResp = apiService.getEmployeeCodesByBranch(branchCode = "eq.$branchId")
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
