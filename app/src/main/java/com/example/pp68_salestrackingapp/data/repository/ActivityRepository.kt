package com.example.pp68_salestrackingapp.data.repository

import android.util.Log
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.example.pp68_salestrackingapp.data.local.*
import com.example.pp68_salestrackingapp.data.model.*
import com.example.pp68_salestrackingapp.data.remote.ApiService
import com.example.pp68_salestrackingapp.ui.viewmodels.activity.ActivityCard
import com.example.pp68_salestrackingapp.utils.AppointmentPolicy
import com.example.pp68_salestrackingapp.utils.policyFacts
import com.example.pp68_salestrackingapp.utils.AppointmentAlarmScheduler
import com.example.pp68_salestrackingapp.utils.SyncManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import java.io.IOException
import kotlinx.coroutines.CancellationException
import com.example.pp68_salestrackingapp.utils.queuedOrFailed
import com.example.pp68_salestrackingapp.utils.retrySend
import com.example.pp68_salestrackingapp.data.remote.CreateRequestPayloads

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class ActivityRepository @Inject constructor(
    private val apiService: ApiService,
    private val activityDao: ActivityDao,
    private val projectDao: ProjectDao,
    private val localIdMappingDao: LocalIdMappingDao,
    private val customerDao: CustomerDao,
    private val contactDao: ContactDao,
    private val planItemDao: ActivityPlanItemDao,
    private val resultDao: ActivityResultDao,
    private val photoDao: ActivityResultPhotoDao,
    private val appointmentContactDao: AppointmentContactDao,
    private val projectRepo: ProjectRepository,
    private val syncManager: SyncManager,
    private val networkMonitor: com.example.pp68_salestrackingapp.utils.NetworkMonitor,
    @ApplicationContext private val context: android.content.Context,
    // กติกานัดหมายทั้งชุดขึ้นกับ "ตอนนี้กี่โมง" จึงรับนาฬิกาเข้ามา ไม่เรียก now() เองในนี้
    private val clock: java.time.Clock,
    private val serverTimeAnchor: com.example.pp68_salestrackingapp.utils.ServerTimeAnchor
) {
    fun getAllActivitiesFlow(): Flow<List<SalesActivity>> = activityDao.getAllActivities()

    fun getActivitiesByProjectFlow(projectId: String): Flow<List<SalesActivity>> =
        resolvedIdFlow(LocalIdMapping.ENTITY_PROJECT, projectId).flatMapLatest { resolvedId ->
            activityDao.getActivitiesByProject(resolvedId).map { list ->
                list.map { enrichActivity(it) }
            }
        }

    fun getAllResultIdsFlow(): Flow<List<String>> = resultDao.getAllResultIdsFlow()
    fun getAllResultsFlow(): Flow<List<ActivityResult>> = resultDao.getAllResultsFlow()
    fun getResultsByProjectFlow(projectId: String): Flow<List<ActivityResult>> =
        resolvedIdFlow(LocalIdMapping.ENTITY_PROJECT, projectId)
            .flatMapLatest(resultDao::getAllResultsByProject)

    fun getResultVersionHistory(resultGroupId: String): Flow<List<ActivityResult>> =
        resolvedIdFlow(LocalIdMapping.ENTITY_RESULT, resultGroupId)
            .flatMapLatest(resultDao::getVersionHistory)

    fun getResultVersionHistoryPaging(resultGroupId: String): Flow<PagingData<ActivityResult>> =
        resolvedIdFlow(LocalIdMapping.ENTITY_RESULT, resultGroupId).flatMapLatest { resolvedId ->
            Pager(
                config = PagingConfig(
                    pageSize = 20,
                    initialLoadSize = 40,
                    prefetchDistance = 5,
                    maxSize = 100,
                    enablePlaceholders = false
                ),
                pagingSourceFactory = { resultDao.getVersionHistoryPaging(resolvedId) }
            ).flow
        }

    private fun resolvedIdFlow(entityType: String, id: String): Flow<String> =
        if (id.startsWith("TEMP-")) {
            localIdMappingDao.observeRealId(entityType, id).map { it ?: id }
        } else {
            kotlinx.coroutines.flow.flowOf(id)
        }

    fun getActivityCardsForMonthFlow(
        userId: String,
        startDate: String,
        endDateExclusive: String
    ): Flow<List<ActivityCard>> = activityDao
        .getActivityCardsForMonth(userId, startDate, endDateExclusive)
        .map { rows ->
            rows.map { row ->
                ActivityCard(
                    activityId = row.activityId,
                    activityType = row.activityType,
                    projectName = row.projectName,
                    companyName = row.companyName,
                    contactName = row.contactName,
                    objective = row.objective,
                    planStatus = row.planStatus,
                    plannedDate = row.plannedDate,
                    plannedTime = row.plannedTime,
                    plannedEndTime = row.plannedEndTime,
                    weeklyNote = row.weeklyNote,
                    customerId = row.customerId,
                    hasResult = row.hasResult,
                    checkInTime = row.checkInTime,
                    isLocationVerified = row.isLocationVerified,
                    plannedLat = row.plannedLat,
                    plannedLong = row.plannedLong,
                    locationName = row.locationName
                )
            }
        }

    suspend fun refreshActivities(userId: String): kotlin.Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val cleanUserId = userId.removePrefix("eq.")
                val resp = apiService.getMyAppointments(cleanUserId)
                if (resp.isSuccessful && resp.body() != null) {
                    val activities = resp.body()!!.map { it.copy(isSynced = true) }
                    val unsyncedContacts = appointmentContactDao.getAll()
                        .filter { it.appointmentId.startsWith("TEMP-") }
                    activityDao.clearAndInsert(activities)
                    // Restore offline (TEMP) contacts
                    if (unsyncedContacts.isNotEmpty())
                        appointmentContactDao.insertAppointmentContacts(unsyncedContacts)
                    // Fetch all contacts from server in one call
                    if (activities.isNotEmpty()) {
                        var contactFailure: Throwable? = null
                        val ids = activities.map { it.activityId }
                        val chunks = ids.chunked(50)
                        for (chunk in chunks) {
                            try {
                                val cr = apiService.getAppointmentContacts("in.(${chunk.joinToString(",")})")
                                // ✅ "สำเร็จแต่ไม่มีผู้เข้าร่วม" คือคำตอบที่ถูกต้อง ไม่ใช่ความล้มเหลว
                                // เดิมนับลิสต์ว่างเป็น error แล้วบันทึกเป็น "HTTP 200" ทำให้การรีเฟรช
                                // ทั้งก้อนล้มทั้งที่ server ตอบปกติ — นัดที่ยังไม่ใส่ผู้เข้าร่วมเป็นเรื่องปกติ
                                if (!cr.isSuccessful) {
                                    contactFailure = contactFailure ?: Exception("HTTP ${cr.code()}")
                                } else {
                                    cr.body()?.takeIf { it.isNotEmpty() }
                                        ?.let { appointmentContactDao.insertAppointmentContacts(it) }
                                }
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                contactFailure = contactFailure ?: e
                            }
                        }
                        contactFailure?.let { return@withContext kotlin.Result.failure(it) }
                    }
                    kotlin.Result.success(Unit)
                } else {
                    kotlin.Result.failure(Exception("API error: ${resp.code()}"))
                }
            } catch (e: IOException) {
                kotlin.Result.failure(e)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                kotlin.Result.failure(e)
            }
        }
    }

    suspend fun refreshResults(userId: String): kotlin.Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val cleanUserId = userId.removePrefix("eq.")
                val resp = apiService.getResultsByUser(cleanUserId)
                if (resp.isSuccessful && resp.body() != null) {
                    val results = resp.body()!!.map { it.copy(isSynced = true) }
                    resultDao.clearAndInsert(results)
                    // ✅ clearAndInsert ลบ+สร้างแถว activity_result ใหม่ ซึ่ง cascade ลบ activity_result_photo ที่ผูกอยู่ไปด้วย
                    // ต้องดึงรูปกลับมาจาก server ใหม่ทุกครั้งหลัง sync ไม่งั้นจะเหลือแค่รูปปก (photo_url บน activity_result เอง)
                    if (results.isNotEmpty()) {
                        var photoFailure: Throwable? = null
                        val ids = results.map { it.resultId }
                        val chunks = ids.chunked(50)
                        for (chunk in chunks) {
                            try {
                                val pr = apiService.getResultPhotos("in.(${chunk.joinToString(",")})", limit = chunk.size * 5)
                                // เหตุผลเดียวกับผู้เข้าร่วม — ผลการขายที่ไม่มีรูปแนบเป็นเรื่องปกติ
                                if (!pr.isSuccessful) {
                                    photoFailure = photoFailure ?: Exception("HTTP ${pr.code()}")
                                } else {
                                    pr.body()?.takeIf { it.isNotEmpty() }?.let { photoDao.insertPhotos(it) }
                                }
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                photoFailure = photoFailure ?: e
                            }
                        }
                        photoFailure?.let { return@withContext kotlin.Result.failure(it) }
                    }
                    kotlin.Result.success(Unit)
                } else {
                    kotlin.Result.failure(Exception("API error: ${resp.code()}"))
                }
            } catch (e: IOException) {
                kotlin.Result.failure(e)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                kotlin.Result.failure(e)
            }
        }
    }

    suspend fun addActivity(activity: SalesActivity): kotlin.Result<String> {
        return withContext(Dispatchers.IO) {
            val tempId = "TEMP-${java.util.UUID.randomUUID().toString().take(8).uppercase()}"
            val now = java.time.Instant.now().toString()
            val preparedActivity = activity.copy(
                activityId = tempId,
                isSynced = false,
                createdAt = activity.createdAt ?: now,
                operationId = activity.operationId ?: java.util.UUID.randomUUID().toString()
            )
            val localActivity: SalesActivity
            try {
                localActivity = localIdMappingDao.insertActivityResolvingProject(preparedActivity)
            } catch (e: Exception) {
                Log.e("ActivityRepository", "addActivity: เขียนลงเครื่องไม่สำเร็จ", e)
                return@withContext kotlin.Result.failure(Exception("สร้างนัดหมายไม่สำเร็จ: ${e.message}"))
            }
            // A TEMP parent is an expected dependency, not a failed appointment. Keep the local
            // save successful and let the worker upload the project before this appointment.
            if (localActivity.projectId?.startsWith("TEMP-") == true) {
                syncManager.scheduleSync()
                return@withContext kotlin.Result.success(tempId)
            }
            try {
                val body = CreateRequestPayloads.activity(localActivity)
                val response = retrySend(idempotent = false, tag = "addActivity") {
                    apiService.addActivityMap(body, localActivity.operationId)
                }
                if (response.isSuccessful) {
                    val realId = response.body()?.firstOrNull()?.activityId
                    if (realId != null && realId != tempId) {
                        localIdMappingDao.replaceTemporaryActivity(
                            tempId,
                            localActivity.copy(activityId = realId, isSynced = true)
                        )
                        kotlin.Result.success(realId)
                    } else {
                        // ✅ ห้าม mark synced ถ้าไม่ได้ realId กลับมา (server ไม่คืนแถวที่สร้าง เช่น RLS บล็อก)
                        // ไม่งั้นแถวนี้จะค้างเป็น TEMP- ตลอดไปแต่ถูกมองว่า sync แล้ว ทำให้บันทึกที่ผูกกับนัดหมายนี้ insert ไม่ได้ (FK violation)
                        syncManager.scheduleSync()
                        networkMonitor.queuedOrFailed(tempId, "เซิร์ฟเวอร์ไม่คืนรหัสนัดหมาย")
                    }
                } else if (response.code() == 403) {
                    syncManager.markBlocked("activity", tempId)
                    kotlin.Result.failure(Exception("สร้างนัดหมายไม่สำเร็จ: ไม่มีสิทธิ์ทำรายการนี้"))
                } else {
                    syncManager.scheduleSync()
                    networkMonitor.queuedOrFailed(tempId, "เซิร์ฟเวอร์ตอบ ${response.code()}")
                }
            } catch (e: IOException) {
                syncManager.scheduleSync()
                networkMonitor.queuedOrFailed(tempId, e.message)
            } catch (e: Exception) {
                kotlin.Result.failure(e)
            }
        }
    }

    /**
     * @param isPlanEdit true = การแก้แผนนัดหมายโดยผู้ใช้ จึงต้องเคารพกติกาล็อกแก้ไข (W6)
     *   false สำหรับการอัปเดตที่เกิดจากการบันทึกผล (เช่นผูกโครงการเข้ากับนัด) — นัดที่ขาดไป
     *   ต้องบันทึกย้อนหลังได้เสมอ การล็อกตรงนี้จะทำให้บันทึกผลนัดที่ขาดไม่ได้เลย
     */
    suspend fun updateActivity(
        activityId: String,
        // Any? ไม่ใช่ Any — ผู้เรียกต้องส่ง null ได้เพื่อ "ล้างค่าฟิลด์นี้" (เช่น ถอดโครงการออกจาก
        // นัดหมาย) ตัว apply ด้านล่างกับ backend เช็คด้วย containsKey แล้วยอมรับ null อยู่แล้ว
        updates: Map<String, Any?>,
        isPlanEdit: Boolean = true
    ): kotlin.Result<Unit> {
        return withContext(Dispatchers.IO) {
            val resolvedActivityId = try {
                localIdMappingDao.resolveExistingId(LocalIdMapping.ENTITY_ACTIVITY, activityId)
            } catch (e: Exception) {
                return@withContext kotlin.Result.failure(e)
            }
            val resolvedUpdates = updates.toMutableMap().apply {
                (this["project_code"] as? String)?.let {
                    this["project_code"] = localIdMappingDao.resolveMappedId(LocalIdMapping.ENTITY_PROJECT, it)
                }
                (this["cust_code"] as? String)?.let {
                    this["cust_code"] = localIdMappingDao.resolveMappedId(LocalIdMapping.ENTITY_CUSTOMER, it)
                }
            }
            // กติกาเดียวกับที่ UI ใช้ซ่อนปุ่มดินสอ — entry point อื่นที่เรียก repository ตรง ๆ จึงข้ามไม่ได้
            // กันกรณีเปิดฟอร์มค้างไว้ข้ามเวลานัดแล้วค่อยกดบันทึก ซึ่งตอนเปิดยังแก้ได้อยู่
            var planEditAt: String? = null
            var planEditTrusted = false
            if (isPlanEdit) {
                val existing = activityDao.getActivityById(resolvedActivityId)
                val decision = existing?.let { AppointmentPolicy.canEdit(it.policyFacts(), clock) }
                if (decision is AppointmentPolicy.Decision.Denied) {
                    return@withContext kotlin.Result.failure(Exception(decision.message))
                }
                // B.4: ประทับเวลาที่ "กดแก้" ไว้ตั้งแต่ตอนนี้ ไม่ใช่ตอนที่ส่งขึ้นได้สำเร็จ
                // แก้ตอนออฟไลน์ก่อนเวลานัดแล้วค่อยส่งทีหลัง server จะได้ตัดสินจากเวลาที่แก้จริง
                val trustedNow = serverTimeAnchor.nowOrNull()
                planEditTrusted = trustedNow != null
                planEditAt = (trustedNow ?: java.time.Instant.now(clock)).toString()
                resolvedUpdates["client_modified_at"] = planEditAt
                resolvedUpdates["client_time_trusted"] = planEditTrusted
            }
            // ── ชั้นในเครื่อง: ต้องสำเร็จก่อน ถ้าพังต้องคืน failure ─────────────────────────
            // เดิม catch ครอบทั้งฟังก์ชันแล้วคืน success(Unit) เสมอ ทำให้ cast พลาด (as String
            // ด้านล่างไม่มีอะไรการันตีชนิด) หรือ Room พังตรงนี้ กลายเป็น "บันทึกสำเร็จ" ทั้งที่
            // ไม่มีอะไรถูกเขียนลงเครื่องเลย และ outbox ก็ไม่มีแถวอะไรให้ตามส่ง = หายถาวรแบบเงียบ
            var hasPendingParent = false
            try {
                activityDao.getActivityById(resolvedActivityId)?.let { local ->
                    var updated = local.copy(
                        isSynced = false,
                        planEditAt = planEditAt ?: local.planEditAt,
                        planEditTimeTrusted = if (planEditAt != null) planEditTrusted else local.planEditTimeTrusted
                    )
                    if (resolvedUpdates.containsKey("plan_status"))    updated = updated.copy(status        = resolvedUpdates["plan_status"] as String)
                    if (resolvedUpdates.containsKey("note"))           updated = updated.copy(weeklyNote    = resolvedUpdates["note"] as? String)
                    if (resolvedUpdates.containsKey("topic"))          updated = updated.copy(detail        = resolvedUpdates["topic"] as? String)
                    if (resolvedUpdates.containsKey("type"))           updated = updated.copy(activityType  = resolvedUpdates["type"] as String)
                    if (resolvedUpdates.containsKey("planned_date"))   updated = updated.copy(activityDate  = resolvedUpdates["planned_date"] as String)
                    if (resolvedUpdates.containsKey("planned_time"))   updated = updated.copy(plannedTime   = resolvedUpdates["planned_time"] as? String)
                    if (resolvedUpdates.containsKey("planned_end_time")) updated = updated.copy(plannedEndTime = resolvedUpdates["planned_end_time"] as? String)
                    if (resolvedUpdates.containsKey("planned_lat"))    updated = updated.copy(plannedLat    = resolvedUpdates["planned_lat"] as? Double)
                    if (resolvedUpdates.containsKey("planned_long"))   updated = updated.copy(plannedLong   = resolvedUpdates["planned_long"] as? Double)
                    if (resolvedUpdates.containsKey("is_appointment")) updated = updated.copy(isAppointment = resolvedUpdates["is_appointment"] as Boolean)
                    if (resolvedUpdates.containsKey("project_code"))  updated = updated.copy(projectId  = resolvedUpdates["project_code"] as? String)
                    if (resolvedUpdates.containsKey("cust_code"))     updated = updated.copy(customerId = (resolvedUpdates["cust_code"] as? String) ?: updated.customerId)
                    val persisted = localIdMappingDao.insertActivityResolvingProject(updated)
                    hasPendingParent = persisted.projectId?.startsWith("TEMP-") == true ||
                        persisted.customerId?.startsWith("TEMP-") == true
                }
            } catch (e: Exception) {
                Log.e("ActivityRepository", "updateActivity: เขียนลงเครื่องไม่สำเร็จ $activityId", e)
                return@withContext kotlin.Result.failure(
                    Exception("บันทึกการแก้ไขนัดหมายไม่สำเร็จ: ${e.message}")
                )
            }

            if (resolvedActivityId.startsWith("TEMP-") || hasPendingParent) {
                syncManager.scheduleSync()
                return@withContext kotlin.Result.success(Unit)
            }

            // ── ชั้น server: จากจุดนี้ข้อมูลอยู่ในเครื่องแล้วและ is_synced = false ──────────
            // ส่งไม่ขึ้นจึงไม่ใช่การสูญหาย outbox ตามส่งให้เอง ยกเว้น 403 ที่ปฏิเสธถาวร
            try {
                val response = retrySend(idempotent = true, tag = "updateActivity") { apiService.updateActivity("eq.$resolvedActivityId", resolvedUpdates) }
                when {
                    response.isSuccessful && response.body()?.isNotEmpty() == true -> {
                        activityDao.updateSyncStatus(resolvedActivityId, true)
                        kotlin.Result.success(Unit)
                    }
                    response.code() == 403 -> {
                        syncManager.markBlocked("activity", resolvedActivityId)
                        kotlin.Result.failure(Exception("แก้ไขนัดหมายไม่สำเร็จ: ไม่มีสิทธิ์ทำรายการนี้"))
                    }
                    else -> {
                        syncManager.scheduleSync()
                        networkMonitor.queuedOrFailed(Unit, "เซิร์ฟเวอร์ตอบ ${response.code()}")
                    }
                }
            } catch (e: Exception) {
                Log.w("ActivityRepository", "updateActivity: ส่งขึ้น server ไม่สำเร็จ $activityId — ${e.message}")
                syncManager.scheduleSync()
                networkMonitor.queuedOrFailed(Unit, e.message)
            }
        }
    }

    suspend fun savePlanItems(appointmentId: String, items: List<ActivityPlanItem>) {
        withContext(Dispatchers.IO) {
            // เก็บลงเครื่องแบบยังไม่ซิงค์ไว้ก่อน แล้วค่อยปลดธงเมื่อส่งขึ้น server สำเร็จ
            // ถ้าพลาด outbox จะเห็นและลองใหม่ให้ — เดิมกลืน error เงียบ ๆ แล้วติ๊กหายถาวร
            val (resolvedAppointmentId, resolvedItems) = localIdMappingDao.replacePlanItemsResolvingAppointment(
                appointmentId,
                items.map { it.copy(isSynced = false) }
            )
            if (resolvedAppointmentId.startsWith("TEMP-")) {
                // นัดหมายยังไม่มี id จริง ต้องรอให้มันซิงค์ก่อน checklist ถึงจะผูกถูกแถว
                syncManager.scheduleSync()
                return@withContext
            }
            val pushed = pushChecklist(resolvedAppointmentId, resolvedItems)
            if (pushed) {
                planItemDao.updateSyncStatusByAppointment(resolvedAppointmentId, true)
            } else {
                syncManager.scheduleSync()
            }
        }
    }

    /** คืน true เมื่อ server รับชุด checklist นี้ครบแล้วเท่านั้น */
    internal suspend fun pushChecklist(appointmentId: String, items: List<ActivityPlanItem>): Boolean {
        return try {
            val deleted = apiService.deleteChecklistByAppointment("eq.$appointmentId")
            if (!deleted.isSuccessful) return false
            if (items.isEmpty()) return true
            val dtos = items.map {
                ChecklistInsertDto(appointmentId = appointmentId, masterId = it.masterId, isDone = it.isDone, actName = it.actName)
            }
            apiService.insertChecklist(dtos).isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getPlanItems(activityId: String): kotlin.Result<List<PlanItemDto>> {
        return withContext(Dispatchers.IO) {
            try {
                val resolvedId = localIdMappingDao.resolveMappedId(LocalIdMapping.ENTITY_ACTIVITY, activityId)
                val localItems = planItemDao.getPlanItemsByAppointmentId(resolvedId)
                if (localItems.isNotEmpty()) {
                    val dtos = localItems.map { PlanItemDto(masterId = it.masterId, masterDetails = MasterActDto(it.actName ?: ""), isDone = it.isDone) }
                    return@withContext kotlin.Result.success(dtos)
                }
                if (resolvedId.startsWith("TEMP-")) return@withContext kotlin.Result.success(emptyList())
                val checklistResp = apiService.getChecklistByAppointment("eq.$resolvedId")
                if (checklistResp.isSuccessful && !checklistResp.body().isNullOrEmpty()) {
                    val checklist = checklistResp.body()!!
                    val masterResp = apiService.getMasterActivities()
                    val masters = if (masterResp.isSuccessful) masterResp.body() ?: emptyList() else emptyList()
                    val dtos = checklist.map { item ->
                        val master = masters.find { it.masterId == item.masterId }
                        PlanItemDto(masterId = item.masterId, masterDetails = MasterActDto(item.actName ?: master?.actName ?: "Activity ${item.masterId}"), isDone = item.isDone)
                    }
                    val planItems = dtos.map { dto -> ActivityPlanItem(appointmentId = resolvedId, masterId = dto.masterId, actName = dto.masterDetails?.actName, isDone = dto.isDone) }
                    planItemDao.insertPlanItems(planItems)
                    kotlin.Result.success(dtos)
                } else {
                    kotlin.Result.success(emptyList())
                }
            } catch (e: Exception) {
                kotlin.Result.failure(e)
            }
        }
    }

    suspend fun updatePlanItemStatus(activityId: String, masterId: Int, isDone: Boolean) {
        withContext(Dispatchers.IO) {
            val resolvedId = localIdMappingDao.resolveExistingId(LocalIdMapping.ENTITY_ACTIVITY, activityId)
            planItemDao.updateItemStatus(resolvedId, masterId, isDone)
        }
    }

    suspend fun updateChecklistItem(appointmentId: String, masterId: Int, isDone: Boolean) {
        withContext(Dispatchers.IO) {
            val resolvedId = localIdMappingDao.resolveExistingId(LocalIdMapping.ENTITY_ACTIVITY, appointmentId)
            planItemDao.updateItemStatus(resolvedId, masterId, isDone)
            if (resolvedId.startsWith("TEMP-")) {
                planItemDao.markItemUnsynced(resolvedId, masterId)
                syncManager.scheduleSync()
                return@withContext
            }
            val landed = try {
                val updates = mapOf<String, Any>("is_checked" to isDone)
                apiService.updateChecklist(appointmentId = "eq.$resolvedId", masterId = "eq.$masterId", updates = updates)
                    .isSuccessful
            } catch (e: Exception) {
                false
            }
            // ปักธงไว้ให้ outbox เก็บไปส่งใหม่ — เดิมกลืน error แล้วติ๊กนั้นหายจาก server ถาวร
            if (!landed) {
                planItemDao.markItemUnsynced(resolvedId, masterId)
                syncManager.scheduleSync()
            }
        }
    }

    suspend fun getActivityById(id: String): kotlin.Result<List<SalesActivity>> {
        return withContext(Dispatchers.IO) {
            val resolvedId = localIdMappingDao.resolveMappedId(LocalIdMapping.ENTITY_ACTIVITY, id)
            try {
                val local = activityDao.getActivityById(resolvedId)
                if (local != null) return@withContext kotlin.Result.success(listOf(enrichActivity(local)))
                if (resolvedId.startsWith("TEMP-")) return@withContext kotlin.Result.success(emptyList())
                val resp = apiService.getAppointmentById("eq.$resolvedId")
                if (resp.isSuccessful && resp.body() != null) {
                    val data = resp.body()!!.map { it.copy(isSynced = true) }
                    if (data.isNotEmpty()) activityDao.insertActivities(data)
                    kotlin.Result.success(data.map { enrichActivity(it) })
                } else kotlin.Result.success(emptyList())
            } catch (e: Exception) {
                val local = activityDao.getActivityById(resolvedId)
                if (local != null) kotlin.Result.success(listOf(enrichActivity(local))) else kotlin.Result.failure(e)
            }
        }
    }

    suspend fun checkIn(activityId: String, lat: Double, lng: Double, isVerified: Boolean, distanceDeviation: Double? = null): kotlin.Result<Unit> {
        return withContext(Dispatchers.IO) {
            val resolvedId = try {
                localIdMappingDao.resolveExistingId(LocalIdMapping.ENTITY_ACTIVITY, activityId)
            } catch (e: Exception) {
                return@withContext kotlin.Result.failure(e)
            }
            val nowStr = java.time.Instant.now().toString()
            val updates = mutableMapOf<String, Any>("check_in_lat" to lat, "check_in_long" to lng, "check_in_time" to nowStr, "plan_status" to "checked_in", "is_location_verified" to isVerified)
            distanceDeviation?.let { updates["distance_deviation"] = it }

            // ── ชั้นในเครื่อง: เขียนก่อนยิง API เสมอ ด้วย is_synced = false ─────────────────
            // เดิมเขียนหลังยิง API แล้วเขียนซ้ำอีกชุดใน catch ทำให้ถ้าเขียนพัง (หรือแถวหาย) จะคืน
            // success(Unit) ทั้งที่การเช็คอินไม่ได้ถูกบันทึกที่ไหนเลย ผู้ใช้เห็นว่าเช็คอินแล้วแต่ไม่มีข้อมูล
            try {
                // ✅ W6 เดิมเช็คแค่ชั้น UI (CheckInScreen's navigation guard) — entry point อื่นที่
                // เรียก repository ตรงๆ เช็คอินซ้ำ/เช็คอินนัดที่ขาดนัดไปแล้วได้เลย ย้ายมาเช็คที่นี่แทน
                val existing = activityDao.getActivityById(resolvedId)
                    ?: return@withContext kotlin.Result.failure(Exception("ไม่พบนัดหมายนี้ในเครื่อง"))
                if (AppointmentPolicy.effectiveStatus(existing.policyFacts(), clock) != "planned") {
                    return@withContext kotlin.Result.failure(Exception("นัดหมายนี้เช็คอินไม่ได้แล้ว (เช็คอินไปแล้ว/ขาดนัด/เสร็จสิ้นแล้ว)"))
                }
                activityDao.insertActivity(existing.copy(status = "checked_in", checkInLat = lat, checkInLong = lng, checkInTime = nowStr, isLocationVerified = isVerified, distanceDeviation = distanceDeviation, isSynced = false))
            } catch (e: Exception) {
                Log.e("ActivityRepository", "checkIn: เขียนลงเครื่องไม่สำเร็จ $activityId", e)
                return@withContext kotlin.Result.failure(Exception("บันทึกเช็คอินไม่สำเร็จ: ${e.message}"))
            }

            if (resolvedId.startsWith("TEMP-")) {
                syncManager.scheduleSync()
                return@withContext kotlin.Result.success(Unit)
            }

            // ── ชั้น server ──────────────────────────────────────────────────────────────
            // HTTP error ไม่โยน exception — ถ้าไม่ตรวจผลแล้วปัก is_synced = true ไว้เลย
            // outbox จะข้ามแถวนี้ตลอดไป แล้วการเช็คอินจะหายจาก server อย่างถาวร
            try {
                val response = retrySend(idempotent = true, tag = "updateActivity") { apiService.updateActivity("eq.$resolvedId", updates) }
                when {
                    response.isSuccessful && response.body()?.isNotEmpty() == true -> {
                        activityDao.updateSyncStatus(resolvedId, true)
                        kotlin.Result.success(Unit)
                    }
                    response.code() == 403 -> {
                        // ❌ server ปฏิเสธถาวร (ไม่ใช่เคส "ไม่ตรวจผล" ที่คอมเมนต์ข้างบนพูดถึง — ตรงนั้นคือ
                        // ตอบ 2xx แต่ body ว่าง) ต้องบอกผู้ใช้ตรง ๆ ว่าเช็คอินไม่สำเร็จ ไม่ใช่เงียบไว้แล้วลองซ้ำ
                        syncManager.markBlocked("activity", resolvedId)
                        // ✅ ViewModel เติม "เช็คอินไม่สำเร็จ: " นำหน้าเองแล้ว (ActivityDetailViewModel.confirmCheckin)
                        // ข้อความตรงนี้จึงมีแค่เหตุผล ไม่งั้นจะซ้ำเป็น "เช็คอินไม่สำเร็จ: เช็คอินไม่สำเร็จ: ..."
                        kotlin.Result.failure(Exception("ไม่มีสิทธิ์ทำรายการนี้"))
                    }
                    else -> {
                        syncManager.scheduleSync()
                        networkMonitor.queuedOrFailed(Unit, "เซิร์ฟเวอร์ตอบ ${response.code()}")
                    }
                }
            } catch (e: Exception) {
                Log.w("ActivityRepository", "checkIn: ส่งขึ้น server ไม่สำเร็จ $activityId — ${e.message}")
                syncManager.scheduleSync()
                networkMonitor.queuedOrFailed(Unit, e.message)
            }
        }
    }

    suspend fun finishActivity(activityId: String, doneMasterIds: List<Int>, note: String?): kotlin.Result<Unit> {
        return withContext(Dispatchers.IO) {
            val resolvedId = try {
                localIdMappingDao.resolveExistingId(LocalIdMapping.ENTITY_ACTIVITY, activityId)
            } catch (e: Exception) {
                return@withContext kotlin.Result.failure(e)
            }
            // ── ชั้นในเครื่อง: ต้องเขียนทั้ง checklist และสถานะให้ครบก่อน ────────────────────
            // เดิมถ้า planItemDao พังกลางทาง catch จะเขียนแค่สถานะ (และเขียน weeklyNote แต่ลืม note)
            // แล้วคืน success(Unit) — ติ๊ก checklist หายเงียบ ๆ ทั้งที่ผู้ใช้เห็นว่าบันทึกสำเร็จ
            try {
                val currentItems = planItemDao.getPlanItemsByAppointmentId(resolvedId)
                planItemDao.insertPlanItems(currentItems.map { it.copy(isDone = it.masterId in doneMasterIds) })
                val local = activityDao.getActivityById(resolvedId)
                    ?: return@withContext kotlin.Result.failure(Exception("ไม่พบนัดหมายนี้ในเครื่อง"))
                activityDao.insertActivity(local.copy(status = "completed", note = note, weeklyNote = note, isSynced = false))
            } catch (e: Exception) {
                Log.e("ActivityRepository", "finishActivity: เขียนลงเครื่องไม่สำเร็จ $activityId", e)
                return@withContext kotlin.Result.failure(Exception("บันทึกสถานะเสร็จสิ้นไม่สำเร็จ: ${e.message}"))
            }

            if (resolvedId.startsWith("TEMP-")) {
                syncManager.scheduleSync()
                return@withContext kotlin.Result.success(Unit)
            }

            // ── ชั้น server ──────────────────────────────────────────────────────────────
            // ✅ ผู้เรียกปัจจุบัน (SalesResultViewModel.save()) ไม่ได้เช็ค Result ตัวนี้อยู่แล้ว
            // (fire-and-forget ต่อท้ายหลังบันทึกผลสำเร็จ) แต่ยังต้อง markBlocked ไว้กัน outbox
            // ลองส่งซ้ำเงียบ ๆ ตลอดไปเหมือนจุดอื่น — คืน failure ไว้เผื่อผู้เรียกในอนาคตเช็ค
            val updates = mutableMapOf<String, Any>("plan_status" to "completed")
            note?.let { updates["note"] = it }
            try {
                val response = retrySend(idempotent = true, tag = "updateActivity") { apiService.updateActivity("eq.$resolvedId", updates) }
                when {
                    response.isSuccessful && response.body()?.isNotEmpty() == true -> {
                        activityDao.updateSyncStatus(resolvedId, true)
                        kotlin.Result.success(Unit)
                    }
                    response.code() == 403 -> {
                        syncManager.markBlocked("activity", resolvedId)
                        kotlin.Result.failure(Exception("บันทึกสถานะเสร็จสิ้นไม่สำเร็จ: ไม่มีสิทธิ์ทำรายการนี้"))
                    }
                    else -> {
                        syncManager.scheduleSync()
                        networkMonitor.queuedOrFailed(Unit, "เซิร์ฟเวอร์ตอบ ${response.code()}")
                    }
                }
            } catch (e: Exception) {
                Log.w("ActivityRepository", "finishActivity: ส่งขึ้น server ไม่สำเร็จ $activityId — ${e.message}")
                syncManager.scheduleSync()
                networkMonitor.queuedOrFailed(Unit, e.message)
            }
        }
    }

    suspend fun getMyActivitiesWithDetails(): kotlin.Result<List<ActivityCard>> {
        return withContext(Dispatchers.IO) {
            try {
                val activities = activityDao.getAllActivities().first()
                val projects = projectDao.getAllProjects().first().associateBy { it.projectId }
                val customers = customerDao.getAllCustomers().first().associateBy { it.custId }
                val cards = activities.map { activity ->
                    val project = activity.projectId?.let { projects[it] }
                    val customer = activity.customerId?.let { customers[it] }
                    ActivityCard(activityId = activity.activityId, activityType = activity.activityType, projectName = project?.projectName ?: activity.projectName, companyName = customer?.companyName ?: activity.companyName, contactName = activity.contactName, objective = activity.detail, planStatus = activity.status, plannedDate = activity.activityDate, plannedTime = activity.plannedTime, plannedEndTime = activity.plannedEndTime, weeklyNote = activity.weeklyNote ?: activity.note, customerId = activity.customerId, checkInTime = activity.checkInTime, isLocationVerified = activity.isLocationVerified, plannedLat = activity.plannedLat, plannedLong = activity.plannedLong, locationName = activity.locationName)
                }
                kotlin.Result.success(cards)
            } catch (e: Exception) {
                kotlin.Result.failure(e)
            }
        }
    }

    suspend fun getMasterActivities(): List<ActivityMaster> {
        return withContext(Dispatchers.IO) {
            try {
                val resp = apiService.getMasterActivities()
                if (resp.isSuccessful && resp.body() != null) {
                    resp.body()!!.map { ActivityMaster(masterId = it.masterId, category = it.category, actName = it.actName) }
                } else emptyList()
            } catch (e: Exception) { emptyList() }
        }
    }

    // ✅ cancelAlarm มีมาตั้งแต่แรกแต่ไม่มีใครเรียกใช้เลย — แยก try/catch ของตัวเองเพราะยกเลิก alarm
    // พลาดไม่ควรทำให้ผลลัพธ์การลบนัดหมาย (ที่สำเร็จไปแล้วจริง) กลายเป็น failure ไปด้วย
    private fun cancelAlarmSafely(activityId: String) {
        try { AppointmentAlarmScheduler(context).cancelAlarm(activityId) } catch (_: Exception) {}
    }

    // ✅ activity_plan_item / appointment_contact ไม่มี FK CASCADE ฝั่ง Room (คนละแบบกับ
    // activity_result_photo) ลบนัดหมายแล้วแถวลูกจึงค้างเป็นขยะกำพร้า และถ้าแถว checklist นั้นยัง
    // is_synced = 0 อยู่ จะบล็อก logout ถาวรทั้งที่เน็ตดี เพราะ:
    //   - ถ้า appointmentId ยังเป็น TEMP- : doSync ข้ามทุกรอบ ไม่เคยพยายามส่งเลย
    //   - ถ้าเป็น id จริง : ส่งขึ้นไปแล้ว server ตอบ 404 (นัดหมายถูกลบไปแล้ว) ไม่สำเร็จตลอดกาล
    // ต้องเก็บกวาดพร้อมกับการลบนัดหมายเสมอ
    private suspend fun deleteChildRowsOf(activityId: String) {
        try { planItemDao.deletePlanItemsByAppointmentId(activityId) } catch (_: Exception) {}
        try { appointmentContactDao.deleteContactsByAppointmentId(activityId) } catch (_: Exception) {}
    }

    suspend fun deleteActivity(activityId: String): kotlin.Result<Unit> {
        return withContext(Dispatchers.IO) {
            var resolvedId = activityId
            try {
                resolvedId = localIdMappingDao.resolveExistingId(LocalIdMapping.ENTITY_ACTIVITY, activityId)
                // ✅ W6 เดิมเช็คแค่ชั้น UI (HomeScreen.canDelete) — entry point อื่นที่เรียก
                // repository ตรงๆ ข้ามกฎห้ามลบไปได้เลย จุดนี้คือจุดบล็อกจริงที่ทุกทางต้องผ่าน
                // ตัดสินด้วย policy ตัวเดียวกับที่ UI ใช้ซ่อนปุ่ม เหตุผลมาจากที่เดียวกัน
                val existing = activityDao.getActivityById(resolvedId)
                val decision = existing?.let { AppointmentPolicy.canDelete(it.policyFacts(), clock) }
                if (decision is AppointmentPolicy.Decision.Denied) {
                    return@withContext kotlin.Result.failure(Exception(decision.message))
                }
                if (resolvedId.startsWith("TEMP-")) {
                    activityDao.deleteActivityById(resolvedId)
                    deleteChildRowsOf(resolvedId)
                    cancelAlarmSafely(resolvedId)
                    return@withContext kotlin.Result.success(Unit)
                }
                val response = apiService.deleteActivity("eq.$resolvedId")
                if (response.isSuccessful) {
                    activityDao.deleteActivityById(resolvedId)
                    deleteChildRowsOf(resolvedId)
                    cancelAlarmSafely(resolvedId)
                    if (activityId != resolvedId) cancelAlarmSafely(activityId)
                    kotlin.Result.success(Unit)
                } else {
                    kotlin.Result.failure(Exception("ลบนัดหมายบนเซิร์ฟเวอร์ไม่สำเร็จ"))
                }
            } catch (e: Exception) {
                if (resolvedId.startsWith("TEMP-")) {
                    activityDao.deleteActivityById(resolvedId)
                    deleteChildRowsOf(resolvedId)
                    cancelAlarmSafely(resolvedId)
                    kotlin.Result.success(Unit)
                } else {
                    // หากออฟไลน์ ห้ามลบข้อมูลที่ซิงค์แล้วในเครื่อง ไม่งั้นจะเป็น Zombie Data (ดึงกลับมาใหม่เมื่อออนไลน์)
                    kotlin.Result.failure(Exception("ไม่สามารถลบนัดหมายที่ซิงค์แล้วขณะออฟไลน์ได้"))
                }
            }
        }
    }

    suspend fun getActivityResult(activityId: String): ActivityResult? {
        return withContext(Dispatchers.IO) {
            val resolvedActivityId = localIdMappingDao.resolveMappedId(LocalIdMapping.ENTITY_ACTIVITY, activityId)
            try {
                if (resolvedActivityId.startsWith("TEMP-")) {
                    return@withContext resultDao.getResultByActivityId(resolvedActivityId)
                }
                val resp = apiService.getActivityResult("eq.$resolvedActivityId")
                if (resp.isSuccessful && !resp.body().isNullOrEmpty()) {
                    val result = resp.body()!!.first().copy(isSynced = true)
                    // ✅ insertResult ใช้ OnConflictStrategy.REPLACE — ถ้า result_id นี้มีอยู่แล้วในเครื่อง
                    // SQLite จะลบแถวเดิมทิ้งก่อนแล้วค่อย insert ใหม่ ซึ่ง cascade ลบ activity_result_photo ที่ผูกอยู่ไปด้วย
                    // ต้องดึงรูปกลับมาจาก server ใหม่ทุกครั้งหลังจากนี้ ไม่งั้นจะเหลือแค่รูปปก
                    resultDao.insertResult(result)
                    refreshPhotosForResult(result.resultId)
                    return@withContext result
                }
                resultDao.getResultByActivityId(resolvedActivityId)
            } catch (e: Exception) { resultDao.getResultByActivityId(resolvedActivityId) }
        }
    }

    private suspend fun refreshPhotosForResult(resultId: String) {
        try {
            val pr = apiService.getResultPhotos("eq.$resultId")
            if (pr.isSuccessful && !pr.body().isNullOrEmpty()) photoDao.insertPhotos(pr.body()!!)
        } catch (_: Exception) {}
    }

    suspend fun getResultById(resultId: String): ActivityResult? {
        return withContext(Dispatchers.IO) {
            val resolvedId = localIdMappingDao.resolveMappedId(LocalIdMapping.ENTITY_RESULT, resultId)
            try {
                val local = resultDao.getResultById(resolvedId)
                if (local != null) return@withContext local
                if (resolvedId.startsWith("TEMP-")) return@withContext null
                val resp = apiService.getResultById("eq.$resolvedId")
                if (resp.isSuccessful && !resp.body().isNullOrEmpty()) {
                    val result = resp.body()!!.first().copy(isSynced = true)
                    resultDao.insertResult(result)
                    return@withContext result
                }
                null
            } catch (e: Exception) { null }
        }
    }

    suspend fun saveActivityResult(result: ActivityResult, photos: List<ResultPhotoInput> = emptyList()): kotlin.Result<Unit> {
        return withContext(Dispatchers.IO) {
            saveResultAsNewVersion(result, photos)
        }
    }

    suspend fun saveStandaloneResult(projectId: String, result: ActivityResult, photos: List<ResultPhotoInput> = emptyList()): kotlin.Result<Unit> {
        return withContext(Dispatchers.IO) {
            val resultWithProject = result.copy(projectId = projectId, activityId = null)
            saveResultAsNewVersion(resultWithProject, photos)
        }
    }

    // ✅ ทุกครั้งที่บันทึก (ทั้งครั้งแรกและแก้ไข) จะสร้างแถวใหม่เป็น version ถัดไปเสมอ
    // แทนการเขียนทับของเดิม — เพื่อรักษาประวัติการแก้ไขบันทึกผลการขายไว้ทั้งหมด
    private suspend fun saveResultAsNewVersion(result: ActivityResult, photos: List<ResultPhotoInput> = emptyList()): kotlin.Result<Unit> {
        val previous = result.resultId.takeIf { it.isNotBlank() }?.let { resultDao.getResultById(it) }
        val tempId = "TEMP-${java.util.UUID.randomUUID().toString().take(8).uppercase()}"
        val groupId = previous?.resultGroupId ?: previous?.resultId ?: tempId
        val localResult = result.copy(
            resultId      = tempId,
            isSynced      = false,
            version       = (previous?.version ?: 0) + 1,
            isLatest      = true,
            resultGroupId = groupId,
            operationId   = result.operationId ?: java.util.UUID.randomUUID().toString()
        )
        val now = java.time.Instant.now().toString()
        val remotePhotos = photos.mapNotNull { input ->
            input.remoteUrl?.let { ActivityResultPhoto(tempId, input.photoOrder, it) }
        }
        val ownerId = localResult.createdBy
        if (photos.any { it.staged != null } && ownerId.isNullOrBlank()) {
            return kotlin.Result.failure(Exception("ไม่พบเจ้าของรูปที่รอซิงค์ กรุณาเข้าสู่ระบบใหม่"))
        }
        val attachments = photos.mapNotNull { input ->
            input.staged?.let { staged ->
                AttachmentOutbox(
                    operationId = staged.operationId,
                    ownerId = ownerId!!,
                    resultId = tempId,
                    photoOrder = input.photoOrder,
                    localPath = staged.localPath,
                    mimeType = staged.mimeType,
                    sha256 = staged.sha256,
                    sizeBytes = staged.sizeBytes,
                    createdAt = now,
                    updatedAt = now
                )
            }
        }
        // เขียน result, URL เดิม และคิวไฟล์ใหม่ใน transaction เดียวกัน
        // exception จะหลุดออกไปถึง ViewModel เป็น crash แทนที่จะเป็น failure ที่แสดงให้ผู้ใช้เห็นได้
        val persistedResult: ActivityResult
        try {
            persistedResult = localIdMappingDao.insertResultWithAttachmentsResolvingParents(
                previous?.resultId,
                localResult,
                remotePhotos,
                attachments
            )
        } catch (e: Exception) {
            Log.e("ActivityRepository", "saveResultAsNewVersion: เขียนลงเครื่องไม่สำเร็จ", e)
            return kotlin.Result.failure(Exception("บันทึกผลการขายไม่สำเร็จ: ${e.message}"))
        }
        if (persistedResult.activityId?.startsWith("TEMP-") == true ||
            persistedResult.projectId?.startsWith("TEMP-") == true
        ) {
            syncManager.scheduleSync()
            return kotlin.Result.success(Unit)
        }
        return try {
            val body = CreateRequestPayloads.result(persistedResult, includeResultId = false)
            val apiResp = retrySend(idempotent = false, tag = "saveResult") {
                apiService.insertActivityResultMap(body, persistedResult.operationId)
            }
            if (apiResp.isSuccessful) {
                val serverRow = apiResp.body()?.firstOrNull()
                val realId = serverRow?.resultId
                if (realId.isNullOrBlank() || realId == tempId) {
                    // HTTP 2xx อย่างเดียวไม่พอ: รูปใน outbox ต้องอ้าง result id จริงจาก server
                    // ถ้า mark TEMP- ว่า synced ที่นี่ attachment จะถูกข้ามตลอดและค้างถาวร
                    syncManager.scheduleSync()
                    return networkMonitor.queuedOrFailed(
                        Unit,
                        "เซิร์ฟเวอร์ไม่คืนรหัสบันทึกผลที่ใช้งานได้"
                    )
                }

                // trigger generate_result_id ฝั่ง server เป็นเจ้าของ version กับ result_group_id แล้ว
                // เพราะค่าที่คำนวณจาก Room ในเครื่องจะผิดทันทีถ้าเครื่องนั้นมองประวัติไม่ครบ
                // จึงต้องเอาค่าที่ server คืนมาเท่านั้น ไม่งั้นเครื่องกับ server จะต่างกัน
                val acceptedRow = requireNotNull(serverRow)
                val finalGroupId = acceptedRow.resultGroupId ?: previous?.resultGroupId ?: previous?.resultId ?: realId
                // ✅ ต้อง insert แถว realId ก่อน แล้วค่อยย้ายรูปมาที่ realId แล้วค่อยลบ tempId ทีหลัง
                // เพราะ activity_result_photo มี FK CASCADE ไปยัง activity_result — ถ้าลบ tempId ก่อน รูปที่ยังผูกกับ tempId จะโดนลบไปด้วย
                localIdMappingDao.replaceTemporaryResult(tempId, persistedResult.copy(
                    resultId = realId,
                    version = acceptedRow.version,
                    resultGroupId = finalGroupId,
                    isSynced = true,
                    operationId = persistedResult.operationId
                ))
                if (remotePhotos.isNotEmpty()) {
                    try { apiService.addResultPhotos(photoDao.getPhotosByResultId(realId)) } catch (_: Exception) {}
                }
                // ✅ mark version เก่าบน server ว่าไม่ใช่ล่าสุดแล้ว (best-effort เหมือนจุดอื่นในไฟล์นี้)
                previous?.let {
                    val flipped = try {
                        apiService.updateActivityResult("eq.${it.resultId}", mapOf("is_latest" to false)).isSuccessful
                    } catch (_: Exception) { false }
                    // ถ้า flip ไม่สำเร็จ server จะมี 2 แถว is_latest=1 พร้อมกัน — แถวนี้ isSynced=true
                    // อยู่แล้วตั้งแต่ก่อนหน้า จะไม่มีวันถูกหยิบไป retry เอง ต้อง mark unsynced ให้
                    // SyncManager ส่ง upsertActivityResult (มี is_latest ปัจจุบัน = false ใน Room แล้ว) ซ้ำจนสำเร็จ
                    if (!flipped) {
                        resultDao.updateSyncStatus(it.resultId, false)
                        syncManager.scheduleSync()
                    }
                }
                syncProjectStatus(persistedResult)
                if (attachments.isNotEmpty()) syncManager.scheduleSync()
                kotlin.Result.success(Unit)
            } else if (apiResp.code() == 403) {
                syncManager.markBlocked("result", tempId)
                kotlin.Result.failure(Exception("บันทึกผลการขายไม่สำเร็จ: ไม่มีสิทธิ์ทำรายการนี้"))
            } else {
                syncManager.scheduleSync()
                networkMonitor.queuedOrFailed(Unit, "เซิร์ฟเวอร์ตอบ ${apiResp.code()}")
            }
        } catch (e: Exception) {
            // ของอยู่ในเครื่องแล้วและ is_synced = false — ถ้าไม่มีเน็ต outbox ตามส่งให้ ไม่ใช่การสูญหาย
            // แต่ถ้าเน็ตดีอยู่แล้วยังส่งไม่ขึ้น ต้องบอกผู้ใช้ ไม่ใช่ปล่อยให้เข้าใจว่าบันทึกครบแล้ว
            Log.w("ActivityRepository", "saveResultAsNewVersion: ส่งขึ้น server ไม่สำเร็จ — ${e.message}")
            syncManager.scheduleSync()
            networkMonitor.queuedOrFailed(Unit, e.message)
        }
    }

    suspend fun getResultPhotos(resultId: String): List<String> {
        return withContext(Dispatchers.IO) {
            val resolvedId = localIdMappingDao.resolveMappedId(LocalIdMapping.ENTITY_RESULT, resultId)
            photoDao.getPhotosByResultId(resolvedId).map { it.photoUrl }
        }
    }

    suspend fun getResultPhotoEntries(resultId: String): List<ActivityResultPhoto> =
        withContext(Dispatchers.IO) {
            val resolvedId = localIdMappingDao.resolveMappedId(LocalIdMapping.ENTITY_RESULT, resultId)
            photoDao.getPhotosByResultId(resolvedId)
        }

    suspend fun getPendingResultAttachments(resultId: String): List<AttachmentOutbox> =
        withContext(Dispatchers.IO) {
            val resolvedId = localIdMappingDao.resolveMappedId(LocalIdMapping.ENTITY_RESULT, resultId)
            resultDao.getAttachmentOutboxByResultId(resolvedId)
        }

    // ✅ ให้ export รายงานดึงรูปของหลาย result ทีเดียวแทนที่จะ query ทีละตัวต่อ activity/result
    // (เดิม O(N) query ต่อการ export หนึ่งครั้ง ตอนนี้เหลือ 1 query)
    suspend fun getResultPhotosBatch(resultIds: List<String>): Map<String, List<String>> {
        return withContext(Dispatchers.IO) {
            if (resultIds.isEmpty()) return@withContext emptyMap()
            photoDao.getPhotosByResultIds(resultIds).groupBy({ it.resultId }, { it.photoUrl })
        }
    }

    // เก็บชื่อสถานที่ที่ reverse geocode มาแล้วไว้ใน Room — เป็น local-only field
    // ไม่ต้องตั้ง is_synced = false เพราะไม่ได้ส่งขึ้น server (ไม่มี @SerializedName)
    suspend fun cacheLocationName(activityId: String, locationName: String) {
        withContext(Dispatchers.IO) {
            val resolvedId = localIdMappingDao.resolveMappedId(LocalIdMapping.ENTITY_ACTIVITY, activityId)
            activityDao.updateLocationName(resolvedId, locationName)
        }
    }

    private suspend fun syncProjectStatus(result: ActivityResult) {
        val pid = result.projectId ?: result.activityId?.let { activityDao.getActivityById(it)?.projectId } ?: return
        val newStatus = result.newStatus ?: return
        try {
            val project = projectDao.getProjectById(pid)
            if (project != null && project.projectStatus != newStatus) {
                val updated = project.copy(projectStatus = newStatus, lossReason = result.lossReason, lossReasonNote = result.lossReasonNote)
                // result.activityId is the appointment this result came from — null for standalone results
                projectRepo.updateProject(updated, resultAppointmentId = result.activityId)
            }
        } catch (e: Exception) { Log.e("ActivityRepository", "Update Project Status Failed: ${e.message}") }
    }

    suspend fun enrichActivity(activity: SalesActivity): SalesActivity {
        return withContext(Dispatchers.IO) {
            try {
                val customer = activity.customerId?.let { customerDao.getCustomerById(it) }
                val companyName = customer?.companyName ?: activity.companyName
                val project = activity.projectId?.let { projectDao.getProjectById(it) }
                val projectName = project?.projectName ?: activity.projectName
                val contactIds = appointmentContactDao.getContactsByAppointmentId(activity.activityId).map { it.contactId }.toSet()
                val namesString = if (contactIds.isNotEmpty()) {
                    contactIds.mapNotNull { id -> contactDao.getContactById(id) }
                        .joinToString(", ") { it.fullName ?: it.nickname ?: "Unknown" }
                        .takeIf { it.isNotBlank() }
                } else null
                activity.copy(companyName = companyName, projectName = projectName, contactName = namesString ?: activity.contactName)
            } catch (e: Exception) { activity }
        }
    }

    suspend fun getActivitiesByProjectId(projectId: String): List<SalesActivity> {
        return withContext(Dispatchers.IO) {
            val resolvedId = localIdMappingDao.resolveMappedId(LocalIdMapping.ENTITY_PROJECT, projectId)
            try { activityDao.getActivitiesByProject(resolvedId).first().map { enrichActivity(it) } }
            catch (e: Exception) { emptyList() }
        }
    }

    suspend fun saveAppointmentContacts(appointmentId: String, contactIds: List<String>) {
        withContext(Dispatchers.IO) {
            val (resolvedAppointmentId, items) = localIdMappingDao.replaceAppointmentContactsResolvingIds(
                appointmentId,
                contactIds
            )
            // ✅ ต้องยิง delete เสมอแม้ items ว่างเปล่า (ลบผู้เข้าร่วมออกหมด) ไม่งั้น server จะเหลือ
            // รายชื่อเดิมค้างอยู่ตลอดไปเพราะ if (items.isNotEmpty()) เดิมครอบ delete ไว้ด้วย
            if (!resolvedAppointmentId.startsWith("TEMP-") && items.none { it.contactId.startsWith("TEMP-") }) {
                try {
                    // ✅ ต้องดูผลด้วย — เซิร์ฟเวอร์ตอบ 4xx/5xx ไม่ได้โยน exception ออกมา จึงตกไปที่
                    // "ถือว่าสำเร็จ" เงียบ ๆ ทั้งที่รายชื่อไม่เคยขึ้นไปถึง (เดิมจับแค่ IOException
                    // ซึ่งครอบแค่กรณีเน็ตหลุด) ปักธงให้ outbox ตามส่งด้วยกลไกเดียวกับตอนออฟไลน์
                    val deleted = apiService.deleteAppointmentContacts("eq.$resolvedAppointmentId")
                    val added = if (items.isNotEmpty()) apiService.addAppointmentContacts(items) else null
                    if (!deleted.isSuccessful || added?.isSuccessful == false) {
                        activityDao.updateSyncStatus(resolvedAppointmentId, false)
                        syncManager.scheduleSync()
                    }
                } catch (_: IOException) {
                    // ✅ Room เก็บรายชื่อใหม่ไว้แล้ว แต่ appointment_contact ไม่มี is_synced ของตัวเอง
                    // และ outbox วนเฉพาะนัดหมายที่ is_synced = 0 — ถ้าไม่ปักธงตรงนี้ การแก้ผู้เข้าร่วม
                    // ตอนออฟไลน์จะไม่มีวันถูกอัปขึ้น server เลย แล้วหายถาวรตอน login รอบหน้าที่ล้าง DB
                    // (saveProjectContacts กันไว้แบบเดียวกันอยู่แล้ว ฝั่งนัดหมายเดิมตกไป)
                    activityDao.updateSyncStatus(resolvedAppointmentId, false)
                    syncManager.scheduleSync()
                }
            } else {
                activityDao.updateSyncStatus(resolvedAppointmentId, false)
                syncManager.scheduleSync()
            }
        }
    }

    suspend fun getAppointmentContacts(appointmentId: String): List<String> {
        return withContext(Dispatchers.IO) {
            val resolvedId = localIdMappingDao.resolveMappedId(LocalIdMapping.ENTITY_ACTIVITY, appointmentId)
            appointmentContactDao.getContactsByAppointmentId(resolvedId).map { it.contactId }
        }
    }
}
