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
                val response = apiService.addCustomer(customer)
                if (response.isSuccessful) customerDao.updateSyncStatus(customer.custId, true)
            }

            // 2. Sync Contacts
            val unsyncedContacts = contactDao.getUnsyncedContacts()
            for (contact in unsyncedContacts) {
                val fields = buildMap<String, Any?> {
                    put("customer_code", contact.custId)
                    contact.fullName?.let { put("contact_name", it) }
                    contact.phoneNumber?.let { put("mobile_phone", it) }
                    contact.email?.let { put("email", it) }
                }
                val response = apiService.addContact(fields)
                if (response.isSuccessful) contactDao.updateSyncStatus(contact.contactId, true)
            }

            // 3. Sync Projects
            val unsyncedProjects = projectDao.getUnsyncedProjects()
            for (project in unsyncedProjects) {
                val response = apiService.addProject(project)
                if (response.isSuccessful) projectDao.updateSyncStatus(project.projectId, true)
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
