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
import com.example.pp68_salestrackingapp.data.remote.CreateRequestPayloads
import com.example.pp68_salestrackingapp.data.remote.UploadApiService
import com.example.pp68_salestrackingapp.di.TokenManager
import com.example.pp68_salestrackingapp.worker.SyncWorker
import com.example.pp68_salestrackingapp.worker.DownloadSyncWorker
import com.example.pp68_salestrackingapp.worker.EndOfDaySyncReminderWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.concurrent.TimeUnit
import java.io.File
import java.security.MessageDigest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiService: ApiService,
    private val uploadApiService: UploadApiService,
    private val tokenManager: TokenManager,
    private val customerDao: CustomerDao,
    private val projectDao: ProjectDao,
    private val localIdMappingDao: LocalIdMappingDao,
    private val contactDao: ContactDao,
    private val activityDao: ActivityDao,
    private val resultDao: ActivityResultDao,
    private val photoDao: ActivityResultPhotoDao,
    private val appointmentContactDao: AppointmentContactDao,
    private val planItemDao: ActivityPlanItemDao,
    private val projectContactDao: ProjectContactDao,
    private val syncRejectionDao: SyncRejectionDao,
    private val attachmentOutboxDao: AttachmentOutboxDao
) {
    private companion object {
        // จำกัดเวลาของ worker หนึ่งรอบ; ถ้าเหลือ Room จะทำให้ผลเป็น retry และทำต่อด้วย backoff
        const val MAX_ROWS_PER_ENTITY_PER_RUN = 100
        const val PERIODIC_SYNC_WORK_NAME = "PeriodicDataSync"
    }

    private class RunTracker {
        var attempted: Int = 0
        var skipped: Int = 0
        private val failures = mutableMapOf<SyncFailureType, Int>()

        fun attempted() { attempted++ }
        fun skipped(count: Int = 1) { skipped += count }
        fun failed(type: SyncFailureType) {
            val normalized = if (type == SyncFailureType.UNKNOWN) SyncFailureType.LOCAL_FATAL else type
            failures[normalized] = (failures[normalized] ?: 0) + 1
        }
        fun httpFailed(code: Int) { failed(classifyHttpCode(code)) }
        fun exception(error: Throwable) { failed(classifySyncFailure(error)) }
        fun snapshot(): Map<SyncFailureType, Int> = failures.toMap()
        fun retryableFailureCount(): Int = failures.filterKeys {
            it in setOf(
                SyncFailureType.NETWORK, SyncFailureType.TIMEOUT, SyncFailureType.RATE_LIMITED,
                SyncFailureType.SERVER, SyncFailureType.DEPENDENCY
            )
        }.values.sum()
    }

    private fun <T> bounded(items: List<T>, tracker: RunTracker): List<T> {
        tracker.skipped((items.size - MAX_ROWS_PER_ENTITY_PER_RUN).coerceAtLeast(0))
        return items.take(MAX_ROWS_PER_ENTITY_PER_RUN)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private suspend fun syncAttachments(tracker: RunTracker) {
        val ownerId = tokenManager.getLocalDataOwner() ?: return
        val items = attachmentOutboxDao.getPending(ownerId, MAX_ROWS_PER_ENTITY_PER_RUN)
        for (original in items) {
            if (shouldSkip("attachment", original.operationId)) {
                tracker.skipped()
                continue
            }
            if (original.resultId.startsWith("TEMP-")) {
                tracker.skipped()
                tracker.failed(SyncFailureType.DEPENDENCY)
                continue
            }
            tracker.attempted()
            var item = original
            try {
                var remoteUrl = item.remoteUrl
                if (remoteUrl.isNullOrBlank()) {
                    val file = File(item.localPath)
                    check(file.isFile && file.length() == item.sizeBytes && sha256(file) == item.sha256) {
                        "ไฟล์รูปที่รอซิงค์สูญหายหรือถูกเปลี่ยนแปลง"
                    }
                    val fileBody = file.asRequestBody(item.mimeType.toMediaType())
                    val part = MultipartBody.Part.createFormData("photo", file.name, fileBody)
                    val parent = item.resultId.toRequestBody("text/plain".toMediaType())
                    val response = uploadApiService.uploadVisitPhoto(
                        item.operationId,
                        item.sha256,
                        parent,
                        part
                    )
                    if (!response.isSuccessful || response.body()?.photoUrl.isNullOrBlank()) {
                        noteOutcome("attachment", item.operationId, false, response.code(), tracker)
                        attachmentOutboxDao.markAttemptFailed(
                            item.operationId,
                            "upload_http_${response.code()}",
                            java.time.Instant.now().toString()
                        )
                        continue
                    }
                    remoteUrl = response.body()!!.photoUrl
                    val now = java.time.Instant.now().toString()
                    attachmentOutboxDao.markUploaded(
                        item.operationId,
                        com.example.pp68_salestrackingapp.data.model.AttachmentOutbox.STATE_PENDING_BIND,
                        remoteUrl,
                        now
                    )
                    item = item.copy(
                        state = com.example.pp68_salestrackingapp.data.model.AttachmentOutbox.STATE_PENDING_BIND,
                        remoteUrl = remoteUrl,
                        updatedAt = now
                    )
                }

                // Binding is deliberately separate. If it fails, remoteUrl remains in Room and the
                // next run retries this block without uploading the binary again.
                val boundUrl = requireNotNull(remoteUrl)
                val bind = apiService.addResultPhotos(
                    listOf(com.example.pp68_salestrackingapp.data.model.ActivityResultPhoto(item.resultId, item.photoOrder, boundUrl))
                )
                if (!bind.isSuccessful) {
                    noteOutcome("attachment", item.operationId, false, bind.code(), tracker)
                    attachmentOutboxDao.markAttemptFailed(item.operationId, "bind_http_${bind.code()}", java.time.Instant.now().toString())
                    continue
                }
                if (item.photoOrder == 0) {
                    val cover = apiService.updateActivityResult("eq.${item.resultId}", mapOf("photo_url" to boundUrl))
                    if (!cover.isSuccessful) {
                        noteOutcome("attachment", item.operationId, false, cover.code(), tracker)
                        attachmentOutboxDao.markAttemptFailed(item.operationId, "cover_http_${cover.code()}", java.time.Instant.now().toString())
                        continue
                    }
                }
                clearRejection("attachment", item.operationId)
                attachmentOutboxDao.finalizeBinding(item, boundUrl)
                AttachmentFileStore.delete(item.localPath)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                val classified = classifySyncFailure(error)
                val failureType = if (classified == SyncFailureType.UNKNOWN) SyncFailureType.LOCAL_FATAL else classified
                tracker.failed(failureType)
                attachmentOutboxDao.markAttemptFailed(
                    item.operationId,
                    error::class.java.simpleName.take(80),
                    java.time.Instant.now().toString()
                )
                if (failureType == SyncFailureType.LOCAL_FATAL) {
                    recordRejection(
                        "attachment",
                        item.operationId,
                        0,
                        "ไฟล์รูปในเครื่องสูญหาย เสียหาย หรืออ่านไม่ได้"
                    )
                }
            }
        }
    }

    private data class ChildSyncOutcome(
        val success: Boolean,
        val httpCode: Int? = null,
        val error: Throwable? = null
    )

    private suspend fun applyChildOutcome(
        entityType: String,
        id: String,
        outcome: ChildSyncOutcome,
        tracker: RunTracker
    ) {
        when {
            outcome.success -> Unit
            outcome.error != null -> tracker.exception(outcome.error)
            outcome.httpCode != null -> noteOutcome(entityType, id, false, outcome.httpCode, tracker)
            else -> tracker.failed(SyncFailureType.LOCAL_FATAL)
        }
    }
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
        Log.w("SyncManager", "$entityType ถูกปฏิเสธถาวร: HTTP $code")
        recordRejection(entityType, id, code, reason)
    }

    // เรียกทุกครั้งที่ได้คำตอบจาก server: สำเร็จ = ล้างประวัติการถูกปฏิเสธทิ้ง (ต้นเหตุถูกแก้แล้ว)
    // ไม่สำเร็จ = บันทึกไว้ถ้าเป็นการปฏิเสธถาวร ส่วน 5xx/ขาดเน็ตปล่อยผ่านให้ลองรอบหน้า
    private suspend fun noteOutcome(
        entityType: String,
        id: String,
        success: Boolean,
        code: Int,
        tracker: RunTracker
    ) {
        if (success) clearRejection(entityType, id) else {
            tracker.httpFailed(code)
            recordIfPermanent(entityType, id, code)
        }
    }

    // ข้ามเฉพาะแถวที่เคยถูกปฏิเสธถาวร "และลองซ้ำไปแล้วในรอบเปิดแอปนี้"
    private suspend fun shouldSkip(entityType: String, id: String): Boolean {
        val key = "$entityType:$id"
        val known = key in blockedRows || id in syncRejectionDao.idsOfType(entityType)
        if (!known) return false
        if (retriedThisSession.add(key)) return false // ให้โอกาสลองใหม่หนึ่งครั้ง
        return true
    }

    fun scheduleSync(trigger: SyncTrigger = SyncTrigger.SAVE) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10L, TimeUnit.SECONDS)
            .setInputData(workDataOf(SyncWorker.KEY_TRIGGER to trigger.name))
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
        SyncRuntime.queued(trigger)
    }

    /** คง method เดิมไว้ไม่ให้ caller เก่าพัง แต่ไม่รัน network ผูกกับ lifecycle อีกแล้ว */
    fun runSyncNow(scope: CoroutineScope) {
        scheduleSync(SyncTrigger.APP_FOREGROUND)
    }

    /**
     * ตาข่ายรองรับของ outbox — ไม่ใช่ทางส่งหลัก
     *
     * ทางหลักคือ one-time work ที่ผูกกับการบันทึกแต่ละครั้ง ซึ่งพอมีเน็ตก็รันเองโดยไม่ต้องเปิดแอป
     * แต่ถ้างานนั้นหายไปจากคิว (ตัวจัดการแบตของเครื่องบางยี่ห้อ force stop แอป ซึ่ง Android
     * ห้ามปลุกแอปที่ถูก force stop อีกเลย) จะไม่มีอะไรตามส่งให้จนกว่าผู้ใช้จะเปิดแอปเอง
     *
     * รอบนี้ไม่ได้แก้เคส force stop — ไม่มีอะไรแก้ได้ ต้องให้ผู้ใช้ปลดล็อกการจัดการแบตเอง
     * แต่กู้เคสอื่นที่งานหลุดจากคิวได้ และทำให้ "ค้างยาวเงียบ ๆ" กลายเป็น "ช้าสุด 6 ชั่วโมง"
     *
     * KEEP ไม่ใช่ UPDATE — เปิดแอปบ่อย ๆ ต้องไม่รีเซ็ตรอบใหม่ทุกครั้งจนไม่เคยครบรอบสักที
     */
    fun schedulePeriodicSync() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10L, TimeUnit.SECONDS)
            .setInputData(workDataOf(SyncWorker.KEY_TRIGGER to SyncTrigger.PERIODIC.name))
            .addTag("data_sync_tag")
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun scheduleDownload() {
        val request = OneTimeWorkRequestBuilder<DownloadSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10L, TimeUnit.SECONDS)
            .addTag("download_sync_tag")
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "InitialDownloadWorkName",
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun scheduleEndOfDayReminder() {
        val bangkok = java.time.ZoneId.of("Asia/Bangkok")
        val now = java.time.ZonedDateTime.now(bangkok)
        val todayAtTen = now.toLocalDate().atTime(22, 0).atZone(bangkok)
        // ถ้าเปิดแอปหลัง 22:00 ให้ตรวจทันที ไม่เลื่อนการเตือนของวันนี้ไปวันพรุ่งนี้
        val initialDelay = if (now.isBefore(todayAtTen)) {
            java.time.Duration.between(now, todayAtTen)
        } else java.time.Duration.ZERO
        val request = PeriodicWorkRequestBuilder<EndOfDaySyncReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelay)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "EndOfDaySyncReminder",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
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
        val rejectedAttachments = rejectedIds("attachment")
        val ownerId = tokenManager.getLocalDataOwner()

        listOf(
            "ลูกค้า" to customerDao.getUnsyncedCustomers().count { it.custId !in rejectedCustomers },
            "ผู้ติดต่อ" to contactDao.getUnsyncedContacts().count { it.contactId !in rejectedContacts },
            "โครงการ" to projectDao.getUnsyncedProjects().count { it.projectId !in rejectedProjects },
            "นัดหมาย" to activityDao.getUnsyncedActivities().count { it.activityId !in rejectedActivities },
            "บันทึกผล" to resultDao.getUnsyncedResults().count { it.resultId !in rejectedResults },
            "เช็คลิสต์" to planItemDao.getUnsyncedAppointmentIds().count { it !in rejectedChecklist },
            "รูป" to (ownerId?.let {
                attachmentOutboxDao.getPending(it, Int.MAX_VALUE)
                    .count { item -> item.operationId !in rejectedAttachments }
            } ?: 0)
        ).filter { it.second > 0 }
    }

    /** Called only immediately before the owning Room data is explicitly discarded. */
    suspend fun discardPendingAttachmentFiles() = withContext(Dispatchers.IO) {
        val ownerId = tokenManager.getLocalDataOwner() ?: return@withContext
        attachmentOutboxDao.getPending(ownerId, Int.MAX_VALUE).forEach { item ->
            AttachmentFileStore.delete(item.localPath)
        }
    }

    // รายการที่เซิร์ฟเวอร์ปฏิเสธถาวร พร้อมเหตุผล — ใช้บอกผู้ใช้ตอน logout ว่าจะทิ้งอะไรไว้บ้าง
    suspend fun rejectedSummary(): List<com.example.pp68_salestrackingapp.data.model.SyncRejection> =
        withContext(Dispatchers.IO) { runCatching { syncRejectionDao.getAll() }.getOrDefault(emptyList()) }

    // ✅ doSync ถูกยิงจากสามทางที่ทับกันได้จริง: MainActivity.runSyncNow ทุกครั้งที่เปิดแอป,
    // SyncWorker ของ WorkManager และ AuthRepository ตอน login/logout ถ้าสองรอบวนแถว TEMP- ชุดเดียวกัน
    // พร้อมกัน ทั้งคู่จะ POST แถวเดิม = ข้อมูลซ้ำบน server (ต้นเหตุเดียวกับ duplicate key ที่เคยเจอ)
    // ใช้ withLock ไม่ใช่ tryLock เพราะ AuthRepository เรียกแล้วต้องได้ผลจริงก่อน logout
    private val syncMutex = kotlinx.coroutines.sync.Mutex()

    internal suspend fun doSync(): SyncRunResult = syncMutex.withLock { doSyncLocked() }

    private suspend fun doSyncLocked(): SyncRunResult {
        val runId = java.util.UUID.randomUUID().toString()
        val startedAt = java.time.Instant.now().toString()
        val tracker = RunTracker()
        runCatching {
            AttachmentFileStore.cleanupOrphans(
                context,
                attachmentOutboxDao.getAllReferencedPaths().toSet()
            )
        }
        val pendingBefore = pendingSummary().sumOf { it.second }
        val rejectedBefore = rejectedSummary().size
        Log.d("SyncManager", "Starting sync...")
        tokenManager.getUserData()?.userId?.let { userId ->
            try {
                apiService.setAppContext(mapOf("user_id" to userId))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }

        val unsyncedCustomers = bounded(customerDao.getUnsyncedCustomers(), tracker)
        for (customer in unsyncedCustomers) {
            if (shouldSkip("customer", customer.custId)) { tracker.skipped(); continue }
            tracker.attempted()
            try {
                val body = CreateRequestPayloads.customer(customer)

                if (customer.custId.startsWith("TEMP-")) {
                    val response = apiService.addCustomer(body, customer.operationId)
                    noteOutcome("customer", customer.custId, response.isSuccessful, response.code(), tracker)
                    if (response.isSuccessful) {
                        val realCustId = response.body()?.firstOrNull()?.custId
                        if (realCustId != null && realCustId != customer.custId) {
                            // ทุกตารางที่อ้าง custId ต้องถูกชี้ใหม่ให้ครบ — project ไม่มี FK ฝั่ง server
                            // ถ้าตกหล่น มันจะ insert สำเร็จโดยชี้ไปหาลูกค้าที่ไม่มีอยู่จริง แบบเงียบ ๆ
                            localIdMappingDao.replaceTemporaryCustomer(
                                customer.custId,
                                customer.copy(custId = realCustId, isSynced = true)
                            )
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
                    noteOutcome("customer", customer.custId, response.isSuccessful, response.code(), tracker)
                    if (response.isSuccessful && response.body()?.isNotEmpty() == true) {
                        customerDao.updateSyncStatus(customer.custId, true)
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                tracker.exception(e)
                Log.e("SyncManager", "Customer sync failed: ${e::class.java.simpleName}")
            }
        }

        val unsyncedContacts = bounded(contactDao.getUnsyncedContacts(), tracker)
        for (pendingContact in unsyncedContacts) {
            if (shouldSkip("contact", pendingContact.contactId)) { tracker.skipped(); continue }
            val contact = localIdMappingDao.resolvePendingContactCustomer(pendingContact)
            if (contact == null) {
                tracker.skipped()
                tracker.failed(SyncFailureType.DEPENDENCY)
                continue
            }
            tracker.attempted()
            try {
                val fields = buildMap<String, Any?> {
                    put("customer_code", contact.custId)
                    put("customer_name", contact.customerName)
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
                noteOutcome("contact", contact.contactId, response.isSuccessful, response.code(), tracker)

                if (response.isSuccessful) {
                    val serverContact = response.body()?.firstOrNull()
                    if (serverContact != null && serverContact.contactId != contact.contactId) {
                        localIdMappingDao.replaceTemporaryContact(
                            contact.contactId,
                            serverContact.copy(isSynced = true)
                        )
                    } else {
                        contactDao.updateSyncStatus(contact.contactId, true)
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                tracker.exception(e)
                Log.e("SyncManager", "Contact sync failed: ${e::class.java.simpleName}")
            }
        }

        val unsyncedProjects = bounded(projectDao.getUnsyncedProjects(), tracker)
        for (pendingProject in unsyncedProjects) {
            if (shouldSkip("project", pendingProject.projectId)) { tracker.skipped(); continue }
            val project = localIdMappingDao.resolvePendingProjectCustomer(pendingProject)
            if (project == null) {
                tracker.skipped()
                tracker.failed(SyncFailureType.DEPENDENCY)
                continue
            }
            tracker.attempted()
            try {
                val isUpdate = !project.projectId.startsWith("TEMP-")
                val body = if (isUpdate) {
                    mutableMapOf<String, Any?>(
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
                } else {
                    CreateRequestPayloads.project(project).toMutableMap()
                }

                if (isUpdate) {
                    body["updated_at"] = java.time.Instant.now().toString()
                }

                val response = if (isUpdate) {
                    apiService.updateProject("eq.${project.projectId}", body)
                } else {
                    apiService.addProject(body, project.operationId)
                }

                if (!response.isSuccessful) {
                    Log.e("SyncManager", "Project sync failed: HTTP ${response.code()}")
                }
                noteOutcome("project", project.projectId, response.isSuccessful, response.code(), tracker)


                if (response.isSuccessful) {
                    val finalId = if (isUpdate) {
                        project.projectId
                    } else {
                        val realId = response.body()?.firstOrNull()?.projectId
                        if (realId != null && realId != project.projectId) {
                            val oldId = project.projectId
                            localIdMappingDao.replaceTemporaryProject(
                                oldId,
                                project.copy(projectId = realId, isSynced = false)
                            )
                            realId
                        } else {
                            project.projectId
                        }
                    }

                    // Sync contacts to remote
                    val contactOutcome = try {
                        // ลบฝั่ง server ก่อนเสมอ แม้ในเครื่องจะไม่เหลือผู้ติดต่อแล้ว — ไม่งั้นการลบ
                        // ออกจนหมดจะไม่ถูกส่งขึ้นไป แล้ว sync รอบถัดไปจะดึงของเก่ากลับลงมา
                        val localContacts = projectContactDao.getContactIdsByProject(finalId)
                        if (localContacts.any { it.startsWith("TEMP-") }) {
                            ChildSyncOutcome(false, error = IllegalStateException("Waiting for contact dependency"))
                        } else {
                        val deleted = apiService.deleteProjectContacts("eq.$finalId")
                        if (!deleted.isSuccessful) {
                            ChildSyncOutcome(false, httpCode = deleted.code())
                        } else if (localContacts.isNotEmpty()) {
                            val rows = localContacts.map { ProjectContact(finalId, it.trim()) }
                            val added = apiService.addProjectContacts(rows)
                            ChildSyncOutcome(added.isSuccessful, httpCode = added.code())
                        } else ChildSyncOutcome(true)
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.e("SyncManager", "Project-contact sync failed: ${e::class.java.simpleName}")
                        ChildSyncOutcome(false, error = e)
                    }
                    applyChildOutcome("project", finalId, contactOutcome, tracker)
                    projectDao.updateSyncStatus(finalId, contactOutcome.success)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                tracker.exception(e)
                Log.e("SyncManager", "Project sync failed: ${e::class.java.simpleName}")
            }
        }

        val unsyncedActivities = bounded(activityDao.getUnsyncedActivities(), tracker)
        for (pendingActivity in unsyncedActivities) {
            if (shouldSkip("activity", pendingActivity.activityId)) { tracker.skipped(); continue }
            // Never send a client-only project ID to the backend. If the parent sync failed, this
            // child remains pending; if a durable mapping exists, repair old/stale rows first.
            val activity = localIdMappingDao.resolvePendingActivityProject(pendingActivity)
            if (activity == null) {
                tracker.skipped()
                continue
            }
            tracker.attempted()
            try {
                if (activity.activityId.startsWith("TEMP-")) {
                    val body = CreateRequestPayloads.activity(activity)
                    val response = apiService.addActivityMap(body, activity.operationId)
                    noteOutcome("activity", activity.activityId, response.isSuccessful, response.code(), tracker)
                    // ✅ ถ้าไม่ได้ realId กลับมา ห้าม mark synced เด็ดขาด ปล่อยให้ลองใหม่รอบถัดไป
                    val realId = if (response.isSuccessful) response.body()?.firstOrNull()?.activityId else null
                    if (realId != null) {
                        if (realId != activity.activityId) {
                            localIdMappingDao.replaceTemporaryActivity(
                                activity.activityId,
                                activity.copy(activityId = realId, isSynced = false)
                            )
                            // alarm ถูกตั้งไว้ด้วย requestCode ที่คำนวณจาก TEMP- id — ถ้าไม่ย้ายมาที่ id จริง
                            // จะยกเลิกไม่ได้อีกเลย (เตือนต่อแม้ลบนัดหมายไปแล้ว) และกดแจ้งเตือนจะ deep link
                            // ไปหา id ที่ไม่มีอยู่ ส่วนการแก้เวลานัดก็จะไปตั้งซ้ำใต้ id ใหม่ กลายเป็นเตือนสองครั้ง
                            moveAlarmToRealId(activity, realId)
                        }
                        // ผู้เข้าร่วมต้อง "ขึ้นสำเร็จด้วย" ถึงจะนับว่านัดหมายนี้ซิงค์แล้ว ไม่งั้นรายชื่อหายเงียบ
                        // ตอนนี้แถวมี id จริงแล้ว รอบหน้าจะเข้าเส้น PATCH ไม่ใช่ POST ซ้ำ
                        val contactOutcome = pushAppointmentContacts(realId)
                        applyChildOutcome("activity", realId, contactOutcome, tracker)
                        activityDao.updateSyncStatus(realId, contactOutcome.success)
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
                        // B.4: แถวนี้อาจถูกแก้ไว้ตั้งแต่ก่อนเวลานัดแต่เพิ่งได้ส่งตอนนี้
                        // ถ้าไม่บอกเวลาที่แก้ไปด้วย server จะตัดสินจากเวลาที่รับคำขอ แล้วปฏิเสธงานที่ทำถูกต้อง
                        activity.planEditAt?.let {
                            put("client_modified_at", it)
                            put("client_time_trusted", activity.planEditTimeTrusted)
                        }
                    }
                    val response = apiService.updateActivity("eq.${activity.activityId}", patchBody)
                    noteOutcome("activity", activity.activityId, response.isSuccessful, response.code(), tracker)
                    if (response.isSuccessful && response.body()?.isNotEmpty() == true) {
                        // ✅ เส้นนี้เดิมไม่ส่งผู้เข้าร่วมเลย — แก้รายชื่อผู้เข้าร่วมตอนออฟไลน์แล้วรอบนี้
                        // PATCH ผ่านแค่ฟิลด์อื่น จะถูก mark synced ทั้งที่ appointment_contact
                        // ไม่เคยถูกส่งขึ้นไป (ตารางนี้ไม่มี is_synced ของตัวเอง จึงไม่มีใครมาเก็บให้ทีหลัง)
                        val contactOutcome = pushAppointmentContacts(activity.activityId)
                        applyChildOutcome("activity", activity.activityId, contactOutcome, tracker)
                        activityDao.updateSyncStatus(activity.activityId, contactOutcome.success)
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                tracker.exception(e)
                Log.e("SyncManager", "Activity sync failed: ${e::class.java.simpleName}")
            }
        }

        val unsyncedResults = bounded(resultDao.getUnsyncedResults(), tracker)
        for (pendingResult in unsyncedResults) {
            if (shouldSkip("result", pendingResult.resultId)) { tracker.skipped(); continue }
            val res = localIdMappingDao.resolvePendingResultParents(pendingResult)
            if (res == null) {
                tracker.skipped()
                tracker.failed(SyncFailureType.DEPENDENCY)
                continue
            }
            tracker.attempted()
            try {
                if (res.resultId.startsWith("TEMP-")) {
                    // ✅ ถ้ายังไม่เคยมี version ก่อนหน้า group id จะผูกกับ tempId ของตัวเองไปก่อน ต้องแก้เป็น realId ทีหลัง
                    val wasSelfGroup = res.resultGroupId == res.resultId
                    val body = CreateRequestPayloads.result(res, includeResultId = false)
                    val response = apiService.insertActivityResultMap(body, res.operationId)
                    val serverRow = if (response.isSuccessful) response.body()?.firstOrNull() else null
                    val realId = serverRow?.resultId
                    val accepted = response.isSuccessful && !realId.isNullOrBlank() && realId != res.resultId
                    // 2xx ที่ไม่คืน id จริงถือเป็น server failure แบบ retry ได้ ไม่ใช่ความผิดถาวรของข้อมูลผู้ใช้
                    noteOutcome(
                        "result",
                        res.resultId,
                        accepted,
                        if (response.isSuccessful && !accepted) 502 else response.code(),
                        tracker
                    )
                    if (accepted) {
                            val acceptedRow = requireNotNull(serverRow)
                            val acceptedId = requireNotNull(realId)
                            // server เป็นเจ้าของ version/result_group_id แล้ว (trigger generate_result_id)
                            // เอาค่าที่คืนมา ไม่งั้นแถวในเครื่องจะต่างจากบน server ถาวร
                            val finalGroupId = acceptedRow.resultGroupId
                                ?: if (wasSelfGroup) acceptedId else res.resultGroupId
                            // ✅ ต้อง insert แถว realId ก่อน แล้วค่อยย้ายรูปมาที่ realId แล้วค่อยลบ tempId ทีหลัง
                            // เพราะ activity_result_photo มี FK CASCADE ไปยัง activity_result — ถ้าลบ tempId ก่อน รูปที่ยังผูกกับ tempId จะโดนลบไปด้วย
                            localIdMappingDao.replaceTemporaryResult(
                                res.resultId,
                                res.copy(
                                    resultId = acceptedId,
                                    version = acceptedRow.version,
                                    resultGroupId = finalGroupId,
                                    isSynced = true
                                )
                            )
                            val pendingPhotos = photoDao.getPhotosByResultId(acceptedId)
                            if (pendingPhotos.isNotEmpty()) {
                                try {
                                    apiService.addResultPhotos(pendingPhotos)
                                } catch (e: Exception) {
                                    if (e is CancellationException) throw e
                                    tracker.exception(e)
                                }
                            }
                    }
                } else {
                    val body = CreateRequestPayloads.result(res, includeResultId = true)
                    val response = apiService.upsertActivityResult(body)
                    noteOutcome("result", res.resultId, response.isSuccessful, response.code(), tracker)
                    if (response.isSuccessful && response.body()?.isNotEmpty() == true) resultDao.updateSyncStatus(res.resultId, true)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                tracker.exception(e)
                Log.e("SyncManager", "Result sync failed: ${e::class.java.simpleName}")
            }
        }

        // Parent result must have a real server ID first. Binary upload and metadata binding then
        // progress independently so a binding failure never causes the image to be uploaded twice.
        syncAttachments(tracker)

        // checklist ต้องมาหลังนัดหมาย เพราะรายการที่ผูกกับ TEMP- id ต้องรอให้นัดหมายได้ id จริงก่อน
        // (updateAppointmentId ด้านบนย้าย appointmentId ให้แล้ว) ไม่งั้นจะส่งขึ้นไปผูกกับ id ที่ไม่มีจริง
        for (appointmentId in bounded(planItemDao.getUnsyncedAppointmentIds(), tracker)) {
            // ✅ แถว checklist กำพร้า (นัดหมายแม่ถูกลบไปแล้ว) ต้องลบทิ้ง ไม่ใช่ปล่อยค้าง:
            //   - id เป็น TEMP-: เดิม continue เฉยๆ ทุกรอบ = ไม่เคยพยายามส่ง และ is_synced ไม่เคยเป็น 1
            //   - id จริง: ส่งขึ้นไปก็ได้ 404 เพราะ server ไม่มีนัดหมายนั้นแล้ว
            // ทั้งสองกรณีทำให้ hasPendingChanges() เป็น true ตลอดกาล = logout ไม่ได้ทั้งที่เน็ตดี
            if (activityDao.getActivityById(appointmentId) == null) {
                Log.w("SyncManager", "ลบ checklist กำพร้าของนัดหมายที่ไม่มีอยู่แล้ว")
                planItemDao.deletePlanItemsByAppointmentId(appointmentId)
                tracker.attempted()
                continue
            }
            // นัดหมายแม่ยังอยู่แต่ยังไม่ได้ id จริง — รอรอบหน้าหลังนัดหมายซิงค์สำเร็จ (ปกติ)
            if (appointmentId.startsWith("TEMP-")) {
                tracker.skipped()
                tracker.failed(SyncFailureType.DEPENDENCY)
                continue
            }
            tracker.attempted()
            try {
                val items = planItemDao.getPlanItemsByAppointmentId(appointmentId)
                val code = activityRepositoryPush(appointmentId, items)
                noteOutcome("checklist", appointmentId, code == 200, code, tracker)
                if (code == 200) {
                    planItemDao.updateSyncStatusByAppointment(appointmentId, true)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                tracker.exception(e)
                Log.e("SyncManager", "Checklist sync failed: ${e::class.java.simpleName}")
            }
        }

        val pendingAfter = pendingSummary().sumOf { it.second }
        val rejectedAfter = rejectedSummary().size
        val succeeded = ((pendingBefore + rejectedBefore) - (pendingAfter + rejectedAfter)).coerceAtLeast(0)
        val failureTypes = tracker.snapshot()
        Log.d("SyncManager", "Sync finished run=$runId pending=$pendingAfter rejected=$rejectedAfter")
        return SyncRunResult(
            runId = runId,
            attempted = tracker.attempted,
            succeeded = succeeded,
            temporaryFailures = tracker.retryableFailureCount(),
            permanentFailures = failureTypes[SyncFailureType.PERMANENT] ?: 0,
            rejectedPending = rejectedAfter,
            skipped = tracker.skipped,
            stillPending = pendingAfter,
            failureTypes = failureTypes,
            startedAt = startedAt,
            finishedAt = java.time.Instant.now().toString()
        )
    }

    /**
     * ส่งผู้เข้าร่วมทั้งชุดของนัดหมายหนึ่ง — คืน true เมื่อฝั่ง server ตรงกับในเครื่องแล้ว
     *
     * ต้องยิง delete เสมอแม้ในเครื่องไม่เหลือผู้เข้าร่วมแล้ว ไม่งั้นการลบออกจนหมดจะไม่ถูกส่งขึ้นไป
     * แล้ว sync รอบถัดไปจะดึงรายชื่อเก่ากลับลงมา (บั๊กเดียวกับที่ saveAppointmentContacts เคยมี)
     */
    private suspend fun pushAppointmentContacts(appointmentId: String): ChildSyncOutcome {
        return try {
            val contacts = appointmentContactDao.getContactsByAppointmentId(appointmentId)
            if (contacts.any { it.contactId.startsWith("TEMP-") }) {
                return ChildSyncOutcome(false, error = IllegalStateException("Waiting for contact dependency"))
            }
            val deleted = apiService.deleteAppointmentContacts("eq.$appointmentId")
            if (!deleted.isSuccessful) return ChildSyncOutcome(false, httpCode = deleted.code())
            if (contacts.isEmpty()) return ChildSyncOutcome(true)
            val added = apiService.addAppointmentContacts(contacts)
            ChildSyncOutcome(added.isSuccessful, httpCode = added.code())
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("SyncManager", "Failed to sync appointment contacts: ${e::class.java.simpleName}")
            ChildSyncOutcome(false, error = e)
        }
    }

    // ย้าย alarm ที่ตั้งไว้ใต้ TEMP- id มาอยู่ใต้ id จริงหลังซิงค์สำเร็จ — requestCode คำนวณจาก
    // activityId จึงต้องยกเลิกของเดิมแล้วตั้งใหม่ ไม่ใช่แค่ตั้งใหม่ ไม่งั้นได้สองชุดซ้อนกัน
    private fun moveAlarmToRealId(activity: com.example.pp68_salestrackingapp.data.model.SalesActivity, realId: String) {
        runCatching {
            val scheduler = AppointmentAlarmScheduler(context)
            scheduler.cancelAlarm(activity.activityId)
            if (activity.isAppointment) {
                scheduler.scheduleAlarm(
                    activityId      = realId,
                    companyName     = activity.companyName ?: "สถานที่นัดหมาย",
                    topic           = activity.detail ?: "นัดหมายพบลูกค้า",
                    plannedDateStr  = activity.activityDate,
                    plannedTimeStr  = activity.plannedTime ?: ""
                )
            }
        }
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
            if (e is CancellationException) throw e
            0
        }
    }

}
