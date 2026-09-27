package com.example.pp68_salestrackingapp.data.repository

import android.util.Log
import com.example.pp68_salestrackingapp.utils.DealFactors
import com.example.pp68_salestrackingapp.utils.LossReasons
import com.example.pp68_salestrackingapp.utils.ProjectStages
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncManager @Inject constructor(
    private val customerRepo: CustomerRepository,
    private val contactRepo: ContactRepository,
    private val projectRepo: ProjectRepository,
    private val activityRepo: ActivityRepository,
    private val masterDataRepo: MasterDataRepository
) {
    suspend fun syncAll(userId: String, branchId: String) {
        supervisorScope {
            launch {
                try {
                    projectRepo.refreshProjects(userId)
                } catch (e: Exception) {
                    Log.e("SyncManager", "Failed to sync projects: ${e.message}")
                }
            }

            // Customers then contacts — contacts are scoped to customer_codes in Room
            launch {
                try {
                    if (branchId.isNotEmpty()) {
                        customerRepo.refreshCustomers(branchId)
                    }
                    contactRepo.refreshContacts()
                } catch (e: Exception) {
                    Log.e("SyncManager", "Failed to sync customers/contacts: ${e.message}")
                }
            }

            launch {
                try {
                    activityRepo.refreshActivities(userId)
                } catch (e: Exception) {
                    Log.e("SyncManager", "Failed to sync activities: ${e.message}")
                }
            }

            launch {
                try {
                    activityRepo.refreshResults(userId)
                } catch (e: Exception) {
                    Log.e("SyncManager", "Failed to sync results: ${e.message}")
                }
            }

            // W5a: master data ของ stage/เหตุผล — ล้มเหลวแล้วเงียบได้ เพราะ ProjectStages/LossReasons
            // มี fallback เป็นค่าที่ฝังมากับ APK อยู่แล้ว
            launch {
                try {
                    ProjectStages.applyServerData(masterDataRepo.getProjectStages())
                    LossReasons.applyServerData(masterDataRepo.getLossReasons())
                    DealFactors.applyServerData(masterDataRepo.getDealFactorQuestions())
                } catch (e: Exception) {
                    Log.e("SyncManager", "Failed to sync stage/loss-reason/deal-factor master data: ${e.message}")
                }
            }
        }
    }
}
