package com.example.pp68_salestrackingapp.data.repository

import com.example.pp68_salestrackingapp.data.local.ActivityDao
import com.example.pp68_salestrackingapp.data.local.CustomerDao
import com.example.pp68_salestrackingapp.data.local.ProjectDao
import com.example.pp68_salestrackingapp.data.local.ContactDao
import com.example.pp68_salestrackingapp.data.local.ActivityPlanItemDao
import com.example.pp68_salestrackingapp.data.model.SalesActivity
import com.example.pp68_salestrackingapp.data.model.ActivityMaster
import com.example.pp68_salestrackingapp.data.model.ContactPerson
import com.example.pp68_salestrackingapp.data.model.PlanItemDto
import com.example.pp68_salestrackingapp.data.model.MasterActDto
import com.example.pp68_salestrackingapp.data.model.ActivityPlanItem
import com.example.pp68_salestrackingapp.data.model.ChecklistInsertDto
import com.example.pp68_salestrackingapp.data.remote.ApiService
import com.example.pp68_salestrackingapp.ui.viewmodels.activity.ActivityCard
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ActivityRepository @Inject constructor(
    private val apiService: ApiService,
    private val activityDao: ActivityDao,
    private val projectDao: ProjectDao,
    private val customerDao: CustomerDao,
    private val contactDao: ContactDao,
    private val planItemDao: ActivityPlanItemDao
) {
    fun getAllActivitiesFlow(): Flow<List<SalesActivity>> = activityDao.getAllActivities()

    fun getActivitiesByProjectFlow(projectId: String): Flow<List<SalesActivity>> =
        activityDao.getActivitiesByProject(projectId)

    suspend fun refreshActivities(userId: String): kotlin.Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val resp = apiService.getMyAppointments("eq.$userId")
                if (resp.isSuccessful && resp.body() != null) {
                    // ✅ อัปเดต local เฉพาะตอน API สำเร็จ
                    activityDao.clearAndInsert(resp.body()!!)
                    kotlin.Result.success(Unit)
                } else {
                    // ✅ API fail → ใช้ข้อมูล local เดิม ไม่ลบทิ้ง
                    kotlin.Result.failure(Exception("API error: ${resp.code()}"))
                }
            } catch (e: Exception) {
                // ✅ Network error → ใช้ข้อมูล local เดิม
                kotlin.Result.failure(e)
            }
        }
    }

    suspend fun addActivity(activity: SalesActivity): kotlin.Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.addActivity(activity)
                // ✅ 201 Created = isSuccessful = true
                activityDao.insertActivity(activity)
                if (response.isSuccessful) {
                    kotlin.Result.success(Unit)
                } else {
                    val errBody = response.errorBody()?.string() ?: ""
                    kotlin.Result.failure(Exception("บันทึก Appointment ไม่สำเร็จ: ${response.code()} — $errBody"))
                }
            } catch (e: Exception) {
                // offline mode — บันทึก local
                activityDao.insertActivity(activity)
                kotlin.Result.success(Unit)
            }
        }
    }

    suspend fun updateActivity(activityId: String, updates: Map<String, Any>): kotlin.Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
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
            // บันทึก local
            planItemDao.deletePlanItemsByAppointmentId(appointmentId)
            planItemDao.insertPlanItems(items)

            // ✅ บันทึกขึ้น API ด้วย
            try {
                val dtos = items.map { item ->
                    ChecklistInsertDto(
                        appointmentId = appointmentId,
                        masterId = item.masterId,
                        isDone = item.isDone
                    )
                }
                apiService.insertChecklist(dtos)
            } catch (e: Exception) {
                // ถ้า API fail ก็ไม่เป็นไร มี local แล้ว
            }
        }
    }

    suspend fun getPlanItems(activityId: String): kotlin.Result<List<PlanItemDto>> {
        return withContext(Dispatchers.IO) {
            try {
                // ✅ ดึงจาก local ก่อน
                val localItems = planItemDao.getPlanItemsByAppointmentId(activityId)

                if (localItems.isNotEmpty()) {
                    // มีใน local ใช้เลย
                    val dtos = localItems.map {
                        PlanItemDto(
                            masterId      = it.masterId,
                            masterDetails = MasterActDto(it.actName ?: ""),
                            isDone        = it.isDone
                        )
                    }
                    return@withContext kotlin.Result.success(dtos)
                }

                // ✅ ถ้า local ว่าง ดึงจาก API
                val checklistResp = apiService.getChecklistByAppointment("eq.$activityId")
                if (checklistResp.isSuccessful && !checklistResp.body().isNullOrEmpty()) {
                    val checklist = checklistResp.body()!!

                    // ดึง master name จาก activity_master
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

                    // บันทึกลง local ด้วย
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
                // อัปเดต local ก่อน
                planItemDao.updateItemStatus(appointmentId, masterId, isDone)

                // อัปเดต API
                val updates = mapOf<String, Any>("is_done" to isDone)
                apiService.updateChecklist(
                    appointmentId = "eq.$appointmentId",
                    masterId      = "eq.$masterId",
                    updates       = updates
                )
            } catch (e: Exception) {
                // ถ้า API fail ก็ไม่เป็นไร local บันทึกแล้ว
            }
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

    suspend fun checkIn(activityId: String, lat: Double, lng: Double): kotlin.Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val updates = mapOf(
                    "checkin_lat" to lat as Any,
                    "checkin_lng" to lng as Any,
                    "checkin_time" to java.time.Instant.now().toString() as Any,
                    "plan_status" to "checked_in"
                )
                apiService.updateActivity(activityId, updates)
                activityDao.getActivityById(activityId)?.let {
                    activityDao.insertActivity(it.copy(status = "checked_in"))
                }
                kotlin.Result.success(Unit)
            } catch (e: Exception) {
                activityDao.getActivityById(activityId)?.let {
                    activityDao.insertActivity(it.copy(status = "checked_in"))
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

                // อัปเดต local
                activityDao.getActivityById(activityId)?.let {
                    activityDao.insertActivity(it.copy(status = "completed"))
                }

                if (response.isSuccessful) kotlin.Result.success(Unit)
                else kotlin.Result.failure(Exception("HTTP ${response.code()}"))
            } catch (e: Exception) {
                // offline mode — อัปเดต local แล้ว return success
                activityDao.getActivityById(activityId)?.let {
                    activityDao.insertActivity(it.copy(status = "completed"))
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

    // เพิ่มใน ActivityRepository.kt
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

    // เพิ่ม DTO
    data class ActivityMasterDto(
        @SerializedName("master_id")  val masterId:  Int,
        @SerializedName("category")   val category:  String,
        @SerializedName("act_name")   val actName:   String,
        @SerializedName("is_active")  val isActive:  Boolean = true
    )

    suspend fun deleteActivity(activityId: String): kotlin.Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                // ลบจาก API ก่อน
                val response = apiService.deleteActivity("eq.$activityId")
                // ลบจาก local DB เสมอ
                activityDao.deleteActivityById(activityId)
                kotlin.Result.success(Unit)
            } catch (e: Exception) {
                // ถ้า API fail ก็ยังลบ local ได้
                activityDao.deleteActivityById(activityId)
                kotlin.Result.success(Unit)
            }
        }
    }
}

