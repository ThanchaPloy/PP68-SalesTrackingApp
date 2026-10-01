package com.example.pp68_salestrackingapp.data.repository

import com.example.pp68_salestrackingapp.data.local.ContactDao
import com.example.pp68_salestrackingapp.data.local.ProjectContactDao
import com.example.pp68_salestrackingapp.data.local.ProjectDao
import com.example.pp68_salestrackingapp.data.model.ContactPerson
import com.example.pp68_salestrackingapp.data.model.Project
import com.example.pp68_salestrackingapp.data.model.ProjectContact
import com.example.pp68_salestrackingapp.data.model.ProjectFactorLog
import com.example.pp68_salestrackingapp.data.remote.ApiService
import com.example.pp68_salestrackingapp.utils.SyncManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate
import javax.inject.Inject
import com.example.pp68_salestrackingapp.utils.queuedOrFailed
import com.example.pp68_salestrackingapp.utils.retrySend

class ProjectRepository @Inject constructor(
    private val apiService: ApiService,
    private val projectDao: ProjectDao,
    private val projectContactDao: ProjectContactDao,
    private val contactDao: ContactDao,
    private val syncManager: SyncManager,
    private val networkMonitor: com.example.pp68_salestrackingapp.utils.NetworkMonitor
) {
    fun getAllProjectsFlow(): Flow<List<Project>> = projectDao.getAllProjects()
    fun searchProjectsFlow(query: String): Flow<List<Project>> =
        projectDao.searchProjects("%$query%")

    fun getProjectByIdFlow(projectId: String): Flow<Project?> = projectDao.getProjectByIdFlow(projectId)

    suspend fun refreshProjects(userId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val cleanUserId = userId.removePrefix("eq.")
                // ✅ 1 โครงการมีเจ้าของคนเดียว (create_by) — ไม่มีตาราง membership แยกแล้ว
                val creatorResp = apiService.getProjectsByCreator(userId = cleanUserId)
                val creatorProjects = if (creatorResp.isSuccessful) creatorResp.body() ?: emptyList() else emptyList()

                // ponytail: never clear local cache on empty — missing records would silently wipe all local data
                if (creatorProjects.isEmpty()) return@withContext Result.success(Unit)

                val merged = creatorProjects.distinctBy { it.projectId }.map { it.copy(isSynced = true) }
                projectDao.clearAndInsert(merged)
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
                // ไม่ log ตัว body — มันมีชื่อลูกค้า มูลค่าโครงการ วันที่ปิดดีล ครบชุด และ
                // minifyEnabled = false จึงไม่มี ProGuard มาตัด Log.d ออกตอน build release
                // แปลว่าข้อมูลลูกค้าจริงถูกพิมพ์ลง logcat ของเครื่องผู้ใช้ทุกครั้งที่สร้างโครงการ
                Log.d("ProjectRepo", "POST project (${body.size} fields)")
                val response = retrySend(idempotent = false, tag = "createProject") { apiService.addProject(body) }
                Log.d("ProjectRepo", "POST project → HTTP ${response.code()}")
                if (response.isSuccessful) {
                    val realId = response.body()?.firstOrNull()?.projectId
                    Log.d("ProjectRepo", "realId=$realId tempId=$tempId")
                    val finalProject = if (realId != null && realId != tempId) {
                        val returnedProject = response.body()?.first()
                        val real = (returnedProject ?: tempProject.copy(projectId = realId)).copy(isSynced = true)
                        projectDao.insertProject(real)
                        projectContactDao.updateProjectId(tempId, realId)
                        projectDao.deleteProjectById(tempId)
                        real
                    } else {
                        val returnedProject = response.body()?.firstOrNull()
                        if (returnedProject != null) {
                            projectDao.insertProject(returnedProject.copy(isSynced = true))
                        } else {
                            projectDao.updateSyncStatus(tempId, true)
                        }
                        returnedProject?.copy(isSynced = true) ?: tempProject
                    }
                    Result.success(finalProject)
                } else {
                    val err = response.errorBody()?.string()
                    Log.e("ProjectRepo", "POST failed ${response.code()}: $err")
                    // ❌ ไม่ใช่ offline (นี่คือ server ปฏิเสธ request จริง เช่น validation error) —
                    // อย่าคืน success เพราะ caller จะเข้าใจว่าบันทึกสำเร็จทั้งที่ยังไม่ถูกสร้างบนเซิร์ฟเวอร์
                    if (response.code() == 403) {
                        // ปฏิเสธถาวร ไม่ใช่ error ชั่วคราว — ลองใหม่ก็ 403 ซ้ำทุกครั้ง ไม่ต้อง schedule retry
                        syncManager.markBlocked("project", tempId)
                    } else {
                        syncManager.scheduleSync()
                    }
                    Result.failure(Exception("บันทึกโครงการไม่สำเร็จ: HTTP ${response.code()} $err"))
                }
            } catch (e: IOException) {
                // ไม่มีเน็ต — เก็บไว้ใน Room รอ retry ตาม offline-first แต่ถ้าเน็ตดีอยู่แล้ว
                // ยังส่งไม่ขึ้น ต้องบอกผู้ใช้ ไม่ใช่ปล่อยให้เข้าใจว่าโครงการถูกสร้างบน server แล้ว
                syncManager.scheduleSync()
                networkMonitor.queuedOrFailed(tempProject, e.message)
            } catch (e: Exception) { Result.failure(e) }
        }
    }

    // resultAppointmentId: appointment_id จริงของผลการขายที่ทำให้ project_status เปลี่ยน (null สำหรับ
    // standalone หรือแก้โครงการตรงๆ) — backend ใช้ค่านี้บันทึก project_stage_log.appointment_id ตรงๆ
    // ไม่มีการเดาจากวันที่ใกล้เคียงฝั่ง backend แล้ว เพราะนัดหมายใกล้วันที่สุดอาจไม่ใช่ของโครงการนี้เลย
    suspend fun updateProject(project: Project, resultAppointmentId: String? = null): kotlin.Result<Unit> {
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
                    "loss_reason_note" to project.lossReasonNote,
                    "deal_position" to project.dealPosition,
                    "current_solution" to project.previousSolution,
                    "counterparty_type" to project.counterpartyType,
                    "response_speed" to project.responseSpeed,
                    "is_proposal_sent" to project.isProposalSent,
                    "proposal_date" to project.proposalDate,
                    "competitor_count" to project.competitorCount,
                    "start_date" to project.startDate,
                    "closing_date" to project.closingDate,
                    "progress_pct" to project.progressPct,
                    "updated_at" to java.time.Instant.now().toString()
                ).filterValues { it != null }.toMutableMap()
                // ✅ filterValues ข้างบนจำเป็น — มันกันฟิลด์ที่ผู้เรียกไม่ได้ดูแล (progress_pct,
                // is_proposal_sent, competitor_count ฯลฯ) ไม่ให้ถูกล้างทิ้งโดยไม่ตั้งใจ
                // แต่ loss_reason ต้องยกเว้น เพราะ "ค่าว่าง" คือความหมายจริงที่ต้องส่ง: ทั้งสองผู้เรียก
                // (ฟอร์มแก้โครงการ และ syncProjectStatus ตอนสถานะออกจาก Lost/Failed) ตั้งใจให้ล้าง
                // ถ้าปล่อยให้ถูกกรองทิ้ง โครงการที่กลับมาเดินต่อจะยังติดเหตุผลที่ไม่ได้งานค้างบน server
                // แล้วแถวที่ตอบกลับมาก็เขียนทับ Room ให้ค่าเก่าเด้งกลับมาให้ผู้ใช้เห็นว่า "แก้ไม่ติด"
                updates["loss_reason"] = project.lossReason
                updates["loss_reason_note"] = project.lossReasonNote
                // ✅ สามช่องนี้ฟอร์มแก้โครงการเป็นเจ้าของเต็มและลบทิ้งได้จริง (AddProjectViewModel
                // ส่ง toDoubleOrNull() / ifBlank { null }) ถ้าปล่อยให้ filterValues ตัดทิ้งตอนเป็น null
                // = ลบมูลค่าหรือวันที่แล้วบันทึก ค่าเก่ายังอยู่บน server แล้ว refresh ดึงกลับมาทับ
                // ส่ง "" แทน null เพราะ Gson ไม่ได้เปิด serializeNulls — backend แปลง "" เป็น NULL ให้
                // (ผู้เรียกอีกทางคือ syncProjectStatus ซึ่งส่งแถวเต็มจาก Room จึงไม่กระทบ)
                updates["expected_value"] = project.expectedValue?.toString() ?: ""
                updates["start_date"] = project.startDate.orEmpty()
                updates["closing_date"] = project.closingDate.orEmpty()
                project.projectLat?.let { updates["project_lat"] = it }
                project.projectLong?.let { updates["project_long"] = it }
                resultAppointmentId?.let { updates["stage_appointment_id"] = it }

                val response = retrySend(idempotent = true, tag = "updateProject") { apiService.updateProject("eq.${project.projectId}", updates) }
                if (response.isSuccessful && response.body()?.isNotEmpty() == true) {
                    val returnedProject = response.body()!!.first().copy(isSynced = true)
                    projectDao.insertProject(returnedProject)
                    kotlin.Result.success(Unit)
                } else if (response.code() == 403) {
                    syncManager.markBlocked("project", project.projectId)
                    kotlin.Result.failure(Exception("แก้ไขโครงการไม่สำเร็จ: ไม่มีสิทธิ์ทำรายการนี้"))
                } else {
                    syncManager.scheduleSync()
                    networkMonitor.queuedOrFailed(Unit, "เซิร์ฟเวอร์ตอบ ${response.code()}")
                }
            } catch (e: Exception) {
                syncManager.scheduleSync()
                if (e is IOException) networkMonitor.queuedOrFailed(Unit, e.message)
                else kotlin.Result.failure(e)
            }
        }
    }

    suspend fun deleteProject(projectId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                if (projectId.startsWith("TEMP-")) {
                    projectDao.deleteProjectById(projectId)
                    // โครงการที่ยังไม่เคยขึ้น server ก็มีผู้ติดต่อผูกไว้ในเครื่องได้เหมือนกัน
                    projectContactDao.deleteByProject(projectId)
                    return@withContext Result.success(Unit)
                }
                // ✅ ต้องเช็คผลของขั้นแรกก่อนไปขั้นสอง (เหมือนที่ deleteCustomer ทำ) — เดิมยิงลบ
                // ผู้ติดต่อทิ้งแล้วไม่ดูผลเลย ถ้าลบตัวโครงการต่อไม่สำเร็จ (เช่น 403) จะเหลือโครงการ
                // ที่ผู้ติดต่อถูกลบไปแล้วบน server โดยผู้ใช้ไม่รู้ว่าเสียอะไรไป
                val contactsResp = apiService.deleteProjectContacts("eq.$projectId")
                if (!contactsResp.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("ลบผู้ติดต่อของโครงการไม่สำเร็จ (HTTP ${contactsResp.code()}) จึงยังไม่ลบโครงการ")
                    )
                }
                val response = apiService.deleteProject("eq.$projectId")
                if (response.isSuccessful) {
                    projectDao.deleteProjectById(projectId)
                    // Room ไม่มี FK CASCADE — ลบแถวผูกผู้ติดต่อในเครื่องเองด้วย ไม่งั้นค้างเป็นแถวกำพร้า
                    // ที่ชี้ไปหาโครงการที่ไม่มีแล้ว (และจะถูกส่งขึ้น server อีกตอน saveProjectContacts)
                    projectContactDao.deleteByProject(projectId)
                    Result.success(Unit)
                } else Result.failure(Exception("HTTP ${response.code()}"))
            } catch (e: Exception) { Result.failure(e) }
        }
    }

    suspend fun saveProjectContacts(projectId: String, contactIds: List<String>): Result<Unit> {
        return withContext(Dispatchers.IO) {
            Log.d("ProjectRepo", "saveProjectContacts started. projectId=$projectId, ${contactIds.size} contacts")
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
                // Room เก็บรายชื่อใหม่ไว้แล้ว แต่ outbox วนเฉพาะโปรเจคที่ is_synced = 0 — ถ้าไม่ปักธง
                // ตรงนี้ การแก้ผู้ติดต่อตอนออฟไลน์จะไม่มีวันถูกอัปขึ้น server เลย
                projectDao.updateSyncStatus(projectId, false)
                syncManager.scheduleSync()
                networkMonitor.queuedOrFailed(Unit, e.message)
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

    // ✅ 1 โครงการมีเจ้าของคนเดียว (project.createBy) — resolve ชื่อแสดงผลจาก /user
    suspend fun getProjectOwnerName(userId: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val resp = apiService.getUserById("eq.$userId")
                val name = resp.body()?.firstOrNull()?.fullName?.trim()?.ifBlank { null }
                name ?: userId
            } catch (e: Exception) { userId }
        }
    }

    // ประวัติการแก้ไขปัจจัยข้อ 4-9 — online-only โดยตั้งใจ ไม่ cache ลง Room เพราะเป็นข้อมูล
    // อ่านย้อนหลังเฉยๆ ออฟไลน์แล้วคืน list ว่างให้ UI โชว์สถานะ "ดูประวัติไม่ได้ตอนออฟไลน์"
    suspend fun getFactorHistory(projectId: String): Result<List<ProjectFactorLog>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getProjectFactorLog("eq.$projectId")
                if (response.isSuccessful) Result.success(response.body() ?: emptyList())
                else Result.failure(Exception("โหลดประวัติไม่สำเร็จ (HTTP ${response.code()})"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun updateProjectFields(projectId: String, fields: Map<String, Any?>): Result<Unit> {
        return try {
            val response = retrySend(idempotent = true, tag = "updateProjectFields") { apiService.updateProject("eq.$projectId", fields) }
            if (response.isSuccessful && response.body()?.isNotEmpty() == true) {
                val returnedProject = response.body()!!.first().copy(isSynced = true)
                projectDao.insertProject(returnedProject)
                Result.success(Unit)
            } else Result.failure(Exception("API Error: ${response.code()}"))
        } catch (e: Exception) { Result.failure(e) }
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
