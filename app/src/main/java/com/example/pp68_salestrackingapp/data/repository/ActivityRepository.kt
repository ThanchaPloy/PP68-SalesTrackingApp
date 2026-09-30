package com.example.pp68_salestrackingapp.data.repository

import android.util.Log
import com.example.pp68_salestrackingapp.data.local.*
import com.example.pp68_salestrackingapp.data.model.*
import com.example.pp68_salestrackingapp.data.remote.ApiService
import com.example.pp68_salestrackingapp.data.remote.UploadApiService
import com.example.pp68_salestrackingapp.ui.viewmodels.activity.ActivityCard
import com.example.pp68_salestrackingapp.utils.AppointmentAlarmScheduler
import com.example.pp68_salestrackingapp.utils.SyncManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton
import java.io.IOException
import com.example.pp68_salestrackingapp.utils.queuedOrFailed
import com.example.pp68_salestrackingapp.utils.retrySend

@Singleton
class ActivityRepository @Inject constructor(
    private val apiService: ApiService,
    private val uploadApiService: UploadApiService,
    private val activityDao: ActivityDao,
    private val projectDao: ProjectDao,
    private val customerDao: CustomerDao,
    private val contactDao: ContactDao,
    private val planItemDao: ActivityPlanItemDao,
    private val resultDao: ActivityResultDao,
    private val photoDao: ActivityResultPhotoDao,
    private val appointmentContactDao: AppointmentContactDao,
    private val projectRepo: ProjectRepository,
    private val syncManager: SyncManager,
    private val networkMonitor: com.example.pp68_salestrackingapp.utils.NetworkMonitor,
    @ApplicationContext private val context: android.content.Context
) {
    fun getAllActivitiesFlow(): Flow<List<SalesActivity>> = activityDao.getAllActivities()

    fun getActivitiesByProjectFlow(projectId: String): Flow<List<SalesActivity>> =
        activityDao.getActivitiesByProject(projectId).map { list ->
            list.map { enrichActivity(it) }
        }

    fun getAllResultIdsFlow(): Flow<List<String>> = resultDao.getAllResultIdsFlow()
    fun getAllResultsFlow(): Flow<List<ActivityResult>> = resultDao.getAllResultsFlow()
    fun getResultsByProjectFlow(projectId: String): Flow<List<ActivityResult>> = resultDao.getAllResultsByProject(projectId)
    fun getResultVersionHistory(resultGroupId: String): Flow<List<ActivityResult>> = resultDao.getVersionHistory(resultGroupId)

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
                        val ids = activities.map { it.activityId }
                        val chunks = ids.chunked(50)
                        for (chunk in chunks) {
                            try {
                                val cr = apiService.getAppointmentContacts("in.(${chunk.joinToString(",")})")
                                if (cr.isSuccessful && !cr.body().isNullOrEmpty())
                                    appointmentContactDao.insertAppointmentContacts(cr.body()!!)
                            } catch (_: Exception) {}
                        }
                    }
                    kotlin.Result.success(Unit)
                } else {
                    kotlin.Result.failure(Exception("API error: ${resp.code()}"))
                }
            } catch (e: IOException) {
                kotlin.Result.success(Unit) // offline â€” Room data still valid
            } catch (e: Exception) {
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
                        val ids = results.map { it.resultId }
                        val chunks = ids.chunked(50)
                        for (chunk in chunks) {
                            try {
                                val pr = apiService.getResultPhotos("in.(${chunk.joinToString(",")})", limit = chunk.size * 5)
                                if (pr.isSuccessful && !pr.body().isNullOrEmpty()) photoDao.insertPhotos(pr.body()!!)
                            } catch (_: Exception) {}
                        }
                    }
                    kotlin.Result.success(Unit)
                } else {
                    kotlin.Result.failure(Exception("API error: ${resp.code()}"))
                }
            } catch (e: IOException) {
                kotlin.Result.success(Unit) // offline â€” Room data still valid
            } catch (e: Exception) {
                kotlin.Result.failure(e)
            }
        }
    }

    suspend fun addActivity(activity: SalesActivity): kotlin.Result<String> {
        return withContext(Dispatchers.IO) {
            val tempId = "TEMP-${java.util.UUID.randomUUID().toString().take(8).uppercase()}"
            val now = java.time.Instant.now().toString()
            val localActivity = activity.copy(activityId = tempId, isSynced = false, createdAt = activity.createdAt ?: now)
            try {
                activityDao.insertActivity(localActivity)
            } catch (e: Exception) {
                Log.e("ActivityRepository", "addActivity: เขียนลงเครื่องไม่สำเร็จ", e)
                return@withContext kotlin.Result.failure(Exception("สร้างนัดหมายไม่สำเร็จ: ${e.message}"))
            }
            try {
                val custCode = if (activity.customerId == "CST-UNKNOWN") null else activity.customerId
                val body = mutableMapOf<String, Any?>(
                    "emp_code"         to activity.userId,
                    "cust_code"        to custCode,
                    "project_code"     to activity.projectId,
                    "type"             to activity.activityType,
                    "is_appointment"   to activity.isAppointment,
                    "topic"            to activity.detail,
                    "planned_date"     to activity.activityDate,
                    "planned_time"     to activity.plannedTime,
                    "planned_end_time" to activity.plannedEndTime,
                    "planned_lat"      to activity.plannedLat,
                    "planned_long"     to activity.plannedLong,
                    "plan_status"      to activity.status,
                    "created_at"       to localActivity.createdAt
                ).filterValues { it != null }
                val response = retrySend(idempotent = false, tag = "addActivity") { apiService.addActivityMap(body) }
                if (response.isSuccessful) {
                    val realId = response.body()?.firstOrNull()?.activityId
                    if (realId != null && realId != tempId) {
                        activityDao.deleteActivityById(tempId)
                        activityDao.insertActivity(localActivity.copy(activityId = realId, isSynced = true))
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

    suspend fun updateActivity(activityId: String, updates: Map<String, Any>): kotlin.Result<Unit> {
        return withContext(Dispatchers.IO) {
            // ── ชั้นในเครื่อง: ต้องสำเร็จก่อน ถ้าพังต้องคืน failure ─────────────────────────
            // เดิม catch ครอบทั้งฟังก์ชันแล้วคืน success(Unit) เสมอ ทำให้ cast พลาด (as String
            // ด้านล่างไม่มีอะไรการันตีชนิด) หรือ Room พังตรงนี้ กลายเป็น "บันทึกสำเร็จ" ทั้งที่
            // ไม่มีอะไรถูกเขียนลงเครื่องเลย และ outbox ก็ไม่มีแถวอะไรให้ตามส่ง = หายถาวรแบบเงียบ
            try {
                activityDao.getActivityById(activityId)?.let { local ->
                    var updated = local.copy(isSynced = false)
                    if (updates.containsKey("plan_status"))    updated = updated.copy(status        = updates["plan_status"] as String)
                    if (updates.containsKey("note"))           updated = updated.copy(weeklyNote    = updates["note"] as? String)
                    if (updates.containsKey("topic"))          updated = updated.copy(detail        = updates["topic"] as? String)
                    if (updates.containsKey("type"))           updated = updated.copy(activityType  = updates["type"] as String)
                    if (updates.containsKey("planned_date"))   updated = updated.copy(activityDate  = updates["planned_date"] as String)
                    if (updates.containsKey("planned_time"))   updated = updated.copy(plannedTime   = updates["planned_time"] as? String)
                    if (updates.containsKey("planned_end_time")) updated = updated.copy(plannedEndTime = updates["planned_end_time"] as? String)
                    if (updates.containsKey("planned_lat"))    updated = updated.copy(plannedLat    = updates["planned_lat"] as? Double)
                    if (updates.containsKey("planned_long"))   updated = updated.copy(plannedLong   = updates["planned_long"] as? Double)
                    if (updates.containsKey("is_appointment")) updated = updated.copy(isAppointment = updates["is_appointment"] as Boolean)
                    if (updates.containsKey("project_code"))  updated = updated.copy(projectId  = updates["project_code"] as? String)
                    if (updates.containsKey("cust_code"))     updated = updated.copy(customerId = (updates["cust_code"] as? String) ?: updated.customerId)
                    activityDao.insertActivity(updated)
                }
            } catch (e: Exception) {
                Log.e("ActivityRepository", "updateActivity: เขียนลงเครื่องไม่สำเร็จ $activityId", e)
                return@withContext kotlin.Result.failure(
                    Exception("บันทึกการแก้ไขนัดหมายไม่สำเร็จ: ${e.message}")
                )
            }

            if (activityId.startsWith("TEMP-")) {
                syncManager.scheduleSync()
                return@withContext kotlin.Result.success(Unit)
            }

            // ── ชั้น server: จากจุดนี้ข้อมูลอยู่ในเครื่องแล้วและ is_synced = false ──────────
            // ส่งไม่ขึ้นจึงไม่ใช่การสูญหาย outbox ตามส่งให้เอง ยกเว้น 403 ที่ปฏิเสธถาวร
            try {
                val response = retrySend(idempotent = true, tag = "updateActivity") { apiService.updateActivity("eq.$activityId", updates) }
                when {
                    response.isSuccessful && response.body()?.isNotEmpty() == true -> {
                        activityDao.updateSyncStatus(activityId, true)
                        kotlin.Result.success(Unit)
                    }
                    response.code() == 403 -> {
                        syncManager.markBlocked("activity", activityId)
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
            planItemDao.deletePlanItemsByAppointmentId(appointmentId)
            // เก็บลงเครื่องแบบยังไม่ซิงค์ไว้ก่อน แล้วค่อยปลดธงเมื่อส่งขึ้น server สำเร็จ
            // ถ้าพลาด outbox จะเห็นและลองใหม่ให้ — เดิมกลืน error เงียบ ๆ แล้วติ๊กหายถาวร
            planItemDao.insertPlanItems(items.map { it.copy(isSynced = false) })
            if (appointmentId.startsWith("TEMP-")) {
                // นัดหมายยังไม่มี id จริง ต้องรอให้มันซิงค์ก่อน checklist ถึงจะผูกถูกแถว
                syncManager.scheduleSync()
                return@withContext
            }
            val pushed = pushChecklist(appointmentId, items)
            if (pushed) {
                planItemDao.updateSyncStatusByAppointment(appointmentId, true)
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
                val localItems = planItemDao.getPlanItemsByAppointmentId(activityId)
                if (localItems.isNotEmpty()) {
                    val dtos = localItems.map { PlanItemDto(masterId = it.masterId, masterDetails = MasterActDto(it.actName ?: ""), isDone = it.isDone) }
                    return@withContext kotlin.Result.success(dtos)
                }
                val checklistResp = apiService.getChecklistByAppointment("eq.$activityId")
                if (checklistResp.isSuccessful && !checklistResp.body().isNullOrEmpty()) {
                    val checklist = checklistResp.body()!!
                    val masterResp = apiService.getMasterActivities()
                    val masters = if (masterResp.isSuccessful) masterResp.body() ?: emptyList() else emptyList()
                    val dtos = checklist.map { item ->
                        val master = masters.find { it.masterId == item.masterId }
                        PlanItemDto(masterId = item.masterId, masterDetails = MasterActDto(item.actName ?: master?.actName ?: "Activity ${item.masterId}"), isDone = item.isDone)
                    }
                    val planItems = dtos.map { dto -> ActivityPlanItem(appointmentId = activityId, masterId = dto.masterId, actName = dto.masterDetails?.actName, isDone = dto.isDone) }
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
        withContext(Dispatchers.IO) { planItemDao.updateItemStatus(activityId, masterId, isDone) }
    }

    suspend fun updateChecklistItem(appointmentId: String, masterId: Int, isDone: Boolean) {
        withContext(Dispatchers.IO) {
            planItemDao.updateItemStatus(appointmentId, masterId, isDone)
            val landed = try {
                val updates = mapOf<String, Any>("is_checked" to isDone)
                apiService.updateChecklist(appointmentId = "eq.$appointmentId", masterId = "eq.$masterId", updates = updates)
                    .isSuccessful
            } catch (e: Exception) {
                false
            }
            // ปักธงไว้ให้ outbox เก็บไปส่งใหม่ — เดิมกลืน error แล้วติ๊กนั้นหายจาก server ถาวร
            if (!landed) {
                planItemDao.markItemUnsynced(appointmentId, masterId)
                syncManager.scheduleSync()
            }
        }
    }

    suspend fun getActivityById(id: String): kotlin.Result<List<SalesActivity>> {
        return withContext(Dispatchers.IO) {
            try {
                val local = activityDao.getActivityById(id)
                if (local != null) return@withContext kotlin.Result.success(listOf(enrichActivity(local)))
                val resp = apiService.getAppointmentById("eq.$id")
                if (resp.isSuccessful && resp.body() != null) {
                    val data = resp.body()!!.map { it.copy(isSynced = true) }
                    if (data.isNotEmpty()) activityDao.insertActivities(data)
                    kotlin.Result.success(data.map { enrichActivity(it) })
                } else {
                    if (local != null) kotlin.Result.success(listOf(enrichActivity(local))) else kotlin.Result.success(emptyList())
                }
            } catch (e: Exception) {
                val local = activityDao.getActivityById(id)
                if (local != null) kotlin.Result.success(listOf(enrichActivity(local))) else kotlin.Result.failure(e)
            }
        }
    }

    suspend fun checkIn(activityId: String, lat: Double, lng: Double, isVerified: Boolean, distanceDeviation: Double? = null): kotlin.Result<Unit> {
        return withContext(Dispatchers.IO) {
            val nowStr = java.time.Instant.now().toString()
            val updates = mutableMapOf<String, Any>("check_in_lat" to lat, "check_in_long" to lng, "check_in_time" to nowStr, "plan_status" to "checked_in", "is_location_verified" to isVerified)
            distanceDeviation?.let { updates["distance_deviation"] = it }

            // ── ชั้นในเครื่อง: เขียนก่อนยิง API เสมอ ด้วย is_synced = false ─────────────────
            // เดิมเขียนหลังยิง API แล้วเขียนซ้ำอีกชุดใน catch ทำให้ถ้าเขียนพัง (หรือแถวหาย) จะคืน
            // success(Unit) ทั้งที่การเช็คอินไม่ได้ถูกบันทึกที่ไหนเลย ผู้ใช้เห็นว่าเช็คอินแล้วแต่ไม่มีข้อมูล
            try {
                // ✅ W6 เดิมเช็คแค่ชั้น UI (CheckInScreen's navigation guard) — entry point อื่นที่
                // เรียก repository ตรงๆ เช็คอินซ้ำ/เช็คอินนัดที่ขาดนัดไปแล้วได้เลย ย้ายมาเช็คที่นี่แทน
                val existing = activityDao.getActivityById(activityId)
                    ?: return@withContext kotlin.Result.failure(Exception("ไม่พบนัดหมายนี้ในเครื่อง"))
                if (com.example.pp68_salestrackingapp.utils.AppointmentStatus.effective(existing.status, existing.activityDate, existing.activityType) != "planned") {
                    return@withContext kotlin.Result.failure(Exception("นัดหมายนี้เช็คอินไม่ได้แล้ว (เช็คอินไปแล้ว/ขาดนัด/เสร็จสิ้นแล้ว)"))
                }
                activityDao.insertActivity(existing.copy(status = "checked_in", checkInLat = lat, checkInLong = lng, checkInTime = nowStr, isLocationVerified = isVerified, distanceDeviation = distanceDeviation, isSynced = false))
            } catch (e: Exception) {
                Log.e("ActivityRepository", "checkIn: เขียนลงเครื่องไม่สำเร็จ $activityId", e)
                return@withContext kotlin.Result.failure(Exception("บันทึกเช็คอินไม่สำเร็จ: ${e.message}"))
            }

            // ── ชั้น server ──────────────────────────────────────────────────────────────
            // HTTP error ไม่โยน exception — ถ้าไม่ตรวจผลแล้วปัก is_synced = true ไว้เลย
            // outbox จะข้ามแถวนี้ตลอดไป แล้วการเช็คอินจะหายจาก server อย่างถาวร
            try {
                val response = retrySend(idempotent = true, tag = "updateActivity") { apiService.updateActivity("eq.$activityId", updates) }
                when {
                    response.isSuccessful && response.body()?.isNotEmpty() == true -> {
                        activityDao.updateSyncStatus(activityId, true)
                        kotlin.Result.success(Unit)
                    }
                    response.code() == 403 -> {
                        // ❌ server ปฏิเสธถาวร (ไม่ใช่เคส "ไม่ตรวจผล" ที่คอมเมนต์ข้างบนพูดถึง — ตรงนั้นคือ
                        // ตอบ 2xx แต่ body ว่าง) ต้องบอกผู้ใช้ตรง ๆ ว่าเช็คอินไม่สำเร็จ ไม่ใช่เงียบไว้แล้วลองซ้ำ
                        syncManager.markBlocked("activity", activityId)
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
            // ── ชั้นในเครื่อง: ต้องเขียนทั้ง checklist และสถานะให้ครบก่อน ────────────────────
            // เดิมถ้า planItemDao พังกลางทาง catch จะเขียนแค่สถานะ (และเขียน weeklyNote แต่ลืม note)
            // แล้วคืน success(Unit) — ติ๊ก checklist หายเงียบ ๆ ทั้งที่ผู้ใช้เห็นว่าบันทึกสำเร็จ
            try {
                val currentItems = planItemDao.getPlanItemsByAppointmentId(activityId)
                planItemDao.insertPlanItems(currentItems.map { it.copy(isDone = it.masterId in doneMasterIds) })
                val local = activityDao.getActivityById(activityId)
                    ?: return@withContext kotlin.Result.failure(Exception("ไม่พบนัดหมายนี้ในเครื่อง"))
                activityDao.insertActivity(local.copy(status = "completed", note = note, weeklyNote = note, isSynced = false))
            } catch (e: Exception) {
                Log.e("ActivityRepository", "finishActivity: เขียนลงเครื่องไม่สำเร็จ $activityId", e)
                return@withContext kotlin.Result.failure(Exception("บันทึกสถานะเสร็จสิ้นไม่สำเร็จ: ${e.message}"))
            }

            // ── ชั้น server ──────────────────────────────────────────────────────────────
            // ✅ ผู้เรียกปัจจุบัน (SalesResultViewModel.save()) ไม่ได้เช็ค Result ตัวนี้อยู่แล้ว
            // (fire-and-forget ต่อท้ายหลังบันทึกผลสำเร็จ) แต่ยังต้อง markBlocked ไว้กัน outbox
            // ลองส่งซ้ำเงียบ ๆ ตลอดไปเหมือนจุดอื่น — คืน failure ไว้เผื่อผู้เรียกในอนาคตเช็ค
            val updates = mutableMapOf<String, Any>("plan_status" to "completed")
            note?.let { updates["note"] = it }
            try {
                val response = retrySend(idempotent = true, tag = "updateActivity") { apiService.updateActivity("eq.$activityId", updates) }
                when {
                    response.isSuccessful && response.body()?.isNotEmpty() == true -> {
                        activityDao.updateSyncStatus(activityId, true)
                        kotlin.Result.success(Unit)
                    }
                    response.code() == 403 -> {
                        syncManager.markBlocked("activity", activityId)
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
            try {
                // ✅ W6 เดิมเช็คแค่ชั้น UI (HomeScreen.canDelete) — entry point อื่นที่เรียก
                // repository ตรงๆ ข้ามกฎห้ามลบไปได้เลย จุดนี้คือจุดบล็อกจริงที่ทุกทางต้องผ่าน
                // ตัดสินด้วย isDeleteLocked ตัวเดียวกับที่ UI ใช้ซ่อนปุ่ม แล้วค่อยเลือกข้อความ
                // ตามเหตุผลที่โดนบล็อก เพื่อไม่ให้สองที่นิยามกฎต่างกัน
                val existing = activityDao.getActivityById(activityId)
                val status = com.example.pp68_salestrackingapp.utils.AppointmentStatus
                if (existing != null &&
                    status.isDeleteLocked(existing.status, existing.activityDate, existing.activityType)
                ) {
                    val message = if (status.isEditLocked(existing.status, existing.activityDate)) {
                        "ใกล้ถึงวันนัดแล้ว (เหลือไม่ถึง 7 วัน) จึงยกเลิกหรือลบแผนนี้ไม่ได้"
                    } else {
                        "เลยวันนัดแล้วแต่ไม่ได้เช็คอิน จึงถือว่าขาดนัด และลบทิ้งไม่ได้ " +
                            "ให้บันทึกผลย้อนหลังไว้ว่าเกิดอะไรขึ้นแทน"
                    }
                    return@withContext kotlin.Result.failure(Exception(message))
                }
                if (activityId.startsWith("TEMP-")) {
                    activityDao.deleteActivityById(activityId)
                    deleteChildRowsOf(activityId)
                    cancelAlarmSafely(activityId)
                    return@withContext kotlin.Result.success(Unit)
                }
                val response = apiService.deleteActivity("eq.$activityId")
                if (response.isSuccessful) {
                    activityDao.deleteActivityById(activityId)
                    deleteChildRowsOf(activityId)
                    cancelAlarmSafely(activityId)
                    kotlin.Result.success(Unit)
                } else {
                    kotlin.Result.failure(Exception("ลบนัดหมายบนเซิร์ฟเวอร์ไม่สำเร็จ"))
                }
            } catch (e: Exception) {
                if (activityId.startsWith("TEMP-")) {
                    activityDao.deleteActivityById(activityId)
                    deleteChildRowsOf(activityId)
                    cancelAlarmSafely(activityId)
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
            try {
                val resp = apiService.getActivityResult("eq.$activityId")
                if (resp.isSuccessful && !resp.body().isNullOrEmpty()) {
                    val result = resp.body()!!.first().copy(isSynced = true)
                    // ✅ insertResult ใช้ OnConflictStrategy.REPLACE — ถ้า result_id นี้มีอยู่แล้วในเครื่อง
                    // SQLite จะลบแถวเดิมทิ้งก่อนแล้วค่อย insert ใหม่ ซึ่ง cascade ลบ activity_result_photo ที่ผูกอยู่ไปด้วย
                    // ต้องดึงรูปกลับมาจาก server ใหม่ทุกครั้งหลังจากนี้ ไม่งั้นจะเหลือแค่รูปปก
                    resultDao.insertResult(result)
                    refreshPhotosForResult(result.resultId)
                    return@withContext result
                }
                resultDao.getResultByActivityId(activityId)
            } catch (e: Exception) { resultDao.getResultByActivityId(activityId) }
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
            try {
                val local = resultDao.getResultById(resultId)
                if (local != null) return@withContext local
                val resp = apiService.getResultById("eq.$resultId")
                if (resp.isSuccessful && !resp.body().isNullOrEmpty()) {
                    val result = resp.body()!!.first().copy(isSynced = true)
                    resultDao.insertResult(result)
                    return@withContext result
                }
                null
            } catch (e: Exception) { null }
        }
    }

    suspend fun saveActivityResult(result: ActivityResult, photoUrls: List<String> = emptyList()): kotlin.Result<Unit> {
        return withContext(Dispatchers.IO) {
            saveResultAsNewVersion(result, photoUrls)
        }
    }

    suspend fun saveStandaloneResult(projectId: String, result: ActivityResult, photoUrls: List<String> = emptyList()): kotlin.Result<Unit> {
        return withContext(Dispatchers.IO) {
            val resultWithProject = result.copy(projectId = projectId, activityId = null)
            saveResultAsNewVersion(resultWithProject, photoUrls)
        }
    }

    // ✅ ทุกครั้งที่บันทึก (ทั้งครั้งแรกและแก้ไข) จะสร้างแถวใหม่เป็น version ถัดไปเสมอ
    // แทนการเขียนทับของเดิม — เพื่อรักษาประวัติการแก้ไขบันทึกผลการขายไว้ทั้งหมด
    private suspend fun saveResultAsNewVersion(result: ActivityResult, photoUrls: List<String> = emptyList()): kotlin.Result<Unit> {
        val previous = result.resultId.takeIf { it.isNotBlank() }?.let { resultDao.getResultById(it) }
        val tempId = "TEMP-${java.util.UUID.randomUUID().toString().take(8).uppercase()}"
        val groupId = previous?.resultGroupId ?: previous?.resultId ?: tempId
        val localResult = result.copy(
            resultId      = tempId,
            isSynced      = false,
            version       = (previous?.version ?: 0) + 1,
            isLatest      = true,
            resultGroupId = groupId
        )
        // เขียนลงเครื่องให้ครบก่อน — เดิม 3 บรรทัดนี้อยู่นอก try ทั้งหมด ถ้า Room พังตรงนี้
        // exception จะหลุดออกไปถึง ViewModel เป็น crash แทนที่จะเป็น failure ที่แสดงให้ผู้ใช้เห็นได้
        try {
            previous?.let { resultDao.markNotLatest(it.resultId) }
            resultDao.insertResult(localResult)
            savePhotosForResult(tempId, photoUrls)
        } catch (e: Exception) {
            Log.e("ActivityRepository", "saveResultAsNewVersion: เขียนลงเครื่องไม่สำเร็จ", e)
            return kotlin.Result.failure(Exception("บันทึกผลการขายไม่สำเร็จ: ${e.message}"))
        }
        return try {
            val body = buildResultBody(localResult)
            body.remove("result_id") // แต่ละ version คือแถวใหม่เสมอ ให้ server สร้าง id ให้
            val apiResp = retrySend(idempotent = false, tag = "saveResult") { apiService.insertActivityResultMap(body) }
            if (apiResp.isSuccessful) {
                val serverRow = apiResp.body()?.firstOrNull()
                val realId = serverRow?.resultId
                if (realId != null && realId != tempId) {
                    // trigger generate_result_id ฝั่ง server เป็นเจ้าของ version กับ result_group_id แล้ว
                    // เพราะค่าที่คำนวณจาก Room ในเครื่องจะผิดทันทีถ้าเครื่องนั้นมองประวัติไม่ครบ
                    // จึงต้องเอาค่าที่ server คืนมาเท่านั้น ไม่งั้นเครื่องกับ server จะต่างกัน
                    val finalGroupId = serverRow.resultGroupId ?: previous?.resultGroupId ?: previous?.resultId ?: realId
                    // ✅ ต้อง insert แถว realId ก่อน แล้วค่อยย้ายรูปมาที่ realId แล้วค่อยลบ tempId ทีหลัง
                    // เพราะ activity_result_photo มี FK CASCADE ไปยัง activity_result — ถ้าลบ tempId ก่อน รูปที่ยังผูกกับ tempId จะโดนลบไปด้วย
                    resultDao.insertResult(localResult.copy(resultId = realId, version = serverRow.version, resultGroupId = finalGroupId, isSynced = true))
                    photoDao.updateResultId(tempId, realId)
                    resultDao.deleteResultById(tempId)
                    if (photoUrls.isNotEmpty()) {
                        try { apiService.addResultPhotos(photoDao.getPhotosByResultId(realId)) } catch (_: Exception) {}
                    }
                } else {
                    resultDao.updateSyncStatus(tempId, true)
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
                syncProjectStatus(localResult)
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

    // ✅ เก็บรูปยืนยันการเข้าพบสูงสุด 5 รูปต่อบันทึกผล เรียงตาม photo_order (0 = รูปปก)
    private suspend fun savePhotosForResult(resultId: String, photoUrls: List<String>) {
        photoDao.deletePhotosByResultId(resultId)
        if (photoUrls.isEmpty()) return
        val items = photoUrls.mapIndexed { index, url -> ActivityResultPhoto(resultId, index, url) }
        photoDao.insertPhotos(items)
        if (!resultId.startsWith("TEMP-")) {
            try {
                apiService.deleteResultPhotos("eq.$resultId")
                apiService.addResultPhotos(items)
            } catch (_: IOException) { /* offline â€” synced later via SyncManager */ }
        }
    }

    suspend fun getResultPhotos(resultId: String): List<String> {
        return withContext(Dispatchers.IO) { photoDao.getPhotosByResultId(resultId).map { it.photoUrl } }
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
        withContext(Dispatchers.IO) { activityDao.updateLocationName(activityId, locationName) }
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

    private fun buildResultBody(result: ActivityResult): MutableMap<String, Any?> {
        val body = mutableMapOf<String, Any?>()
        body["result_id"] = result.resultId
        body["created_at"] = java.time.Instant.now().toString()
        body["appointment_id"] = result.activityId
        result.projectId?.let { body["project_code"] = it }
        result.createdBy?.let { body["created_by"] = it }
        result.reportDate?.let { body["report_date"] = it }
        result.newStatus?.let { body["new_status"] = it }
        result.opportunityScore?.let { body["opportunity_score"] = it }
        body["dm_involved"] = result.dmInvolved
        body["is_proposal_sent"] = result.isProposalSent
        result.proposalDate?.let { body["proposal_date"] = it }
        body["competitor_count"] = result.competitorCount
        result.responseSpeed?.let { body["response_speed"] = it }
        result.dealPosition?.let { body["deal_position"] = it }
        result.previousSolution?.let { body["current_solution"] = it }
        result.counterpartyMultiplier?.let { body["counterparty_type"] = it }
        result.summary?.let { body["note_summary"] = it }
        if (!result.photoUrl.isNullOrBlank()) body["photo_url"] = result.photoUrl
        if (!result.photoTakenAt.isNullOrBlank()) body["photo_taken_at"] = result.photoTakenAt
        if (result.photoLat != null) body["photo_lat"] = result.photoLat
        if (result.photoLng != null) body["photo_lng"] = result.photoLng
        if (!result.photoDeviceModel.isNullOrBlank()) body["photo_device_model"] = result.photoDeviceModel
        body["version"] = result.version
        body["is_latest"] = result.isLatest
        result.resultGroupId?.let { body["result_group_id"] = it }
        return body
    }

    suspend fun uploadVisitPhoto(activityId: String, imageBytes: ByteArray): kotlin.Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val requestBody = imageBytes.toRequestBody("image/jpeg".toMediaType())
                val photoPart = MultipartBody.Part.createFormData(name = "photo", filename = "visit_photo.jpg", body = requestBody)
                val appointmentIdPart = activityId.toRequestBody("text/plain".toMediaType())
                val response = uploadApiService.uploadVisitPhoto(appointmentIdPart, photoPart)
                if (response.isSuccessful && response.body() != null) kotlin.Result.success(response.body()!!.photoUrl)
                else kotlin.Result.failure(Exception("Upload failed: ${response.code()}"))
            } catch (e: Exception) { kotlin.Result.failure(e) }
        }
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
            try { activityDao.getActivitiesByProject(projectId).first().map { enrichActivity(it) } }
            catch (e: Exception) { emptyList() }
        }
    }

    suspend fun saveAppointmentContacts(appointmentId: String, contactIds: List<String>) {
        withContext(Dispatchers.IO) {
            appointmentContactDao.deleteContactsByAppointmentId(appointmentId)
            val items = contactIds.map { AppointmentContact(appointmentId, it) }
            if (items.isNotEmpty()) {
                appointmentContactDao.insertAppointmentContacts(items)
            }
            // ✅ ต้องยิง delete เสมอแม้ items ว่างเปล่า (ลบผู้เข้าร่วมออกหมด) ไม่งั้น server จะเหลือ
            // รายชื่อเดิมค้างอยู่ตลอดไปเพราะ if (items.isNotEmpty()) เดิมครอบ delete ไว้ด้วย
            if (!appointmentId.startsWith("TEMP-")) {
                try {
                    apiService.deleteAppointmentContacts("eq.$appointmentId")
                    if (items.isNotEmpty()) apiService.addAppointmentContacts(items)
                } catch (_: IOException) { /* offline — skip, contacts saved locally */ }
            }
        }
    }

    suspend fun getAppointmentContacts(appointmentId: String): List<String> {
        return withContext(Dispatchers.IO) { appointmentContactDao.getContactsByAppointmentId(appointmentId).map { it.contactId } }
    }
}

