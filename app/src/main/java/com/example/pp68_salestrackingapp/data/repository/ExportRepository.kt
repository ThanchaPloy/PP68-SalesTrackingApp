package com.example.pp68_salestrackingapp.data.repository

import com.example.pp68_salestrackingapp.data.local.ActivityDao
import com.example.pp68_salestrackingapp.data.local.CustomerDao
import com.example.pp68_salestrackingapp.data.local.ProjectDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ExportRepository @Inject constructor(
    private val activityDao: ActivityDao,
    private val customerDao: CustomerDao,
    private val projectDao: ProjectDao
) {
    // ฟังก์ชันสร้างข้อความ CSV (suspend เพราะต้องรออ่านจาก Database)
    suspend fun generateActivityReportCsv(): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                // 1. ดึงข้อมูลล่าสุดแบบ One-shot (ใช้ .first() เพื่อดึงค่าปัจจุบันแล้วจบ)
                val activities = activityDao.getAllActivities().first()
                val customers = customerDao.getAllCustomers().first()
                val projects = projectDao.getAllProjects().first()

                // สร้าง Header ของไฟล์ Excel (CSV)
                val csvBuilder = StringBuilder()
                csvBuilder.append("Activity ID,Date,Customer Name,Project Name,Type,Status,Description\n")

                // 2. วนลูปจับคู่ข้อมูล
                activities.forEach { activity ->
                    // ✅ แก้จาก name เป็น companyName
                    val customerName = customers.find { it.custId == activity.customerId }?.companyName ?: "Unknown Customer"

                    // หาชื่อโปรเจคจาก ID
                    val projectName = projects.find { it.projectId == activity.projectId }?.projectName ?: "No Project"

                    // ✅ แก้จาก description เป็น detail
                    // (ป้องกันเครื่องหมายคอมม่า (,) ในข้อความไปตีกับรูปแบบ CSV ให้ครอบด้วย Double Quote)
                    val safeDescription = activity.detail?.replace("\"", "\"\"") ?: ""

                    // 3. ประกอบร่างแต่ละแถว
                    csvBuilder.append("${activity.activityId},")
                    csvBuilder.append("${activity.activityDate},")
                    csvBuilder.append("\"$customerName\",")
                    csvBuilder.append("\"$projectName\",")
                    csvBuilder.append("${activity.activityType},")
                    csvBuilder.append("${activity.status},")
                    csvBuilder.append("\"$safeDescription\"\n")
                }

                Result.success(csvBuilder.toString())
            } catch (e: Exception) {
                Result.failure(Exception("เกิดข้อผิดพลาดในการสร้างรายงาน: ${e.message}"))
            }
        }
    }
}