package com.example.pp68_salestrackingapp.data.repository

import com.example.pp68_salestrackingapp.data.local.ActivityDao
import com.example.pp68_salestrackingapp.data.local.CustomerDao
import com.example.pp68_salestrackingapp.data.local.ProjectDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

// Data Class สำหรับส่งสถิติไปให้ UI
data class DashboardSummary(
    val totalCustomers: Int,
    val activeProjects: Int,
    val completedActivities: Int
)

class DashboardRepository @Inject constructor(
    private val customerDao: CustomerDao,
    private val projectDao: ProjectDao,
    private val activityDao: ActivityDao
) {
    // 🌟 นำ Flow ของ 3 ตารางมารวมร่างกัน (Combine)
    fun getDashboardSummary(): Flow<DashboardSummary> {
        return combine(
            customerDao.getAllCustomers(),
            projectDao.getAllProjects(),
            activityDao.getAllActivities()
        ) { customers, projects, activities ->

            DashboardSummary(
                totalCustomers = customers.size,
                // สมมติว่านับเฉพาะโปรเจคที่มีคำว่า "Active" (ตัวพิมพ์เล็ก-ใหญ่ก็ได้)
                activeProjects = projects.count { it.projectStatus.equals("Active", ignoreCase = true) },
                // สมมติว่านับกิจกรรมที่ "Completed" ไปแล้ว
                completedActivities = activities.count { it.status.equals("Completed", ignoreCase = true) }
            )
        }
    }
}