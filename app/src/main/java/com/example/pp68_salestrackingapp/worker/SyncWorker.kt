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
            Log.d("SyncWorker", "Starting sync process...")

            // 1. Sync Customers ก่อน (สำคัญที่สุด เพื่อเลี่ยง Error 23503)
            val unsyncedCustomers = customerDao.getUnsyncedCustomers()
            for (customer in unsyncedCustomers) {
                val response = apiService.addCustomer(customer)
                if (response.isSuccessful) {
                    customerDao.updateSyncStatus(customer.custId, true)
                }
            }

            // 2. Sync Contacts
            val unsyncedContacts = contactDao.getUnsyncedContacts()
            for (contact in unsyncedContacts) {
                val response = apiService.addContact(contact)
                if (response.isSuccessful) {
                    contactDao.updateSyncStatus(contact.contactId, true)
                }
            }

            // 3. Sync Projects
            val unsyncedProjects = projectDao.getUnsyncedProjects()
            for (project in unsyncedProjects) {
                val response = apiService.addProject(project)
                if (response.isSuccessful) {
                    projectDao.updateSyncStatus(project.projectId, true)
                }
            }

            // 4. Sync Activities
            val unsyncedActivities = activityDao.getUnsyncedActivities()
            for (activity in unsyncedActivities) {
                val response = apiService.addActivity(activity)
                if (response.isSuccessful) {
                    activityDao.updateSyncStatus(activity.activityId, true)
                }
            }

            // 5. Sync Activity Results
            val unsyncedResults = resultDao.getUnsyncedResults()
            for (res in unsyncedResults) {
                val body = buildResultBody(res)
                val response = apiService.upsertActivityResult(body)
                if (response.isSuccessful) {
                    resultDao.updateSyncStatus(res.resultId, true)
                }
            }

            Log.d("SyncWorker", "Sync completed successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e("SyncWorker", "Sync failed: ${e.message}")
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private fun buildResultBody(result: ActivityResult): Map<String, Any?> {
        val body = mutableMapOf<String, Any?>()
        body["result_id"]      = result.resultId
        body["appointment_id"] = result.activityId
        result.projectId?.let    { body["project_code"] = it }
        result.createdBy?.let    { body["created_by"] = it }
        result.reportDate?.let   { body["report_date"] = it }
        result.newStatus?.let    { body["new_status"] = it }
        result.opportunityScore?.let { body["opportunity_score"] = it }
        body["dm_involved"]      = result.dmInvolved
        body["is_proposal_sent"] = result.isProposalSent
        result.summary?.let      { body["note_summary"] = it }
        return body
    }
}
