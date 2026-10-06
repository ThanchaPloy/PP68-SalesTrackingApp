package com.example.pp68_salestrackingapp.data.repository

import com.example.pp68_salestrackingapp.data.local.ContactDao
import com.example.pp68_salestrackingapp.data.local.ProjectContactDao
import com.example.pp68_salestrackingapp.data.local.ProjectDao
import com.example.pp68_salestrackingapp.data.local.LocalIdMappingDao
import com.example.pp68_salestrackingapp.data.model.LocalIdMapping
import com.example.pp68_salestrackingapp.data.model.ContactPerson
import com.example.pp68_salestrackingapp.data.model.Project
import com.example.pp68_salestrackingapp.data.model.ProjectContact
import com.example.pp68_salestrackingapp.data.model.ProjectFactorLog
import com.example.pp68_salestrackingapp.data.remote.ApiService
import com.example.pp68_salestrackingapp.utils.SyncManager
import android.util.Log
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlinx.coroutines.CancellationException
import java.time.LocalDate
import javax.inject.Inject
import com.example.pp68_salestrackingapp.utils.queuedOrFailed
import com.example.pp68_salestrackingapp.utils.retrySend
import com.example.pp68_salestrackingapp.utils.ProjectStages
import com.example.pp68_salestrackingapp.data.remote.CreateRequestPayloads

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectRepository @Inject constructor(
    private val apiService: ApiService,
    private val projectDao: ProjectDao,
    private val localIdMappingDao: LocalIdMappingDao,
    private val projectContactDao: ProjectContactDao,
    private val contactDao: ContactDao,
    private val syncManager: SyncManager,
    private val networkMonitor: com.example.pp68_salestrackingapp.utils.NetworkMonitor
) {
    fun getAllProjectsFlow(): Flow<List<Project>> = projectDao.getAllProjects()
    fun searchProjectsFlow(query: String): Flow<List<Project>> =
        projectDao.searchProjects("%$query%")

    /**
     * Paged query for the main project list only. Other callers such as export intentionally keep
     * using [getAllProjectsFlow] because they require a complete dataset.
     */
    /**
     * รายการโครงการของผู้ใช้คนนี้เท่านั้น
     *
     * ตาราง project ในเครื่องมีของทั้งสาขา เพราะ delta sync ดึงตามขอบเขตที่ผู้ใช้มีสิทธิ์เห็น
     * (resolveScope ฝั่ง server คิดจากสาขา ไม่ใช่รายบุคคล) ถ้าไม่กรองตรงนี้ หน้ารายการจะ
     * โชว์โครงการของเพื่อนร่วมสาขาด้วย ซึ่งเคยถูกบังไว้ด้วยผลข้างเคียงของ refreshProjects
     * ที่ลบแถวของคนอื่นทิ้งทุกครั้ง — พอออฟไลน์แล้ว refresh ล้ม ของคนอื่นจึงโผล่หมด
     *
     * กรองเข้ม: แถวที่ create_by เป็น null จะไม่แสดงกับใครเลย ต่างจาก CallerScope.allows
     * ฝั่ง server ที่ปล่อยแถวไร้เจ้าของผ่าน เลือกแบบนี้ตามที่ตกลงกันว่าแถวเก่าที่ไม่มีเจ้าของ
     * จะถูกเติม create_by ให้ครบในฐานข้อมูลแยกต่างหาก ไม่ใช่ปล่อยให้โผล่กับทุกคนไปก่อน
     *
     * โครงการที่สร้างตอนออฟไลน์ไม่หายไปด้วยกติกานี้ เพราะฟอร์มตั้ง createBy เป็นผู้ใช้ปัจจุบัน
     * ตั้งแต่ก่อนเขียนลง Room และ POST /project ฝั่ง server ก็บังคับ create_by จาก JWT อยู่แล้ว
     */
    fun getProjectsPagingFlow(
        ownerId: String,
        searchQuery: String,
        tabIndex: Int,
        selectedStatuses: Set<String>,
        selectedScores: Set<String>
    ): Flow<PagingData<Project>> = Pager(
        config = PagingConfig(
            pageSize = PROJECT_PAGE_SIZE,
            initialLoadSize = PROJECT_INITIAL_LOAD_SIZE,
            prefetchDistance = PROJECT_PREFETCH_DISTANCE,
            maxSize = PROJECT_MAX_LOADED_ROWS,
            enablePlaceholders = false
        ),
        pagingSourceFactory = {
            projectDao.getProjectsPaging(
                ownerId = ownerId,
                searchQuery = searchQuery.trim(),
                tabIndex = tabIndex,
                closedStatuses = ProjectStages.CLOSED.sorted(),
                wonStatuses = ProjectStages.WON.sorted(),
                lostStatuses = ProjectStages.LOST.sorted(),
                applyStatusFilter = selectedStatuses.isNotEmpty(),
                selectedStatuses = selectedStatuses.sorted(),
                applyScoreFilter = selectedScores.isNotEmpty(),
                selectedScores = selectedScores.map { it.uppercase() }.sorted()
            )
        }
    ).flow

    fun getProjectByIdFlow(projectId: String): Flow<Project?> =
        if (projectId.startsWith("TEMP-")) {
            localIdMappingDao.observeRealId(LocalIdMapping.ENTITY_PROJECT, projectId)
                .flatMapLatest { projectDao.getProjectByIdFlow(it ?: projectId) }
        } else {
            projectDao.getProjectByIdFlow(projectId)
        }

    private companion object {
        const val PROJECT_PAGE_SIZE = 30
        const val PROJECT_INITIAL_LOAD_SIZE = 60
        const val PROJECT_PREFETCH_DISTANCE = 10
        const val PROJECT_MAX_LOADED_ROWS = 150
    }

    suspend fun refreshProjects(userId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val cleanUserId = userId.removePrefix("eq.")
                // ✅ 1 โครงการมีเจ้าของคนเดียว (create_by) — ไม่มีตาราง membership แยกแล้ว
                val creatorResp = apiService.getProjectsByCreator(userId = cleanUserId)
                if (!creatorResp.isSuccessful || creatorResp.body() == null) {
                    return@withContext Result.failure(Exception("HTTP ${creatorResp.code()}"))
                }
                val creatorProjects = creatorResp.body()!!

                // ponytail: never clear local cache on empty — missing records would silently wipe all local data
                if (creatorProjects.isEmpty()) return@withContext Result.success(Unit)

                val merged = creatorProjects.distinctBy { it.projectId }.map { it.copy(isSynced = true) }
                projectDao.clearAndInsert(merged)
                Result.success(Unit)
            } catch (e: IOException) {
                Result.failure(e) // local cache ยังใช้ได้ แต่ download รอบนี้ไม่สำเร็จ
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Result.failure(e)
            }
        }
    }

    suspend fun createProject(project: Project, userId: String): Result<Project> {
        return withContext(Dispatchers.IO) {
            val today = java.time.LocalDate.now().toString()
            val tempId = "TEMP-${java.util.UUID.randomUUID().toString().take(8).uppercase()}"
            val tempProject = project.copy(
                projectId = tempId,
                isSynced = false,
                createdAt = project.createdAt ?: today,
                operationId = project.operationId ?: java.util.UUID.randomUUID().toString()
            )
            val localProject = localIdMappingDao.insertProjectResolvingCustomer(tempProject)
            if (localProject.custId?.startsWith("TEMP-") == true) {
                syncManager.scheduleSync()
                return@withContext Result.success(localProject)
            }
            try {
                val body = CreateRequestPayloads.project(localProject)
                // ไม่ log ตัว body — มันมีชื่อลูกค้า มูลค่าโครงการ วันที่ปิดดีล ครบชุด และ
                // minifyEnabled = false จึงไม่มี ProGuard มาตัด Log.d ออกตอน build release
                // แปลว่าข้อมูลลูกค้าจริงถูกพิมพ์ลง logcat ของเครื่องผู้ใช้ทุกครั้งที่สร้างโครงการ
                Log.d("ProjectRepo", "POST project (${body.size} fields)")
                val response = retrySend(idempotent = false, tag = "createProject") {
                    apiService.addProject(body, localProject.operationId)
                }
                Log.d("ProjectRepo", "POST project → HTTP ${response.code()}")
                if (response.isSuccessful) {
                    val realId = response.body()?.firstOrNull()?.projectId
                    Log.d("ProjectRepo", "realId=$realId tempId=$tempId")
                    val finalProject = if (realId != null && realId != tempId) {
                        val returnedProject = response.body()?.first()
                        val real = (returnedProject ?: localProject.copy(projectId = realId)).copy(
                            isSynced = true,
                            operationId = localProject.operationId
                        )
                        localIdMappingDao.replaceTemporaryProject(tempId, real)
                        real
                    } else {
                        val returnedProject = response.body()?.firstOrNull()
                        if (returnedProject != null) {
                            projectDao.insertProject(returnedProject.copy(
                                isSynced = true,
                                operationId = localProject.operationId
                            ))
                        } else {
                            projectDao.updateSyncStatus(tempId, true)
                        }
                        returnedProject?.copy(isSynced = true) ?: localProject
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
                networkMonitor.queuedOrFailed(localProject, e.message)
            } catch (e: Exception) { Result.failure(e) }
        }
    }

    // resultAppointmentId: appointment_id จริงของผลการขายที่ทำให้ project_status เปลี่ยน (null สำหรับ
    // standalone หรือแก้โครงการตรงๆ) — backend ใช้ค่านี้บันทึก project_stage_log.appointment_id ตรงๆ
    // ไม่มีการเดาจากวันที่ใกล้เคียงฝั่ง backend แล้ว เพราะนัดหมายใกล้วันที่สุดอาจไม่ใช่ของโครงการนี้เลย
    suspend fun updateProject(project: Project, resultAppointmentId: String? = null): kotlin.Result<Unit> {
        return withContext(Dispatchers.IO) {
            val resolvedProjectId = try {
                localIdMappingDao.resolveExistingId(LocalIdMapping.ENTITY_PROJECT, project.projectId)
            } catch (e: Exception) {
                return@withContext kotlin.Result.failure(e)
            }
            val localProject = try {
                localIdMappingDao.insertProjectResolvingCustomer(
                    project.copy(projectId = resolvedProjectId, isSynced = false)
                )
            } catch (e: Exception) {
                return@withContext kotlin.Result.failure(e)
            }
            val resolvedAppointmentId = resultAppointmentId?.let {
                localIdMappingDao.resolveMappedId(LocalIdMapping.ENTITY_ACTIVITY, it)
            }
            if (resolvedProjectId.startsWith("TEMP-") || localProject.custId?.startsWith("TEMP-") == true) {
                syncManager.scheduleSync()
                return@withContext kotlin.Result.success(Unit)
            }
            try {
                val updates = mutableMapOf<String, Any?>(
                    "customer_code" to localProject.custId,
                    "customer_name" to localProject.customerName,
                    "project_name" to localProject.projectName,
                    "project_status" to localProject.projectStatus,
                    "expected_value" to localProject.expectedValue,
                    "branch_code" to localProject.branchId,
                    "billing_branch_id" to localProject.billingBranchId,
                    "opportunity_score" to localProject.opportunityScore,
                    "loss_reason" to localProject.lossReason,
                    "loss_reason_note" to localProject.lossReasonNote,
                    "deal_position" to localProject.dealPosition,
                    "current_solution" to localProject.previousSolution,
                    "counterparty_type" to localProject.counterpartyType,
                    "response_speed" to localProject.responseSpeed,
                    "is_proposal_sent" to localProject.isProposalSent,
                    "proposal_date" to localProject.proposalDate,
                    "competitor_count" to localProject.competitorCount,
                    "start_date" to localProject.startDate,
                    "closing_date" to localProject.closingDate,
                    "progress_pct" to localProject.progressPct,
                    "updated_at" to java.time.Instant.now().toString()
                ).filterValues { it != null }.toMutableMap()
                // ✅ filterValues ข้างบนจำเป็น — มันกันฟิลด์ที่ผู้เรียกไม่ได้ดูแล (progress_pct,
                // is_proposal_sent, competitor_count ฯลฯ) ไม่ให้ถูกล้างทิ้งโดยไม่ตั้งใจ
                // แต่ loss_reason ต้องยกเว้น เพราะ "ค่าว่าง" คือความหมายจริงที่ต้องส่ง: ทั้งสองผู้เรียก
                // (ฟอร์มแก้โครงการ และ syncProjectStatus ตอนสถานะออกจาก Lost/Failed) ตั้งใจให้ล้าง
                // ถ้าปล่อยให้ถูกกรองทิ้ง โครงการที่กลับมาเดินต่อจะยังติดเหตุผลที่ไม่ได้งานค้างบน server
                // แล้วแถวที่ตอบกลับมาก็เขียนทับ Room ให้ค่าเก่าเด้งกลับมาให้ผู้ใช้เห็นว่า "แก้ไม่ติด"
                updates["loss_reason"] = localProject.lossReason
                updates["loss_reason_note"] = localProject.lossReasonNote
                // ✅ สามช่องนี้ฟอร์มแก้โครงการเป็นเจ้าของเต็มและลบทิ้งได้จริง (AddProjectViewModel
                // ส่ง toDoubleOrNull() / ifBlank { null }) ถ้าปล่อยให้ filterValues ตัดทิ้งตอนเป็น null
                // = ลบมูลค่าหรือวันที่แล้วบันทึก ค่าเก่ายังอยู่บน server แล้ว refresh ดึงกลับมาทับ
                // ส่ง "" แทน null เพราะ Gson ไม่ได้เปิด serializeNulls — backend แปลง "" เป็น NULL ให้
                // (ผู้เรียกอีกทางคือ syncProjectStatus ซึ่งส่งแถวเต็มจาก Room จึงไม่กระทบ)
                updates["expected_value"] = localProject.expectedValue?.toString() ?: ""
                updates["start_date"] = localProject.startDate.orEmpty()
                updates["closing_date"] = localProject.closingDate.orEmpty()
                localProject.projectLat?.let { updates["project_lat"] = it }
                localProject.projectLong?.let { updates["project_long"] = it }
                resolvedAppointmentId?.takeUnless { it.startsWith("TEMP-") }
                    ?.let { updates["stage_appointment_id"] = it }

                val response = retrySend(idempotent = true, tag = "updateProject") { apiService.updateProject("eq.$resolvedProjectId", updates) }
                if (response.isSuccessful && response.body()?.isNotEmpty() == true) {
                    val returnedProject = response.body()!!.first().copy(isSynced = true)
                    projectDao.insertProject(returnedProject)
                    kotlin.Result.success(Unit)
                } else if (response.code() == 403) {
                    syncManager.markBlocked("project", resolvedProjectId)
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
                val resolvedId = localIdMappingDao.resolveExistingId(LocalIdMapping.ENTITY_PROJECT, projectId)
                if (resolvedId.startsWith("TEMP-")) {
                    projectDao.deleteProjectById(resolvedId)
                    // โครงการที่ยังไม่เคยขึ้น server ก็มีผู้ติดต่อผูกไว้ในเครื่องได้เหมือนกัน
                    projectContactDao.deleteByProject(resolvedId)
                    return@withContext Result.success(Unit)
                }
                // ✅ ต้องเช็คผลของขั้นแรกก่อนไปขั้นสอง (เหมือนที่ deleteCustomer ทำ) — เดิมยิงลบ
                // ผู้ติดต่อทิ้งแล้วไม่ดูผลเลย ถ้าลบตัวโครงการต่อไม่สำเร็จ (เช่น 403) จะเหลือโครงการ
                // ที่ผู้ติดต่อถูกลบไปแล้วบน server โดยผู้ใช้ไม่รู้ว่าเสียอะไรไป
                val contactsResp = apiService.deleteProjectContacts("eq.$resolvedId")
                if (!contactsResp.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("ลบผู้ติดต่อของโครงการไม่สำเร็จ (HTTP ${contactsResp.code()}) จึงยังไม่ลบโครงการ")
                    )
                }
                val response = apiService.deleteProject("eq.$resolvedId")
                if (response.isSuccessful) {
                    projectDao.deleteProjectById(resolvedId)
                    // Room ไม่มี FK CASCADE — ลบแถวผูกผู้ติดต่อในเครื่องเองด้วย ไม่งั้นค้างเป็นแถวกำพร้า
                    // ที่ชี้ไปหาโครงการที่ไม่มีแล้ว (และจะถูกส่งขึ้น server อีกตอน saveProjectContacts)
                    projectContactDao.deleteByProject(resolvedId)
                    Result.success(Unit)
                } else Result.failure(Exception("HTTP ${response.code()}"))
            } catch (e: Exception) { Result.failure(e) }
        }
    }

    suspend fun saveProjectContacts(projectId: String, contactIds: List<String>): Result<Unit> {
        return withContext(Dispatchers.IO) {
            Log.d("ProjectRepo", "saveProjectContacts started. projectId=$projectId, ${contactIds.size} contacts")
            // Resolve stale IDs and replace the relation set in one transaction.
            val (resolvedProjectId, rows) = localIdMappingDao.replaceProjectContactsResolvingIds(
                projectId,
                contactIds.map { it.trim() }
            )
            Log.d("ProjectRepo", "Inserted local contacts: ${rows.size} rows")
            if (resolvedProjectId.startsWith("TEMP-") || rows.any { it.contact_id.startsWith("TEMP-") }) {
                projectDao.updateSyncStatus(resolvedProjectId, false)
                syncManager.scheduleSync()
                return@withContext Result.success(Unit)
            }
            // sync API
            try {
                val delResp = apiService.deleteProjectContacts("eq.$resolvedProjectId")
                Log.d("ProjectRepo", "deleteProjectContacts API status: ${delResp.code()}")
                if (!delResp.isSuccessful) {
                    val errMsg = delResp.errorBody()?.string() ?: ""
                    return@withContext Result.failure(Exception("ลบผู้ติดต่อเก่าล้มเหลว: HTTP ${delResp.code()} $errMsg"))
                }
                if (rows.isNotEmpty()) {
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
                projectDao.updateSyncStatus(resolvedProjectId, false)
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
            val resolvedProjectId = localIdMappingDao.resolveMappedId(LocalIdMapping.ENTITY_PROJECT, projectId)
            // อัพเดท Room จาก API ก่อน (ถ้าทำได้)
            if (!resolvedProjectId.startsWith("TEMP-")) {
                try {
                    val response = apiService.getProjectContacts("eq.$resolvedProjectId")
                    if (response.isSuccessful && response.body() != null) {
                        val rows = response.body()!!.map { ProjectContact(resolvedProjectId, it.contactId) }
                        projectContactDao.deleteByProject(resolvedProjectId)
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
            val ids = projectContactDao.getContactIdsByProject(resolvedProjectId)
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
                val resolvedId = localIdMappingDao.resolveMappedId(LocalIdMapping.ENTITY_PROJECT, projectId)
                if (resolvedId.startsWith("TEMP-")) return@withContext Result.success(emptyList())
                val response = apiService.getProjectFactorLog("eq.$resolvedId")
                if (response.isSuccessful) Result.success(response.body() ?: emptyList())
                else Result.failure(Exception("โหลดประวัติไม่สำเร็จ (HTTP ${response.code()})"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun updateProjectFields(projectId: String, fields: Map<String, Any?>): Result<Unit> {
        return try {
            val resolvedId = localIdMappingDao.resolveExistingId(LocalIdMapping.ENTITY_PROJECT, projectId)
            val local = projectDao.getProjectById(resolvedId)
                ?: return Result.failure(Exception("ไม่พบโครงการนี้ในเครื่อง"))
            val updated = local.copy(
                dealPosition = fields["deal_position"] as? String ?: local.dealPosition,
                previousSolution = fields["current_solution"] as? String ?: local.previousSolution,
                counterpartyType = fields["counterparty_type"] as? String ?: local.counterpartyType,
                responseSpeed = fields["response_speed"] as? String ?: local.responseSpeed,
                isProposalSent = fields["is_proposal_sent"] as? Boolean ?: local.isProposalSent,
                proposalDate = if (fields.containsKey("proposal_date")) {
                    (fields["proposal_date"] as? String)?.ifBlank { null }
                } else local.proposalDate,
                competitorCount = (fields["competitor_count"] as? Number)?.toInt() ?: local.competitorCount,
                isSynced = false
            )
            projectDao.insertProject(updated)
            if (resolvedId.startsWith("TEMP-")) {
                syncManager.scheduleSync()
                return Result.success(Unit)
            }
            val response = retrySend(idempotent = true, tag = "updateProjectFields") { apiService.updateProject("eq.$resolvedId", fields) }
            if (response.isSuccessful && response.body()?.isNotEmpty() == true) {
                val returnedProject = response.body()!!.first().copy(isSynced = true)
                projectDao.insertProject(returnedProject)
                Result.success(Unit)
            } else if (response.code() == 403) {
                syncManager.markBlocked("project", resolvedId)
                Result.failure(Exception("แก้ไขปัจจัยโครงการไม่สำเร็จ: ไม่มีสิทธิ์ทำรายการนี้"))
            } else {
                syncManager.scheduleSync()
                networkMonitor.queuedOrFailed(Unit, "เซิร์ฟเวอร์ตอบ ${response.code()}")
            }
        } catch (e: IOException) {
            syncManager.scheduleSync()
            networkMonitor.queuedOrFailed(Unit, e.message)
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
                val resolvedId = localIdMappingDao.resolveMappedId(LocalIdMapping.ENTITY_PROJECT, projectId)
                val local = projectDao.getProjectById(resolvedId)
                if (local != null) return@withContext Result.success(local)
                val response = apiService.getProjectById("eq.$resolvedId")
                if (response.isSuccessful && !response.body().isNullOrEmpty()) {
                    val project = response.body()!!.first().copy(isSynced = true)
                    projectDao.insertProject(project)
                    Result.success(project)
                } else Result.failure(Exception("ไม่พบข้อมูล Project"))
            } catch (e: Exception) { Result.failure(e) }
        }
    }
}
