package com.example.pp68_salestrackingapp.utils

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.pp68_salestrackingapp.data.local.*
import com.example.pp68_salestrackingapp.data.model.ActivityPlanItem
import com.example.pp68_salestrackingapp.data.model.ActivityResult
import com.example.pp68_salestrackingapp.data.model.ChecklistInsertDto
import com.example.pp68_salestrackingapp.data.model.ProjectContact
import com.example.pp68_salestrackingapp.data.remote.ApiService
import com.example.pp68_salestrackingapp.di.TokenManager
import com.example.pp68_salestrackingapp.worker.SyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections
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
    private val syncRejectionDao: SyncRejectionDao
) {
    // ── แถวที่ server ปฏิเสธถาวร (ไม่ใช่ออฟไลน์) ────────────────────
    // แคชในหน่วยความจำของสิ่งที่อยู่ในตาราง sync_rejection อยู่แล้ว — กันไม่ให้ต้องถาม DB
    // ทุกครั้งที่ repository เรียก markBlocked แบบ fire-and-forget ตัวจริงที่ถืออยู่คือ Room
    private val blockedRows = Collections.synchronizedSet(mutableSetOf<String>())

    // แถวที่เคยถูกปฏิเสธถาวร จะถูกลองใหม่ "ครั้งเดียวต่อการเปิดแอปหนึ่งรอบ" — ไม่ใช่ทุกรอบ sync
    // (เปลืองและรังแกเซิร์ฟเวอร์) และไม่ใช่ไม่ลองเลย (ถ้าต้นเหตุถูกแก้ เช่น แอดมินให้สิทธิ์เพิ่ม
    // ข้อมูลจะค้างในเครื่องตลอดกาลแบบเงียบ ๆ) เก็บใน memory พอ เพราะความหมายคือ "รอบนี้ลองหรือยัง"
    private val retriedThisSession = Collections.synchronizedSet(mutableSetOf<String>())

    fun markBlocked(entityType: String, id: String) {
        blockedRows.add("$entityType:$id")
        rejectionScope.launch { recordRejection(entityType, id, 403, "ไม่มีสิทธิ์แก้ไขรายการนี้") }
    }

    // เรียกจาก repository ได้แบบ fire-and-forget (markBlocked) และจาก doSync แบบ suspend ตรง ๆ
    private val rejectionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    internal suspend fun recordRejection(entityType: String, id: String, httpCode: Int, reason: String?) {
        runCatching {
            syncRejectionDao.upsert(
                com.example.pp68_salestrackingapp.data.model.SyncRejection(
                    entityType = entityType,
                    entityId   = id,
                    httpCode   = httpCode,
                    reason     = reason,
                    rejectedAt = java.time.Instant.now().toString()
                )
            )
        }
    }

    private suspend fun clearRejection(entityType: String, id: String) {
        blockedRows.remove("$entityType:$id")
        runCatching { syncRejectionDao.clear(entityType, id) }
    }

    // 400/403/404/409 = เซิร์ฟเวอร์ตัดสินแล้วว่ารับไม่ได้ ลองใหม่กี่รอบก็เหมือนเดิม
    // ส่วน 5xx/timeout/ไม่มีเน็ต = ปัญหาชั่วคราว ต้องลองใหม่ ห้ามบันทึกเป็นการปฏิเสธถาวร
    private fun isPermanentRejection(code: Int): Boolean = code in setOf(400, 403, 404, 409, 422)

    private suspend fun recordIfPermanent(entityType: String, id: String, code: Int) {
        if (!isPermanentRejection(code)) return
        val reason = when (code) {
            403 -> "ไม่มีสิทธิ์แก้ไขรายการนี้"
            404 -> "ไม่พบรายการนี้บนเซิร์ฟเวอร์ (อาจถูกลบไปแล้ว)"
            409 -> "ข้อมูลชนกับรายการที่มีอยู่แล้ว"
            else -> "เซิร์ฟเวอร์ปฏิเสธข้อมูลนี้ (HTTP $code)"
        }
        Log.w("SyncManager", "$entityType:$id ถูกปฏิเสธถาวร — $reason")
        recordRejection(entityType, id, code, reason)
    }

    // เรียกทุกครั้งที่ได้คำตอบจาก server: สำเร็จ = ล้างประวัติการถูกปฏิเสธทิ้ง (ต้นเหตุถูกแก้แล้ว)
    // ไม่สำเร็จ = บันทึกไว้ถ้าเป็นการปฏิเสธถาวร ส่วน 5xx/ขาดเน็ตปล่อยผ่านให้ลองรอบหน้า
    private suspend fun noteOutcome(entityType: String, id: String, success: Boolean, code: Int) {
        if (success) clearRejection(entityType, id) else recordIfPermanent(entityType, id, code)
    }

    // ข้ามเฉพาะแถวที่เคยถูกปฏิเสธถาวร "และลองซ้ำไปแล้วในรอบเปิดแอปนี้"
    private suspend fun shouldSkip(entityType: String, id: String): Boolean {
        val key = "$entityType:$id"
        val known = key in blockedRows || id in syncRejectionDao.idsOfType(entityType)
        if (!known) return false
        if (retriedThisSession.add(key)) return false // ให้โอกาสลองใหม่หนึ่งครั้ง
        return true
    }

    fun scheduleSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10L, TimeUnit.SECONDS)
            .addTag("data_sync_tag")
            .build()

        // ✅ scheduleSync() ถูกเรียกแทบทุกครั้งที่แก้ข้อมูลออฟไลน์ (20+ จุดทั่ว repository) — REPLACE
        // จะยกเลิกงาน sync ที่กำลังรอ backoff อยู่แล้วรีเซ็ตนับใหม่ทุกครั้ง ถ้าผู้ใช้แก้ข้อมูลถี่ๆ
        // sync จริงอาจไม่มีโอกาสรันจบเลย KEEP ปล่อยงานที่ค้างอยู่ให้ทำต่อ (ไม่ว่าจะรันอยู่หรือรอ
        // backoff) ส่วนงานที่จบไปแล้ว (สำเร็จ/ล้มเหลวจนหมด retry) จะไม่ถูก KEEP บล็อก จะ enqueue ใหม่ปกติ
        WorkManager.getInstance(context).enqueueUniqueWork(
            "DataSyncWorkName",
            ExistingWorkPolicy.KEEP,
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

    suspend fun hasPendingChanges(): Boolean = withContext(Dispatchers.IO) {
        pendingSummary().isNotEmpty()
    }

    // สรุปว่ายังค้างอะไรอยู่บ้าง — เดิม hasPendingChanges คืนแค่ true/false ทำให้ตอน logout ไม่ผ่าน
    // ผู้ใช้เห็นแค่ "ยังไม่ได้ซิงค์ กรุณาเชื่อมต่ออินเทอร์เน็ต" ซึ่งชี้ทางผิดเมื่อเน็ตดีอยู่แล้ว และไม่มี
    // ใครรู้ว่าแถวไหนค้าง ต้องมานั่งเดา — คืนรายการชนิด+จำนวน เอาไปทั้งแสดงและ log
    // นับเฉพาะงานที่ "ยังมีโอกาสส่งสำเร็จ" — แถวที่เซิร์ฟเวอร์ปฏิเสธถาวรไม่นับ เพราะบล็อก logout
    // ด้วยของพวกนั้นไม่ช่วยอะไร ต่อให้รอจนเน็ตดีแค่ไหนมันก็ไม่ผ่าน (ดู rejectedSummary)
    suspend fun pendingSummary(): List<Pair<String, Int>> = withContext(Dispatchers.IO) {
        // ดึงรายการที่ถูกปฏิเสธมาก่อนทีเดียวต่อชนิด แล้วค่อยกรอง — ถูกกว่าถาม DB รายแถว
        suspend fun rejectedIds(type: String): Set<String> =
            runCatching { syncRejectionDao.idsOfType(type).toSet() }.getOrDefault(emptySet())

        val rejectedCustomers = rejectedIds("customer")
        val rejectedContacts  = rejectedIds("contact")
        val rejectedProjects  = rejectedIds("project")
        val rejectedActivities = rejectedIds("activity")
        val rejectedResults   = rejectedIds("result")
        val rejectedChecklist = rejectedIds("checklist")

        listOf(
            "ลูกค้า" to customerDao.getUnsyncedCustomers().count { it.custId !in rejectedCustomers },
            "ผู้ติดต่อ" to contactDao.getUnsyncedContacts().count { it.contactId !in rejectedContacts },
            "โครงการ" to projectDao.getUnsyncedProjects().count { it.projectId !in rejectedProjects },
            "นัดหมาย" to activityDao.getUnsyncedActivities().count { it.activityId !in rejectedActivities },
            "บันทึกผล" to resultDao.getUnsyncedResults().count { it.resultId !in rejectedResults },
            "เช็คลิสต์" to planItemDao.getUnsyncedAppointmentIds().count { it !in rejectedChecklist }
        ).filter { it.second > 0 }
    }

    // รายการที่เซิร์ฟเวอร์ปฏิเสธถาวร พร้อมเหตุผล — ใช้บอกผู้ใช้ตอน logout ว่าจะทิ้งอะไรไว้บ้าง
    suspend fun rejectedSummary(): List<com.example.pp68_salestrackingapp.data.model.SyncRejection> =
        withContext(Dispatchers.IO) { runCatching { syncRejectionDao.getAll() }.getOrDefault(emptyList()) }

    internal suspend fun doSync() {
        Log.d("SyncManager", "Starting sync...")
        tokenManager.getUserData()?.userId?.let { userId ->
            try { apiService.setAppContext(mapOf("user_id" to userId)) } catch (_: Exception) {}
        }

        val unsyncedCustomers = customerDao.getUnsyncedCustomers()
        for (customer in unsyncedCustomers) {
            if (shouldSkip("customer", customer.custId)) continue
            try {
                val body = mutableMapOf<String, Any?>(
                    "customer_name"         to customer.companyName,
                    "gen_bus_posting_group" to customer.branchId,
                    "cust_type"             to customer.custType,
                    "address"               to customer.companyAddr,
                    "latitude"              to customer.companyLat,
                    "longitude"             to customer.companyLong,
                    "customer_status"       to customer.companyStatus,
                    "create_date"           to customer.createdAt,
                    "created_at"            to customer.createdAt,
                    "create_by"             to customer.createdBy,
                    "salesperson_code"      to customer.createdBy,
                    "grade"                 to customer.grade,
                    "vat_registration_no"   to customer.vatRegistrationNo
                ).filterValues { it != null }

                if (customer.custId.startsWith("TEMP-")) {
                    val response = apiService.addCustomer(body)
                    noteOutcome("customer", customer.custId, response.isSuccessful, response.code())
                    if (response.isSuccessful) {
                        val realCustId = response.body()?.firstOrNull()?.custId
                        if (realCustId != null && realCustId != customer.custId) {
                            // ทุกตารางที่อ้าง custId ต้องถูกชี้ใหม่ให้ครบ — project ไม่มี FK ฝั่ง server
                            // ถ้าตกหล่น มันจะ insert สำเร็จโดยชี้ไปหาลูกค้าที่ไม่มีอยู่จริง แบบเงียบ ๆ
                            contactDao.updateCustIdForContacts(customer.custId, realCustId)
                            activityDao.updateCustIdForActivities(customer.custId, realCustId)
                            projectDao.updateCustIdForProjects(customer.custId, realCustId)
                            customerDao.deleteCustomerById(customer.custId)
                            customerDao.insertCustomer(customer.copy(custId = realCustId, isSynced = true))
                        } else {
                            customerDao.updateSyncStatus(customer.custId, true)
                        }
                    }
                } else {
                    val response = if (customer.isLead) {
                        apiService.updateLeadCustomer("eq.${customer.custId}", body)
                    } else {
                        apiService.updateCustomer("eq.${customer.custId}", body)
                    }
                    noteOutcome("customer", customer.custId, response.isSuccessful, response.code())
                    if (response.isSuccessful && response.body()?.isNotEmpty() == true) {
                        customerDao.updateSyncStatus(customer.custId, true)
                    }
                }
            } catch (e: Exception) {
                Log.e("SyncManager", "Failed to sync customer ${customer.custId}: ${e.message}")
            }
        }

        val unsyncedContacts = contactDao.getUnsyncedContacts()
        for (contact in unsyncedContacts) {
            if (shouldSkip("contact", contact.contactId)) continue
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
                val response = if (contact.contactId.startsWith("TEMP-")) {
                    apiService.addContact(fields)
                } else {
                    apiService.updateContact("eq.${contact.contactId}", fields)
                }
                noteOutcome("contact", contact.contactId, response.isSuccessful, response.code())

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
            if (shouldSkip("project", project.projectId)) continue
            try {
                val isUpdate = !project.projectId.startsWith("TEMP-")
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
                    "loss_reason"       to project.lossReason,
                    "loss_reason_note"  to project.lossReasonNote
                ).filterValues { it != null }.toMutableMap()

                if (!isUpdate) {
                    body["create_by"] = project.createBy
                    body["created_at"] = project.createdAt
                } else {
                    body["updated_at"] = java.time.Instant.now().toString()
                }

                val response = if (isUpdate) {
                    apiService.updateProject("eq.${project.projectId}", body)
                } else {
                    apiService.addProject(body)
                }

                if (!response.isSuccessful) {
                    Log.e("SyncManager", "Project sync failed ${response.code()}: custId=${project.custId} err=${response.errorBody()?.string()}")
                }
                noteOutcome("project", project.projectId, response.isSuccessful, response.code())


                if (response.isSuccessful) {
                    val finalId = if (isUpdate) {
                        projectDao.updateSyncStatus(project.projectId, true)
                        project.projectId
                    } else {
                        val realId = response.body()?.firstOrNull()?.projectId
                        if (realId != null && realId != project.projectId) {
                            val oldId = project.projectId
                            activityDao.updateProjectIdForActivities(oldId, realId)
                            projectDao.insertProject(project.copy(projectId = realId, isSynced = true))
                            projectContactDao.updateProjectId(oldId, realId)
                            projectDao.deleteProjectById(oldId)
                            realId
                        } else {
                            projectDao.updateSyncStatus(project.projectId, true)
                            project.projectId
                        }
                    }

                    // Sync contacts to remote
                    try {
                        // ลบฝั่ง server ก่อนเสมอ แม้ในเครื่องจะไม่เหลือผู้ติดต่อแล้ว — ไม่งั้นการลบ
                        // ออกจนหมดจะไม่ถูกส่งขึ้นไป แล้ว sync รอบถัดไปจะดึงของเก่ากลับลงมา
                        val localContacts = projectContactDao.getContactIdsByProject(finalId)
                        apiService.deleteProjectContacts("eq.$finalId")
                        if (localContacts.isNotEmpty()) {
                            val rows = localContacts.map { ProjectContact(finalId, it.trim()) }
                            apiService.addProjectContacts(rows)
                        }
                    } catch (e: Exception) {
                        Log.e("SyncManager", "Failed to sync project contacts for $finalId: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e("SyncManager", "Failed to sync project ${project.projectId}: ${e.message}")
            }
        }

        val unsyncedActivities = activityDao.getUnsyncedActivities()
        for (activity in unsyncedActivities) {
            if (shouldSkip("activity", activity.activityId)) continue
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
                    noteOutcome("activity", activity.activityId, response.isSuccessful, response.code())
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
                        // ✅ ไม่งั้นแก้นัดหมายให้ผูก/เปลี่ยนโครงการตอนออฟไลน์ แล้ว retry รอบนี้ผ่านแค่
                        // ฟิลด์อื่น จะถูก mark synced ทั้งที่ project_code/cust_code ไม่เคยถูกส่งเลย
                        // หายถาวรเงียบๆ ไม่มีโอกาสลองใหม่อีก
                        activity.projectId?.let { put("project_code", it) }
                        if (activity.customerId != "CST-UNKNOWN") activity.customerId?.let { put("cust_code", it) }
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
                    noteOutcome("activity", activity.activityId, response.isSuccessful, response.code())
                    if (response.isSuccessful && response.body()?.isNotEmpty() == true) {
                        activityDao.updateSyncStatus(activity.activityId, true)
                    }
                }
            } catch (e: Exception) {
                Log.e("SyncManager", "Failed to sync activity ${activity.activityId}: ${e.message}")
            }
        }

        val unsyncedResults = resultDao.getUnsyncedResults()
        for (res in unsyncedResults) {
            if (shouldSkip("result", res.resultId)) continue
            try {
                if (res.resultId.startsWith("TEMP-")) {
                    // ✅ ถ้ายังไม่เคยมี version ก่อนหน้า group id จะผูกกับ tempId ของตัวเองไปก่อน ต้องแก้เป็น realId ทีหลัง
                    val wasSelfGroup = res.resultGroupId == res.resultId
                    val body = buildResultBody(res).filterKeys { it != "result_id" }
                    val response = apiService.insertActivityResultMap(body)
                    noteOutcome("result", res.resultId, response.isSuccessful, response.code())
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
                                val backfilled = try {
                                    apiService.updateActivityResult("eq.$realId", mapOf("result_group_id" to realId)).isSuccessful
                                } catch (_: Exception) { false }
                                // ไม่งั้นแถวนี้จะเหลือ result_group_id เป็น tempId เดิมตลอดไปเพราะ isSynced
                                // ถูก mark true ไปแล้วด้านบน จะไม่มีวันถูกหยิบมา retry อีกเลย
                                if (!backfilled) resultDao.updateSyncStatus(realId, false)
                            }
                        } else {
                            resultDao.updateSyncStatus(res.resultId, true)
                        }
                    }
                } else {
                    val body = buildResultBody(res)
                    val response = apiService.upsertActivityResult(body)
                    noteOutcome("result", res.resultId, response.isSuccessful, response.code())
                    if (response.isSuccessful && response.body()?.isNotEmpty() == true) resultDao.updateSyncStatus(res.resultId, true)
                }
            } catch (e: Exception) {
                Log.e("SyncManager", "Failed to sync result ${res.resultId}: ${e.message}")
            }
        }

        // checklist ต้องมาหลังนัดหมาย เพราะรายการที่ผูกกับ TEMP- id ต้องรอให้นัดหมายได้ id จริงก่อน
        // (updateAppointmentId ด้านบนย้าย appointmentId ให้แล้ว) ไม่งั้นจะส่งขึ้นไปผูกกับ id ที่ไม่มีจริง
        for (appointmentId in planItemDao.getUnsyncedAppointmentIds()) {
            // ✅ แถว checklist กำพร้า (นัดหมายแม่ถูกลบไปแล้ว) ต้องลบทิ้ง ไม่ใช่ปล่อยค้าง:
            //   - id เป็น TEMP-: เดิม continue เฉยๆ ทุกรอบ = ไม่เคยพยายามส่ง และ is_synced ไม่เคยเป็น 1
            //   - id จริง: ส่งขึ้นไปก็ได้ 404 เพราะ server ไม่มีนัดหมายนั้นแล้ว
            // ทั้งสองกรณีทำให้ hasPendingChanges() เป็น true ตลอดกาล = logout ไม่ได้ทั้งที่เน็ตดี
            if (activityDao.getActivityById(appointmentId) == null) {
                Log.w("SyncManager", "ลบ checklist กำพร้าของนัดหมายที่ไม่มีอยู่แล้ว: $appointmentId")
                planItemDao.deletePlanItemsByAppointmentId(appointmentId)
                continue
            }
            // นัดหมายแม่ยังอยู่แต่ยังไม่ได้ id จริง — รอรอบหน้าหลังนัดหมายซิงค์สำเร็จ (ปกติ)
            if (appointmentId.startsWith("TEMP-")) continue
            try {
                val items = planItemDao.getPlanItemsByAppointmentId(appointmentId)
                val code = activityRepositoryPush(appointmentId, items)
                noteOutcome("checklist", appointmentId, code == 200, code)
                if (code == 200) {
                    planItemDao.updateSyncStatusByAppointment(appointmentId, true)
                }
            } catch (e: Exception) {
                Log.e("SyncManager", "Failed to sync checklist for $appointmentId: ${e.message}")
            }
        }

        Log.d("SyncManager", "Sync finished")
    }

    /**
     * ส่ง checklist ทั้งชุดของนัดหมายหนึ่ง — ลบของเก่าบน server แล้วใส่ชุดใหม่
     * ฝั่ง backend ทำ upsert ให้แล้ว การส่งซ้ำจึงไม่ชน primary key
     *
     * คืน HTTP code เพื่อให้ผู้เรียกแยกออกว่าล้มเหลวเพราะถูกปฏิเสธถาวร (4xx) หรือแค่เน็ตไม่ดี
     * 200 = สำเร็จทั้งชุด, 0 = ยิงไม่ถึง server (exception) ซึ่งต้องลองใหม่เสมอ
     */
    private suspend fun activityRepositoryPush(appointmentId: String, items: List<ActivityPlanItem>): Int {
        return try {
            val deleted = apiService.deleteChecklistByAppointment("eq.$appointmentId")
            if (!deleted.isSuccessful) return deleted.code()
            if (items.isEmpty()) return 200
            val dtos = items.map {
                ChecklistInsertDto(appointmentId = appointmentId, masterId = it.masterId, isDone = it.isDone, actName = it.actName)
            }
            val inserted = apiService.insertChecklist(dtos)
            if (inserted.isSuccessful) 200 else inserted.code()
        } catch (e: Exception) {
            0
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
        body["version"]         = result.version
        body["is_latest"]       = result.isLatest
        body["result_group_id"] = result.resultGroupId
        return body.filterValues { it != null }
    }
}
