package com.example.pp68_salestrackingapp.utils

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.pp68_salestrackingapp.data.local.*
import com.example.pp68_salestrackingapp.data.model.ActivityResult
import com.example.pp68_salestrackingapp.data.model.ProjectContact
import com.example.pp68_salestrackingapp.data.model.ProjectSalesMember
import com.example.pp68_salestrackingapp.data.model.ProjectMemberInsertDto
import com.example.pp68_salestrackingapp.data.remote.ApiService
import com.example.pp68_salestrackingapp.di.TokenManager
import com.example.pp68_salestrackingapp.worker.SyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiService: ApiService,
    private val tokenManager: TokenManager,
    private val customerDao: CustomerDao,
    private val projectDao: ProjectDao,
    private val contactDao: ContactDao,
    private val activityDao: ActivityDao,
    private val resultDao: ActivityResultDao,
    private val photoDao: ActivityResultPhotoDao,
    private val appointmentContactDao: AppointmentContactDao,
    private val planItemDao: ActivityPlanItemDao,
    private val projectContactDao: ProjectContactDao,
    private val projectSalesMemberDao: ProjectSalesMemberDao
) {
    fun scheduleSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10L, TimeUnit.SECONDS)
            .addTag("data_sync_tag")
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "DataSyncWorkName",
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )
    }

    fun runSyncNow(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            try { doSync() } catch (e: Exception) {
                Log.e("SyncManager", "Foreground sync error: ${e.message}")
            }
        }
    }

    internal suspend fun doSync() {
        Log.d("SyncManager", "Starting sync...")
        tokenManager.getUserData()?.userId?.let { userId ->
            try { apiService.setAppContext(mapOf("user_id" to userId)) } catch (_: Exception) {}
        }

        val unsyncedCustomers = customerDao.getUnsyncedCustomers()
        for (customer in unsyncedCustomers) {
            try {
                val body = mutableMapOf<String, Any?>(
                    "customer_name"         to customer.companyName,
                    "gen_bus_posting_group" to customer.branchId,
                    "cust_type"             to customer.custType,
                    "address"               to customer.companyAddr,
                    "company_lat"           to customer.companyLat,
                    "company_long"          to customer.companyLong,
                    "customer_status"       to customer.companyStatus,
                    "create_date"           to customer.createdAt,
                    "created_at"            to customer.createdAt,
                    "create_by"             to customer.createdBy,
                    "salesperson_code"      to customer.createdBy,
                    "grade"                 to customer.grade,
                    "vat_registration_no"   to customer.vatRegistrationNo
                ).filterValues { it != null }
                val response = apiService.addCustomer(body)
                if (response.isSuccessful) {
                    val realCustId = response.body()?.firstOrNull()?.custId
                    if (realCustId != null && realCustId != customer.custId) {
                        contactDao.updateCustIdForContacts(customer.custId, realCustId)
                        activityDao.updateCustIdForActivities(customer.custId, realCustId)
                        customerDao.deleteCustomerById(customer.custId)
                        customerDao.insertCustomer(customer.copy(custId = realCustId, isSynced = true))
                    } else {
                        customerDao.updateSyncStatus(customer.custId, true)
                    }
                }
            } catch (e: Exception) {
                Log.e("SyncManager", "Failed to sync customer ${customer.custId}: ${e.message}")
            }
        }

        val unsyncedContacts = contactDao.getUnsyncedContacts()
        for (contact in unsyncedContacts) {
            try {
                val fields = buildMap<String, Any?> {
                    put("customer_code", contact.custId)
                    contact.fullName?.let { put("contact_name", it) }
                    contact.phoneNumber?.let { put("mobile_phone", it) }
                    contact.email?.let { put("email", it) }
                    contact.nickname?.let { put("nickname", it) }
                    contact.position?.let { put("position", it) }
                    contact.line?.let { put("line", it) }
                    put("is_active", contact.isActive)
                    put("is_dm_confirmed", contact.isDmConfirmed)
                }
                val response = apiService.addContact(fields)
                if (response.isSuccessful) {
                    val serverContact = response.body()?.firstOrNull()
                    if (serverContact != null && serverContact.contactId != contact.contactId) {
                        contactDao.deleteContactById(contact.contactId)
                        contactDao.insertContact(serverContact.copy(isSynced = true))
                    } else {
                        contactDao.updateSyncStatus(contact.contactId, true)
                    }
                }
            } catch (e: Exception) {
                Log.e("SyncManager", "Failed to sync contact ${contact.contactId}: ${e.message}")
            }
        }

        val unsyncedProjects = projectDao.getUnsyncedProjects()
        for (project in unsyncedProjects) {
            try {
                val body = mutableMapOf<String, Any?>(
                    "customer_code"     to project.custId,
                    "customer_name"     to project.customerName,
                    "project_name"      to project.projectName,
                    "branch_code"       to project.branchId,
                    "billing_branch_id" to project.billingBranchId,
                    "expected_value"    to project.expectedValue,
                    "project_status"    to project.projectStatus,
                    "start_date"        to project.startDate,
                    "closing_date"      to project.closingDate,
                    "project_lat"       to project.projectLat,
                    "project_long"      to project.projectLong,
                    "opportunity_score" to project.opportunityScore,
                    "remark"            to project.remark,
                    "create_by"         to project.createBy,
                    "created_at"        to project.createdAt
                ).filterValues { it != null }
                val response = apiService.addProject(body)
                if (!response.isSuccessful) {
                    Log.e("SyncManager", "Project sync failed ${response.code()}: custId=${project.custId} err=${response.errorBody()?.string()}")
                }
                if (response.isSuccessful) {
                    val realId = response.body()?.firstOrNull()?.projectId
                    val finalId = if (realId != null && realId != project.projectId) {
                        val oldId = project.projectId
                        activityDao.updateProjectIdForActivities(oldId, realId)
                        projectDao.insertProject(project.copy(projectId = realId, isSynced = true))
                        projectContactDao.updateProjectId(oldId, realId)
                        projectSalesMemberDao.updateProjectId(oldId, realId)
                        projectDao.deleteProjectById(oldId)
                        realId
                    } else {
                        projectDao.updateSyncStatus(project.projectId, true)
                        project.projectId
                    }

                    // Sync contacts to remote
                    try {
                        val localContacts = projectContactDao.getContactIdsByProject(finalId)
                        if (localContacts.isNotEmpty()) {
                            apiService.deleteProjectContacts("eq.$finalId")
                            val rows = localContacts.map { ProjectContact(finalId, it.trim()) }
                            apiService.addProjectContacts(rows)
                        }
                    } catch (e: Exception) {
                        Log.e("SyncManager", "Failed to sync project contacts for $finalId: ${e.message}")
                    }

                    // Sync sales members to remote
                    try {
                        val localMembers = projectSalesMemberDao.getMemberIdsByProject(finalId)
                        if (localMembers.isNotEmpty()) {
                            apiService.deleteProjectMembers("eq.$finalId")
                            val memberRows = localMembers.map { ProjectMemberInsertDto(finalId, it.trim(), "owner") }
                            apiService.addProjectMembers(memberRows)
                        } else {
                            project.createBy?.let { userId ->
                                apiService.deleteProjectMembers("eq.$finalId")
                                apiService.addProjectMembers(listOf(ProjectMemberInsertDto(finalId, userId, "owner")))
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("SyncManager", "Failed to sync project members for $finalId: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e("SyncManager", "Failed to sync project ${project.projectId}: ${e.message}")
            }
        }

        val unsyncedActivities = activityDao.getUnsyncedActivities()
        for (activity in unsyncedActivities) {
            try {
                if (activity.activityId.startsWith("TEMP-")) {
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
                        "created_at"       to activity.createdAt
                    ).filterValues { it != null }
                    val response = apiService.addActivityMap(body)
                    if (response.isSuccessful) {
                        val realId = response.body()?.firstOrNull()?.activityId
                        val finalId = realId ?: activity.activityId
                        if (realId != null && realId != activity.activityId) {
                            activityDao.insertActivity(activity.copy(activityId = realId, isSynced = true))
                            appointmentContactDao.updateAppointmentId(activity.activityId, realId)
                            planItemDao.updateAppointmentId(activity.activityId, realId)
                            activityDao.deleteActivityById(activity.activityId)
                        }
                        // ✅ ถ้าไม่ได้ realId กลับมา ห้าม mark synced เด็ดขาด ปล่อยให้ลองใหม่รอบถัดไป
                        val contacts = appointmentContactDao.getContactsByAppointmentId(finalId)
                        if (contacts.isNotEmpty()) {
                            try {
                                apiService.deleteAppointmentContacts("eq.$finalId")
                                apiService.addAppointmentContacts(contacts)
                            } catch (_: Exception) {}
                        }
                    }
                } else {
                    val patchBody = buildMap<String, Any> {
                        put("type", activity.activityType)
                        activity.detail?.let { put("topic", it) }
                        put("planned_date", activity.activityDate)
                        put("plan_status", activity.status)
                        put("is_appointment", activity.isAppointment)
                        activity.plannedTime?.let { put("planned_time", it) }
                        activity.plannedEndTime?.let { put("planned_end_time", it) }
                        activity.plannedLat?.let { put("planned_lat", it) }
                        activity.plannedLong?.let { put("planned_long", it) }
                        val noteToSync = activity.weeklyNote ?: activity.note
                        noteToSync?.let { put("note", it) }
                        
                        activity.checkInLat?.let { put("check_in_lat", it) }
                        activity.checkInLong?.let { put("check_in_long", it) }
                        activity.checkInTime?.let { put("check_in_time", it) }
                        put("is_location_verified", activity.isLocationVerified)
                        activity.distanceDeviation?.let { put("distance_deviation", it) }
                    }
                    val response = apiService.updateActivity("eq.${activity.activityId}", patchBody)
                    if (response.isSuccessful) activityDao.updateSyncStatus(activity.activityId, true)
                }
            } catch (e: Exception) {
                Log.e("SyncManager", "Failed to sync activity ${activity.activityId}: ${e.message}")
            }
        }

        val unsyncedResults = resultDao.getUnsyncedResults()
        for (res in unsyncedResults) {
            try {
                if (res.resultId.startsWith("TEMP-")) {
                    // ✅ ถ้ายังไม่เคยมี version ก่อนหน้า group id จะผูกกับ tempId ของตัวเองไปก่อน ต้องแก้เป็น realId ทีหลัง
                    val wasSelfGroup = res.resultGroupId == res.resultId
                    val body = buildResultBody(res).filterKeys { it != "result_id" }
                    val response = apiService.insertActivityResultMap(body)
                    if (response.isSuccessful) {
                        val realId = response.body()?.firstOrNull()?.resultId
                        if (realId != null && realId != res.resultId) {
                            val finalGroupId = if (wasSelfGroup) realId else res.resultGroupId
                            // ✅ ต้อง insert แถว realId ก่อน แล้วค่อยย้ายรูปมาที่ realId แล้วค่อยลบ tempId ทีหลัง
                            // เพราะ activity_result_photo มี FK CASCADE ไปยัง activity_result — ถ้าลบ tempId ก่อน รูปที่ยังผูกกับ tempId จะโดนลบไปด้วย
                            resultDao.insertResult(res.copy(resultId = realId, resultGroupId = finalGroupId, isSynced = true))
                            photoDao.updateResultId(res.resultId, realId)
                            resultDao.deleteResultById(res.resultId)
                            val pendingPhotos = photoDao.getPhotosByResultId(realId)
                            if (pendingPhotos.isNotEmpty()) {
                                try { apiService.addResultPhotos(pendingPhotos) } catch (_: Exception) {}
                            }
                            if (wasSelfGroup) {
                                try { apiService.updateActivityResult("eq.$realId", mapOf("result_group_id" to realId)) } catch (_: Exception) {}
                            }
                        } else {
                            resultDao.updateSyncStatus(res.resultId, true)
                        }
                    }
                } else {
                    val body = buildResultBody(res)
                    val response = apiService.upsertActivityResult(body)
                    if (response.isSuccessful) resultDao.updateSyncStatus(res.resultId, true)
                }
            } catch (e: Exception) {
                Log.e("SyncManager", "Failed to sync result ${res.resultId}: ${e.message}")
            }
        }

        Log.d("SyncManager", "Sync finished")
    }

    private fun buildResultBody(result: ActivityResult): Map<String, Any?> {
        val body = mutableMapOf<String, Any?>()
        body["result_id"]          = result.resultId
        body["appointment_id"]     = result.activityId
        body["project_code"]       = result.projectId
        body["created_by"]         = result.createdBy
        body["report_date"]        = result.reportDate
        body["new_status"]         = result.newStatus
        body["opportunity_score"]  = result.opportunityScore
        body["dm_involved"]        = result.dmInvolved
        body["is_proposal_sent"]   = result.isProposalSent
        body["proposal_date"]      = result.proposalDate
        body["competitor_count"]   = result.competitorCount
        body["response_speed"]     = result.responseSpeed
        body["deal_position"]      = result.dealPosition
        body["current_solution"]   = result.previousSolution
        body["counterparty_type"]  = result.counterpartyMultiplier
        body["note_summary"]       = result.summary
        body["photo_url"]          = result.photoUrl
        body["photo_taken_at"]     = result.photoTakenAt
        body["photo_lat"]          = result.photoLat
        body["photo_lng"]          = result.photoLng
        body["photo_device_model"] = result.photoDeviceModel
        body["version"]         = result.version
        body["is_latest"]       = result.isLatest
        body["result_group_id"] = result.resultGroupId
        return body.filterValues { it != null }
    }
}
