package com.example.pp68_salestrackingapp.data.repository

import android.util.Log
import com.example.pp68_salestrackingapp.data.local.*
import com.example.pp68_salestrackingapp.data.model.*
import com.example.pp68_salestrackingapp.data.remote.ApiService
import com.example.pp68_salestrackingapp.data.remote.UploadApiService
import com.example.pp68_salestrackingapp.ui.viewmodels.activity.ActivityCard
import com.example.pp68_salestrackingapp.utils.SyncManager
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
    private val syncManager: SyncManager
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
                kotlin.Result.success(Unit) // offline — Room data still valid
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
                kotlin.Result.success(Unit) // offline — Room data still valid
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
            activityDao.insertActivity(localActivity)
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
                val response = apiService.addActivityMap(body)
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
                        kotlin.Result.success(tempId)
                    }
                } else {
                    syncManager.scheduleSync()
                    kotlin.Result.success(tempId)
                }
            } catch (e: IOException) {
                syncManager.scheduleSync()
                kotlin.Result.success(tempId)
            } catch (e: Exception) {
                kotlin.Result.failure(e)
            }
        }
    }

    suspend fun updateActivity(activityId: String, updates: Map<String, Any>): kotlin.Result<Unit> {
        return withContext(Dispatchers.IO) {
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

                if (activityId.startsWith("TEMP-")) {
                    syncManager.scheduleSync()
                    return@withContext kotlin.Result.success(Unit)
                }
                val response = apiService.updateActivity("eq.$activityId", updates)
                if (response.isSuccessful) {
                    activityDao.updateSyncStatus(activityId, true)
                    kotlin.Result.success(Unit)
                } else {
                    syncManager.scheduleSync()
                    kotlin.Result.success(Unit)
                }
            } catch (e: Exception) {
                syncManager.scheduleSync()
                kotlin.Result.success(Unit)
            }
        }
    }

    suspend fun savePlanItems(appointmentId: String, items: List<ActivityPlanItem>) {
        withContext(Dispatchers.IO) {
            planItemDao.deletePlanItemsByAppointmentId(appointmentId)
            planItemDao.insertPlanItems(items)
            if (!appointmentId.startsWith("TEMP-")) {
                try {
                    apiService.deleteChecklistByAppointment("eq.$appointmentId")
                    if (items.isNotEmpty()) {
                        val dtos = items.map { ChecklistInsertDto(appointmentId = appointmentId, masterId = it.masterId, isDone = it.isDone) }
                        apiService.insertChecklist(dtos)
                    }
                } catch (_: Exception) { }
            }
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
                        PlanItemDto(masterId = item.masterId, masterDetails = MasterActDto(master?.actName ?: "Activity ${item.masterId}"), isDone = item.isDone)
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
            try {
                planItemDao.updateItemStatus(appointmentId, masterId, isDone)
                val updates = mapOf<String, Any>("is_checked" to isDone)
                apiService.updateChecklist(appointmentId = "eq.$appointmentId", masterId = "eq.$masterId", updates = updates)
            } catch (e: Exception) { }
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
            try {
                val updates = mutableMapOf<String, Any>("check_in_lat" to lat, "check_in_long" to lng, "check_in_time" to java.time.Instant.now().toString(), "plan_status" to "checked_in", "is_location_verified" to isVerified)
                distanceDeviation?.let { updates["distance_deviation"] = it }
                apiService.updateActivity("eq.$activityId", updates)
                activityDao.getActivityById(activityId)?.let {
                    activityDao.insertActivity(it.copy(status = "checked_in", checkInLat = lat, checkInLong = lng, isLocationVerified = isVerified, distanceDeviation = distanceDeviation, isSynced = true))
                }
                kotlin.Result.success(Unit)
            } catch (e: Exception) {
                activityDao.getActivityById(activityId)?.let {
                    activityDao.insertActivity(it.copy(status = "checked_in", checkInLat = lat, checkInLong = lng, isLocationVerified = isVerified, distanceDeviation = distanceDeviation, isSynced = false))
                    syncManager.scheduleSync()
                }
                kotlin.Result.success(Unit)
            }
        }
    }

    suspend fun finishActivity(activityId: String, doneMasterIds: List<Int>, note: String?): kotlin.Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val currentItems = planItemDao.getPlanItemsByAppointmentId(activityId)
                val updatedItems = currentItems.map { it.copy(isDone = it.masterId in doneMasterIds) }
                planItemDao.insertPlanItems(updatedItems)
                val updates = mutableMapOf<String, Any>("plan_status" to "completed")
                note?.let { updates["note"] = it }
                val response = apiService.updateActivity("eq.$activityId", updates)
                activityDao.getActivityById(activityId)?.let {
                    activityDao.insertActivity(it.copy(status = "completed", weeklyNote = note, isSynced = response.isSuccessful))
                    if (!response.isSuccessful) syncManager.scheduleSync()
                }
                kotlin.Result.success(Unit)
            } catch (e: Exception) {
                activityDao.getActivityById(activityId)?.let {
                    activityDao.insertActivity(it.copy(status = "completed", weeklyNote = note, isSynced = false))
                    syncManager.scheduleSync()
                }
                kotlin.Result.success(Unit)
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
                    ActivityCard(activityId = activity.activityId, activityType = activity.activityType, projectName = project?.projectName ?: activity.projectName, companyName = customer?.companyName ?: activity.companyName, contactName = activity.contactName, objective = activity.detail, planStatus = activity.status, plannedDate = activity.activityDate, plannedTime = activity.plannedTime, plannedEndTime = activity.plannedEndTime, weeklyNote = activity.weeklyNote ?: activity.note, customerId = activity.customerId)
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

    suspend fun deleteActivity(activityId: String): kotlin.Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                apiService.deleteActivity("eq.$activityId")
                activityDao.deleteActivityById(activityId)
                kotlin.Result.success(Unit)
            } catch (e: Exception) {
                activityDao.deleteActivityById(activityId)
                kotlin.Result.success(Unit)
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
        previous?.let { resultDao.markNotLatest(it.resultId) }
        resultDao.insertResult(localResult)
        savePhotosForResult(tempId, photoUrls)
        return try {
            val body = buildResultBody(localResult)
            body.remove("result_id") // แต่ละ version คือแถวใหม่เสมอ ให้ server สร้าง id ให้
            val apiResp = apiService.insertActivityResultMap(body)
            if (apiResp.isSuccessful) {
                val realId = apiResp.body()?.firstOrNull()?.resultId
                if (realId != null && realId != tempId) {
                    val finalGroupId = previous?.resultGroupId ?: previous?.resultId ?: realId
                    // ✅ ต้อง insert แถว realId ก่อน แล้วค่อยย้ายรูปมาที่ realId แล้วค่อยลบ tempId ทีหลัง
                    // เพราะ activity_result_photo มี FK CASCADE ไปยัง activity_result — ถ้าลบ tempId ก่อน รูปที่ยังผูกกับ tempId จะโดนลบไปด้วย
                    resultDao.insertResult(localResult.copy(resultId = realId, resultGroupId = finalGroupId, isSynced = true))
                    photoDao.updateResultId(tempId, realId)
                    resultDao.deleteResultById(tempId)
                    if (photoUrls.isNotEmpty()) {
                        try { apiService.addResultPhotos(photoDao.getPhotosByResultId(realId)) } catch (_: Exception) {}
                    }
                    if (previous == null) {
                        // version แรกสุด — ผูก group id ของตัวเองเข้ากับ realId บน server ด้วย
                        try { apiService.updateActivityResult("eq.$realId", mapOf("result_group_id" to realId)) } catch (_: Exception) {}
                    }
                } else {
                    resultDao.updateSyncStatus(tempId, true)
                }
                // ✅ mark version เก่าบน server ว่าไม่ใช่ล่าสุดแล้ว (best-effort เหมือนจุดอื่นๆ ในไฟล์นี้)
                previous?.let {
                    try { apiService.updateActivityResult("eq.${it.resultId}", mapOf("is_latest" to false)) } catch (_: Exception) {}
                }
                syncProjectStatus(localResult)
                kotlin.Result.success(Unit)
            } else {
                syncManager.scheduleSync()
                kotlin.Result.success(Unit)
            }
        } catch (e: Exception) {
            syncManager.scheduleSync()
            kotlin.Result.success(Unit)
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
            } catch (_: IOException) { /* offline — synced later via SyncManager */ }
        }
    }

    suspend fun getResultPhotos(resultId: String): List<String> {
        return withContext(Dispatchers.IO) { photoDao.getPhotosByResultId(resultId).map { it.photoUrl } }
    }

    private suspend fun syncProjectStatus(result: ActivityResult) {
        val pid = result.projectId ?: result.activityId?.let { activityDao.getActivityById(it)?.projectId } ?: return
        val newStatus = result.newStatus ?: return
        try {
            val project = projectDao.getProjectById(pid)
            if (project != null && project.projectStatus != newStatus) {
                val updated = project.copy(projectStatus = newStatus, lossReason = result.lossReason)
                projectRepo.updateProject(updated, result.createdBy ?: "")
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
                if (!appointmentId.startsWith("TEMP-")) {
                    try {
                        apiService.deleteAppointmentContacts("eq.$appointmentId")
                        apiService.addAppointmentContacts(items)
                    } catch (_: IOException) { /* offline — skip, contacts saved locally */ }
                }
            }
        }
    }

    suspend fun getAppointmentContacts(appointmentId: String): List<String> {
        return withContext(Dispatchers.IO) { appointmentContactDao.getContactsByAppointmentId(appointmentId).map { it.contactId } }
    }
}
