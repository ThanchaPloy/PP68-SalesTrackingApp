package com.example.pp68_salestrackingapp.data.repository

import android.util.Log
import com.example.pp68_salestrackingapp.data.local.*
import com.example.pp68_salestrackingapp.data.model.SalesActivity
import com.example.pp68_salestrackingapp.data.model.ActivityMaster
import com.example.pp68_salestrackingapp.data.model.PlanItemDto
import com.example.pp68_salestrackingapp.data.model.MasterActDto
import com.example.pp68_salestrackingapp.data.model.ActivityPlanItem
import com.example.pp68_salestrackingapp.data.model.ActivityResult
import com.example.pp68_salestrackingapp.data.model.ChecklistInsertDto
import com.example.pp68_salestrackingapp.data.remote.ApiService
import com.example.pp68_salestrackingapp.data.remote.UploadApiService
import com.example.pp68_salestrackingapp.ui.viewmodels.activity.ActivityCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

class ActivityRepository @Inject constructor(
    private val apiService: ApiService,
    private val uploadApiService: UploadApiService,
    private val activityDao: ActivityDao,
    private val projectDao: ProjectDao,
    private val customerDao: CustomerDao,
    private val contactDao: ContactDao,
    private val planItemDao: ActivityPlanItemDao,
    private val resultDao: ActivityResultDao
) {
    fun getAllActivitiesFlow(): Flow<List<SalesActivity>> = activityDao.getAllActivities()

    fun getActivitiesByProjectFlow(projectId: String): Flow<List<SalesActivity>> =
        activityDao.getActivitiesByProject(projectId)

    fun getAllResultIdsFlow(): Flow<List<String>> = resultDao.getAllResultIdsFlow()

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
                if (local != null) return@withContext kotlin.Result.success(listOf(local))

                val resp = apiService.getAppointmentById("eq.$id")
                if (resp.isSuccessful && resp.body() != null) {
                    val data = resp.body()!!
                    if (data.isNotEmpty()) activityDao.insertActivities(data)
                    kotlin.Result.success(data)
                } else {
                    kotlin.Result.success(emptyList())
                }
            } catch (e: Exception) {
                val local = activityDao.getActivityById(id)
                if (local != null) kotlin.Result.success(listOf(local))
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
                // ดึง local ก่อน
                val local = resultDao.getResultByActivityId(activityId)
                if (local != null) return@withContext local

                // fallback API
                val resp = apiService.getActivityResult("eq.$activityId")
                if (resp.isSuccessful && !resp.body().isNullOrEmpty()) {
                    val result = resp.body()!!.first()
                    resultDao.insertResult(result)
                    result
                } else null
            } catch (e: Exception) {
                resultDao.getResultByActivityId(activityId)
            }
        }
    }

    suspend fun saveActivityResult(result: ActivityResult): kotlin.Result<Unit> {
        return withContext(Dispatchers.IO) {
            Log.d("ActivityRepository", "=== saveActivityResult called ===")
            Log.d("ActivityRepository", "activityId: ${result.activityId}")
            Log.d("ActivityRepository", "body will contain: photo_url=${result.photoUrl}")

            try {
                // 1. บันทึกลง Local DB เสมอ
                resultDao.insertResult(result)

                // 2. เตรียมข้อมูลสำหรับ API (ส่งเฉพาะฟิลด์ที่ไม่เป็น Null เพื่อป้องกันการทับข้อมูลเดิม)
                val body = mutableMapOf<String, Any?>()
                body["appointment_id"]   = result.activityId
                
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
                
                // ฟิลด์เกี่ยวกับรูปภาพ
                if (!result.photoUrl.isNullOrBlank())         body["photo_url"]          = result.photoUrl
                if (!result.photoTakenAt.isNullOrBlank())     body["photo_taken_at"]     = result.photoTakenAt
                if (result.photoLat != null)                  body["photo_lat"]          = result.photoLat
                if (result.photoLng != null)                  body["photo_lng"]          = result.photoLng
                if (!result.photoDeviceModel.isNullOrBlank()) body["photo_device_model"] = result.photoDeviceModel

                Log.d("ActivityRepository", "=== body to send ===")
                Log.d("ActivityRepository", "photo_url: ${body["photo_url"]}")
                Log.d("ActivityRepository", "photo_taken_at: ${body["photo_taken_at"]}")
                Log.d("ActivityRepository", "full body: $body")

                // 3. ซิงค์กับ API
                val existing = apiService.getActivityResult("eq.${result.activityId}")
                val apiResp = if (existing.isSuccessful && !existing.body().isNullOrEmpty()) {
                    apiService.updateActivityResultMap("eq.${result.activityId}", body)
                } else {
                    apiService.insertActivityResultMap(body)
                }

                if (apiResp.isSuccessful) {
                    kotlin.Result.success(Unit)
                } else {
                    val errorMsg = apiResp.errorBody()?.string() ?: "Unknown error"
                    Log.e("ActivityRepository", "Sync failed: ${apiResp.code()} - $errorMsg")
                    kotlin.Result.failure(Exception("HTTP ${apiResp.code()}: $errorMsg"))
                }
            } catch (e: Exception) {
                Log.e("ActivityRepository", "Exception in saveActivityResult: ${e.message}")
                kotlin.Result.success(Unit)
            }
        }
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
}
