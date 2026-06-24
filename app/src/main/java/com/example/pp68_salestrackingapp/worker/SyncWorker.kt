package com.example.pp68_salestrackingapp.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.pp68_salestrackingapp.data.local.*
import com.example.pp68_salestrackingapp.data.model.ActivityResult
import com.example.pp68_salestrackingapp.data.remote.ApiService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val apiService: ApiService,
    private val customerDao: CustomerDao,
    private val projectDao: ProjectDao,
    private val contactDao: ContactDao,
    private val activityDao: ActivityDao,
    private val resultDao: ActivityResultDao
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d("SyncWorker", "Starting background sync...")

            // 1. Sync Customers (ลำดับที่ 1: แม่ของข้อมูลอื่น)
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

            // 2. Sync Contacts (ต้องทำหลัง customer เพื่อให้ custId เป็น real ID แล้ว)
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

            // 3. Sync Projects
            val unsyncedProjects = projectDao.getUnsyncedProjects()
            for (project in unsyncedProjects) {
                val body = mutableMapOf<String, Any?>(
                    "customer_code"           to project.custId,
                    "project_name"            to project.projectName,
                    "branch_code"             to project.branchId,
                    "billing_branch_id"       to project.billingBranchId,
                    "expected_value"          to project.expectedValue,
                    "project_status"          to project.projectStatus,
                    "start_date"              to project.startDate,
                    "closing_date"            to project.closingDate,
                    "project_lat"             to project.projectLat,
                    "project_long"            to project.projectLong,
                    "opportunity_score"       to project.opportunityScore,
                    "remark"                  to project.remark,
                    "create_by"               to project.createBy,
                    "created_at"              to project.createdAt
                ).filterValues { it != null }
                val response = apiService.addProject(body)
                if (response.isSuccessful) {
                    val realId = response.body()?.firstOrNull()?.projectId
                    if (realId != null && realId != project.projectId) {
                        projectDao.deleteProjectById(project.projectId)
                        projectDao.insertProject(project.copy(projectId = realId, isSynced = true))
                    } else {
                        projectDao.updateSyncStatus(project.projectId, true)
                    }
                }
            }

            // 4. Sync Activities (นัดหมาย)
            val unsyncedActivities = activityDao.getUnsyncedActivities()
            for (activity in unsyncedActivities) {
                // ล้างฟิลด์ local-only ก่อนส่ง
                val apiActivity = activity.copy(
                    projectName = null,
                    companyName = null,
                    contactName = null,
                    weeklyNote  = null
                )
                val response = apiService.addActivity(apiActivity)
                if (response.isSuccessful) activityDao.updateSyncStatus(activity.activityId, true)
            }

            // 5. Sync Activity Results (รายงานผล)
            val unsyncedResults = resultDao.getUnsyncedResults()
            for (res in unsyncedResults) {
                val body = buildResultBody(res)
                val response = apiService.upsertActivityResult(body)
                if (response.isSuccessful) resultDao.updateSyncStatus(res.resultId, true)
            }

            Log.d("SyncWorker", "Sync process finished successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e("SyncWorker", "Sync error: ${e.message}")
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
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
