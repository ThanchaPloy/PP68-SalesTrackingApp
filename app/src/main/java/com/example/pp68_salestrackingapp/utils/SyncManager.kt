package com.example.pp68_salestrackingapp.utils

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.pp68_salestrackingapp.data.local.*
import com.example.pp68_salestrackingapp.data.model.ActivityResult
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
    private val appointmentContactDao: AppointmentContactDao
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
            val body = mutableMapOf<String, Any?>(
                "customer_name"         to customer.companyName,
                "gen_bus_posting_group" to customer.branchId,
                "cust_type"             to customer.custType,
                "address"               to customer.companyAddr,
                "company_lat"           to customer.companyLat,
                "company_long"          to customer.companyLong,
                "customer_status"       to customer.companyStatus,
                "create_date"           to customer.createdAt,
                "salesperson_code"      to customer.createdBy,
                "grade"                 to customer.grade
            ).filterValues { it != null }
            val response = apiService.addCustomer(body)
            if (response.isSuccessful) {
                val realCustId = response.body()?.firstOrNull()?.custId
                if (realCustId != null && realCustId != customer.custId) {
                    contactDao.updateCustIdForContacts(customer.custId, realCustId)
                    customerDao.deleteCustomerById(customer.custId)
                    customerDao.insertCustomer(customer.copy(custId = realCustId, isSynced = true))
                } else {
                    customerDao.updateSyncStatus(customer.custId, true)
                }
            }
        }

        val unsyncedContacts = contactDao.getUnsyncedContacts()
        for (contact in unsyncedContacts) {
            val fields = buildMap<String, Any?> {
                put("customer_code", contact.custId)
                contact.fullName?.let { put("contact_name", it) }
                contact.phoneNumber?.let { put("mobile_phone", it) }
                contact.email?.let { put("email", it) }
                contact.nickname?.let { put("nickname", it) }
                contact.position?.let { put("position", it) }
                contact.line?.let { put("line", it) }
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
        }

        val unsyncedProjects = projectDao.getUnsyncedProjects()
        for (project in unsyncedProjects) {
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
                    projectDao.deleteProjectById(project.projectId)
                    projectDao.insertProject(project.copy(projectId = realId, isSynced = true))
                    realId
                } else {
                    projectDao.updateSyncStatus(project.projectId, true)
                    project.projectId
                }
                project.createBy?.let { userId ->
                    try {
                        apiService.addProjectMembers(listOf(ProjectMemberInsertDto(finalId, userId, "owner")))
                    } catch (e: Exception) { /* retry next sync */ }
                }
            }
        }

        val unsyncedActivities = activityDao.getUnsyncedActivities()
        for (activity in unsyncedActivities) {
            if (activity.activityId.startsWith("TEMP-")) {
                val body = mutableMapOf<String, Any?>(
                    "emp_code"         to activity.userId,
                    "cust_code"        to activity.customerId,
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
                    if (realId != null && realId != activity.activityId) {
                        activityDao.insertActivity(activity.copy(activityId = realId, isSynced = true))
                        appointmentContactDao.updateAppointmentId(activity.activityId, realId)
                        activityDao.deleteActivityById(activity.activityId)
                    } else {
                        activityDao.updateSyncStatus(activity.activityId, true)
                    }
                }
            } else {
                val apiActivity = activity.copy(
                    projectName = null, companyName = null, contactName = null, weeklyNote = null
                )
                val response = apiService.addActivity(apiActivity)
                if (response.isSuccessful) activityDao.updateSyncStatus(activity.activityId, true)
            }
        }

        val unsyncedResults = resultDao.getUnsyncedResults()
        for (res in unsyncedResults) {
            if (res.resultId.startsWith("TEMP-")) {
                val body = buildResultBody(res).filterKeys { it != "result_id" }
                val response = apiService.insertActivityResultMap(body)
                if (response.isSuccessful) {
                    val realId = response.body()?.firstOrNull()?.resultId
                    if (realId != null && realId != res.resultId) {
                        resultDao.deleteResultById(res.resultId)
                        resultDao.insertResult(res.copy(resultId = realId, isSynced = true))
                    } else {
                        resultDao.updateSyncStatus(res.resultId, true)
                    }
                }
            } else {
                val body = buildResultBody(res)
                val response = apiService.upsertActivityResult(body)
                if (response.isSuccessful) resultDao.updateSyncStatus(res.resultId, true)
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
        return body.filterValues { it != null }
    }
}
