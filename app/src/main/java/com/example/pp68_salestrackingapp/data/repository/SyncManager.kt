package com.example.pp68_salestrackingapp.data.repository

import android.util.Log
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncManager @Inject constructor(
    private val customerRepo: CustomerRepository,
    private val contactRepo: ContactRepository,
    private val projectRepo: ProjectRepository,
    private val activityRepo: ActivityRepository
) {
    /**
     * ดึงข้อมูลทั้งหมดจาก Server มาลง Local Database
     * ใช้ supervisorScope เพื่อให้งานแต่ละส่วนทำงานแยกกัน ถ้าส่วนหนึ่งเสีย ส่วนอื่นต้องยังทำงานได้
     */
    suspend fun syncAll(userId: String, branchId: String) {
        supervisorScope {
            // 1. ดึงข้อมูลโครงการ (สำคัญที่สุด)
            launch {
                try {
                    projectRepo.refreshProjects(userId)
                } catch (e: Exception) {
                    Log.e("SyncManager", "Failed to sync projects: ${e.message}")
                }
            }

            // 2. ดึงรายชื่อลูกค้าและผู้ติดต่อ
            launch {
                try {
                    if (branchId.isNotEmpty()) {
                        customerRepo.refreshCustomers(branchId)
                    }
                } catch (e: Exception) {
                    Log.e("SyncManager", "Failed to sync customers: ${e.message}")
                }
            }

            launch {
                try {
                    contactRepo.refreshContacts(userId)
                } catch (e: Exception) {
                    Log.e("SyncManager", "Failed to sync contacts: ${e.message}")
                }
            }

            // 3. ดึงข้อมูลงานนัดหมายและบันทึกผล (ส่วนที่ผู้ใช้แจ้งว่าหาย)
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
        }
    }
}
