package com.example.pp68_salestrackingapp.data.repository

import android.util.Log
import com.example.pp68_salestrackingapp.data.local.*
import com.example.pp68_salestrackingapp.data.model.*
import com.example.pp68_salestrackingapp.data.remote.ApiService
import com.example.pp68_salestrackingapp.data.remote.UploadApiService
import com.example.pp68_salestrackingapp.ui.viewmodels.activity.ActivityCard
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
    private val appointmentContactDao: AppointmentContactDao,
    private val projectRepo: ProjectRepository
) {
    fun getAllActivitiesFlow(): Flow<List<SalesActivity>> = activityDao.getAllActivities()

    fun getActivitiesByProjectFlow(projectId: String): Flow<List<SalesActivity>> =
        activityDao.getActivitiesByProject(projectId).map { list ->
            list.map { enrichActivity(it) }
        }

    fun getAllResultIdsFlow(): Flow<List<String>> = resultDao.getAllResultIdsFlow()

    fun getAllResultsFlow(): Flow<List<ActivityResult>> = resultDao.getAllResultsFlow()

    fun getResultsByProjectFlow(projectId: String): Flow<List<ActivityResult>> =
        resultDao.getAllResultsByProject(projectId)

    suspend fun refreshActivities(userId: String): kotlin.Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val resp = apiService.getMyAppointments("eq.$userId")
                if (resp.isSuccessful && resp.body() != null) {
                    activityDao.clearAndInsert(resp.body()!!)
                    kotlin.Result.success(Unit)
                } else {
                    kotlin.Result.failure(Exception("API error: ${resp.code()}"))
                }
            } catch (e: Exception) {
                kotlin.Result.failure(e)
            }
        }
    }

    suspend fun addActivity(activity: SalesActivity): kotlin.Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val apiActivity = activity.copy(
                    projectName = null,
                    companyName = null,
                    contactName = null,
                    weeklyNote  = null
                )
                
                val response = apiService.addActivity(apiActivity)
                activityDao.insertActivity(activity)
                
                if (response.isSuccessful) {
                    kotlin.Result.success(Unit)
                } else {
                    val errBody = response.errorBody()?.string() ?: ""
                    kotlin.Result.failure(Exception("API Error: ${response.code()} — $errBody"))
                }
            } catch (e: Exception) {
                activityDao.insertActivity(activity)
                kotlin.Result.success(Unit)
            }
        }
    }

    suspend fun updateActivity(activityId: String, updates: Map<String, Any>): kotlin.Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                activityDao.getActivityById(activityId)?.let { local ->
                    var updated = local
                    if (updates.containsKey("plan_status")) {
                        updated = updated.copy(status = updates["plan_status"] as String)
                    }
                    if (updates.containsKey("note")) {
                        updated = updated.copy(weeklyNote = updates["note"] as? String)
                    }
                    if (updates.containsKey("topic")) {
                        updated = updated.copy(detail = updates["topic"] as? String)
                    }
                    activityDao.insertActivity(updated)
                }

                val response = apiService.updateActivity("eq.$activityId", updates)
                
                if (response.isSuccessful) {
                    kotlin.Result.success(Unit)
                } else {
                    val errBody = response.errorBody()?.string() ?: ""
                    kotlin.Result.failure(Exception("อัปเดต Appointment ไม่สำเร็จ: ${response.code()} — $errBody"))
                }
            } catch (e: Exception) {
                kotlin.Result.failure(e)
            }
        }
    }

    suspend fun savePlanItems(appointmentId: String, items: List<ActivityPlanItem>) {
        withContext(Dispatchers.IO) {
            planItemDao.deletePlanItemsByAppointmentId(appointmentId)
            planItemDao.insertPlanItems(items)

            try {
                val dtos = items.map { item ->
                    ChecklistInsertDto(
                        appointmentId = appointmentId,
                        masterId = item.masterId,
                        isDone = item.isDone
                    )
                }
                apiService.insertChecklist(dtos)
            } catch (e: Exception) { }
        }
    }

    suspend fun getPlanItems(activityId: String): kotlin.Result<List<PlanItemDto>> {
        return withContext(Dispatchers.IO) {
            try {
                val localItems = planItemDao.getPlanItemsByAppointmentId(activityId)

                if (localItems.isNotEmpty()) {
                    val dtos = localItems.map {
                        PlanItemDto(
                            masterId      = it.masterId,
                            masterDetails = MasterActDto(it.actName ?: ""),
                            isDone        = it.isDone
                        )
                    }
                    return@withContext kotlin.Result.success(dtos)
                }

                val checklistResp = apiService.getChecklistByAppointment("eq.$activityId")
                if (checklistResp.isSuccessful && !checklistResp.body().isNullOrEmpty()) {
                    val checklist = checklistResp.body()!!
                    val masterResp = apiService.getMasterActivities()
                    val masters = if (masterResp.isSuccessful) masterResp.body() ?: emptyList()
                    else emptyList()

                    val dtos = checklist.map { item ->
                        val master = masters.find { it.masterId == item.masterId }
                        PlanItemDto(
                            masterId      = item.masterId,
                            masterDetails = MasterActDto(master?.actName ?: "Activity ${item.masterId}"),
                            isDone        = item.isDone
                        )
                    }

                    val planItems = dtos.map { dto ->
                        ActivityPlanItem(
                            appointmentId = activityId,
                            masterId      = dto.masterId,
                            actName       = dto.masterDetails?.actName,
                            isDone        = dto.isDone
                        )
                    }
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
            planItemDao.updateItemStatus(activityId, masterId, isDone)
        }
    }

    suspend fun updateChecklistItem(
        appointmentId: String,
        masterId:      Int,
        isDone:        Boolean
    ) {
        withContext(Dispatchers.IO) {
            try {
                planItemDao.updateItemStatus(appointmentId, masterId, isDone)
                val updates = mapOf<String, Any>("is_done" to isDone)
                apiService.updateChecklist(
                    appointmentId = "eq.$appointmentId",
                    masterId      = "eq.$masterId",
                    updates       = updates
                )
            } catch (e: Exception) { }
        }
    }

    suspend fun getActivityById(id: String): kotlin.Result<List<SalesActivity>> {
        return withContext(Dispatchers.IO) {
            try {
                val local = activityDao.getActivityById(id)
                if (local != null && !local.plannedTime.isNullOrBlank()) {
                    return@withContext kotlin.Result.success(listOf(enrichActivity(local)))
                }

                val resp = apiService.getAppointmentById("eq.$id")
                if (resp.isSuccessful && resp.body() != null) {
                    val data = resp.body()!!
                    if (data.isNotEmpty()) activityDao.insertActivities(data)
                    kotlin.Result.success(data.map { enrichActivity(it) })
                } else {
                    if (local != null) kotlin.Result.success(listOf(enrichActivity(local)))
                    else kotlin.Result.success(emptyList())
                }
            } catch (e: Exception) {
                val local = activityDao.getActivityById(id)
                if (local != null) kotlin.Result.success(listOf(enrichActivity(local)))
                else kotlin.Result.failure(e)
            }
        }
    }

    suspend fun checkIn(
        activityId: String,
        lat: Double,
        lng: Double,
        isVerified: Boolean
    ): kotlin.Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val updates = mapOf(
                    "check_in_lat" to lat as Any,
                    "check_in_long" to lng as Any,
                    "check_in_time" to java.time.Instant.now().toString() as Any,
                    "plan_status" to "checked_in",
                    "is_location_verified" to isVerified
                )
                apiService.updateActivity("eq.$activityId", updates)
                activityDao.getActivityById(activityId)?.let {
                    activityDao.insertActivity(it.copy(
                        status = "checked_in",
                        checkInLat = lat,
                        checkInLong = lng,
                        isLocationVerified = isVerified
                    ))
                }
                kotlin.Result.success(Unit)
            } catch (e: Exception) {
                activityDao.getActivityById(activityId)?.let {
                    activityDao.insertActivity(it.copy(
                        status = "checked_in",
                        checkInLat = lat,
                        checkInLong = lng,
                        isLocationVerified = isVerified
                    ))
                }
                kotlin.Result.success(Unit)
            }
        }
    }

    suspend fun finishActivity(
        activityId:    String,
        doneMasterIds: List<Int>,
        note:          String?
    ): kotlin.Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val currentItems  = planItemDao.getPlanItemsByAppointmentId(activityId)
                val updatedItems  = currentItems.map { it.copy(isDone = it.masterId in doneMasterIds) }
                planItemDao.insertPlanItems(updatedItems)

                val updates = mutableMapOf<String, Any>("plan_status" to "completed")
                note?.let { updates["note"] = it }

                val response = apiService.updateActivity("eq.$activityId", updates)
                activityDao.getActivityById(activityId)?.let {
                    activityDao.insertActivity(it.copy(status = "completed", weeklyNote = note))
                }

                if (response.isSuccessful) kotlin.Result.success(Unit)
                else kotlin.Result.failure(Exception("HTTP ${response.code()}"))
            } catch (e: Exception) {
                activityDao.getActivityById(activityId)?.let {
                    activityDao.insertActivity(it.copy(status = "completed", weeklyNote = note))
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
                    val customer = customers[activity.customerId]

                    ActivityCard(
                        activityId = activity.activityId,
                        activityType = activity.activityType,
                        projectName = project?.projectName ?: activity.projectName,
                        companyName = customer?.companyName ?: activity.companyName,
                        contactName = activity.contactName,
                        objective = activity.detail,
                        planStatus = activity.status,
                        plannedDate = activity.activityDate,
                        plannedTime = activity.plannedTime,
                        plannedEndTime = activity.plannedEndTime,
                        weeklyNote = activity.weeklyNote,
                        customerId = activity.customerId
                    )
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
                    resp.body()!!.map { dto ->
                        ActivityMaster(
                            masterId = dto.masterId,
                            category = dto.category,
                            actName  = dto.actName
                        )
                    }
                } else emptyList()
            } catch (e: Exception) {
                emptyList()
            }
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
                    val result = resp.body()!!.first()
                    resultDao.insertResult(result)
                    return@withContext result
                }
                resultDao.getResultByActivityId(activityId)
            } catch (e: Exception) {
                resultDao.getResultByActivityId(activityId)
            }
        }
    }

    // ✅ เพิ่มใหม่ — ดึงข้อมูลบันทึกด้วย Result ID โดยตรง
    suspend fun getResultById(resultId: String): ActivityResult? {
        return withContext(Dispatchers.IO) {
            try {
                val local = resultDao.getResultById(resultId)
                if (local != null) return@withContext local

                val resp = apiService.getActivityResult("eq.$resultId")
                if (resp.isSuccessful && !resp.body().isNullOrEmpty()) {
                    val result = resp.body()!!.first()
                    resultDao.insertResult(result)
                    return@withContext result
                }
                null
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun saveActivityResult(result: ActivityResult): kotlin.Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                resultDao.insertResult(result)
                val body = buildResultBody(result)
                val apiResp = apiService.upsertActivityResult(result = body)

                if (apiResp.isSuccessful) {
                    syncProjectStatus(result)
                    kotlin.Result.success(Unit)
                } else {
                    val err = apiResp.errorBody()?.string() ?: "Unknown"
                    kotlin.Result.failure(Exception("HTTP ${apiResp.code()}: $err"))
                }
            } catch (e: Exception) {
                kotlin.Result.failure(e)
            }
        }
    }

    suspend fun saveStandaloneResult(
        projectId: String,
        result: ActivityResult
    ): kotlin.Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val resultWithProject = result.copy(
                    resultId   = java.util.UUID.randomUUID().toString(),
                    projectId  = projectId,
                    activityId = null
                )
                resultDao.insertResult(resultWithProject)
                val body = buildResultBody(resultWithProject)
                val apiResp = apiService.upsertActivityResult(body)

                if (apiResp.isSuccessful) {
                    syncProjectStatus(resultWithProject)
                    kotlin.Result.success(Unit)
                } else {
                    val err = apiResp.errorBody()?.string() ?: "Unknown"
                    kotlin.Result.failure(Exception("HTTP ${apiResp.code()}: $err"))
                }
            } catch (e: Exception) {
                kotlin.Result.failure(e)
            }
        }
    }

    private suspend fun syncProjectStatus(result: ActivityResult) {
        val pid = result.projectId ?: result.activityId?.let { 
            activityDao.getActivityById(it)?.projectId 
        } ?: return
        
        val newStatus = result.newStatus ?: return
        
        try {
            val project = projectDao.getProjectById(pid)
            if (project != null && project.projectStatus != newStatus) {
                val updated = project.copy(projectStatus = newStatus)
                projectRepo.updateProject(updated, result.createdBy ?: "")
            }
        } catch (e: Exception) {
            Log.e("ActivityRepository", "Update Project Status Failed: ${e.message}")
        }
    }

    private fun buildResultBody(result: ActivityResult): MutableMap<String, Any?> {
        val body = mutableMapOf<String, Any?>()
        body["result_id"]      = result.resultId
        body["appointment_id"]   = result.activityId
        result.projectId?.let    { body["project_id"]       = it }
        result.createdBy?.let    { body["created_by"]       = it }
        result.reportDate?.let   { body["report_date"]      = it }
        result.newStatus?.let    { body["new_status"]        = it }
        result.opportunityScore?.let { body["opportunity_score"] = it }
        body["dm_involved"]      = result.dmInvolved
        body["is_proposal_sent"] = result.isProposalSent
        result.proposalDate?.let { body["proposal_date"]    = it }
        body["competitor_count"] = result.competitorCount
        result.responseSpeed?.let    { body["response_speed"]    = it }
        result.dealPosition?.let     { body["deal_position"]     = it }
        result.previousSolution?.let { body["current_solution"]  = it }
        result.counterpartyMultiplier?.let { body["counterparty_type"] = it }
        result.summary?.let          { body["note_summary"]      = it }
        if (!result.photoUrl.isNullOrBlank())         body["photo_url"]          = result.photoUrl
        if (!result.photoTakenAt.isNullOrBlank())     body["photo_taken_at"]     = result.photoTakenAt
        if (result.photoLat != null)                  body["photo_lat"]          = result.photoLat
        if (result.photoLng != null)                  body["photo_lng"]          = result.photoLng
        if (!result.photoDeviceModel.isNullOrBlank()) body["photo_device_model"] = result.photoDeviceModel
        return body
    }

    suspend fun uploadVisitPhoto(activityId: String, imageBytes: ByteArray): kotlin.Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val requestBody = imageBytes.toRequestBody("image/jpeg".toMediaType())
                val photoPart = MultipartBody.Part.createFormData(
                    name = "photo",
                    filename = "visit_photo.jpg",
                    body = requestBody
                )
                val appointmentIdPart = activityId.toRequestBody("text/plain".toMediaType())

                val response = uploadApiService.uploadVisitPhoto(appointmentIdPart, photoPart)
                if (response.isSuccessful && response.body() != null) {
                    kotlin.Result.success(response.body()!!.photoUrl)
                } else {
                    kotlin.Result.failure(Exception("Upload failed: ${response.code()}"))
                }
            } catch (e: Exception) {
                kotlin.Result.failure(e)
            }
        }
    }

    suspend fun enrichActivity(activity: SalesActivity): SalesActivity {
        return withContext(Dispatchers.IO) {
            try {
                // 1. ดึงชื่อบริษัทจาก Local
                val customer = customerDao.getCustomerById(activity.customerId)
                val companyName = customer?.companyName ?: activity.companyName

                // 2. ดึงชื่อโครงการจาก Local
                val project = activity.projectId?.let { projectDao.getProjectById(it) }
                val projectName = project?.projectName ?: activity.projectName

                // 3. ดึงชื่อผู้ติดต่อ
                val contactIds = appointmentContactDao.getContactsByAppointmentId(activity.activityId).map { it.contactId }
                val allContacts = contactDao.getContactsByCustomerId(activity.customerId)
                val selectedContacts = allContacts.filter { it.contactId in contactIds }
                
                val namesString = if (selectedContacts.isNotEmpty()) {
                    selectedContacts.joinToString(", ") { it.fullName ?: it.nickname ?: "Unknown" }
                } else {
                    allContacts.firstOrNull()?.let { it.fullName ?: it.nickname }
                }

                activity.copy(
                    companyName = companyName,
                    projectName = projectName,
                    contactName = namesString ?: activity.contactName
                )
            } catch (e: Exception) {
                activity
            }
        }
    }

    suspend fun getActivitiesByProjectId(projectId: String): List<SalesActivity> {
        return withContext(Dispatchers.IO) {
            try {
                activityDao.getActivitiesByProject(projectId).first().map { enrichActivity(it) }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    suspend fun saveAppointmentContacts(appointmentId: String, contactIds: List<String>) {
        withContext(Dispatchers.IO) {
            appointmentContactDao.deleteContactsByAppointmentId(appointmentId)
            val items = contactIds.map { AppointmentContact(appointmentId, it) }
            appointmentContactDao.insertAppointmentContacts(items)
        }
    }

    suspend fun getAppointmentContacts(appointmentId: String): List<String> {
        return withContext(Dispatchers.IO) {
            appointmentContactDao.getContactsByAppointmentId(appointmentId).map { it.contactId }
        }
    }
}
