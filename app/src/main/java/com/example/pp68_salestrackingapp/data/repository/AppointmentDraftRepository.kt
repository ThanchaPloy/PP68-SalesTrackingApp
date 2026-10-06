package com.example.pp68_salestrackingapp.data.repository

import androidx.room.withTransaction
import com.example.pp68_salestrackingapp.data.local.AppDatabase
import com.example.pp68_salestrackingapp.data.model.AppointmentDraft
import com.example.pp68_salestrackingapp.di.TokenManager
import com.example.pp68_salestrackingapp.utils.DraftStore
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** ผลของการบันทึกร่าง — เต็มโควตาไม่ใช่ error ทั่วไป ผู้ใช้ต้องได้เลือกเองว่าจะลบอันไหน */
sealed interface DraftSaveResult {
    data class Saved(val draftId: String) : DraftSaveResult
    data class LimitReached(val max: Int) : DraftSaveResult
    data class Failed(val error: Throwable) : DraftSaveResult
}

/**
 * ฉบับร่างนัดหมายหลายรายการ (แผนงาน C.2)
 *
 * ขอบเขตของบัญชีมาจาก [TokenManager] ที่นี่ที่เดียว ผู้เรียกไม่ต้องรู้จัก owner key และไม่มีทาง
 * ส่งคีย์ของคนอื่นเข้ามาได้
 */
@Singleton
class AppointmentDraftRepository @Inject constructor(
    private val database: AppDatabase,
    private val tokenManager: TokenManager,
    private val draftStore: DraftStore,
    private val clock: Clock
) {
    private val dao get() = database.appointmentDraftDao()

    private fun ownerKeyOrNull(): String? =
        tokenManager.getUserData()?.userId?.takeIf { it.isNotBlank() }?.let { syncAccountKey(it) }

    fun observeDrafts(): Flow<List<AppointmentDraft>> =
        ownerKeyOrNull()?.let { dao.observeByOwner(it) } ?: flowOf(emptyList())

    suspend fun getDraft(draftId: String): AppointmentDraft? =
        ownerKeyOrNull()?.let { dao.getById(it, draftId) }

    suspend fun countDrafts(): Int = ownerKeyOrNull()?.let { dao.countForOwner(it) } ?: 0

    /**
     * บันทึกร่าง — [draftId] ที่ส่งมาคือการเขียนทับร่างเดิม ไม่ใช่สร้างสำเนาใหม่
     *
     * การนับโควตาต้องอยู่ใน transaction เดียวกับการเขียน ไม่งั้นกดบันทึกรัว ๆ สองครั้งพร้อมกัน
     * จะนับได้ 20 ทั้งคู่แล้วเขียนทะลุเป็น 21
     */
    suspend fun saveDraft(
        draftId: String?,
        schemaVersion: Int,
        payloadJson: String,
        title: String? = null,
        plannedDate: String? = null,
        plannedTime: String? = null,
        projectId: String? = null,
        projectNameSnapshot: String? = null,
        customerId: String? = null,
        customerNameSnapshot: String? = null
    ): DraftSaveResult {
        val ownerKey = ownerKeyOrNull()
            ?: return DraftSaveResult.Failed(IllegalStateException("ยังไม่ได้เข้าสู่ระบบ"))
        val now = Instant.now(clock)
        return runCatching {
            database.withTransaction {
                val existing = draftId?.let { dao.getById(ownerKey, it) }
                if (existing == null && dao.countForOwner(ownerKey) >= MAX_DRAFTS_PER_ACCOUNT) {
                    return@withTransaction DraftSaveResult.LimitReached(MAX_DRAFTS_PER_ACCOUNT)
                }
                val id = existing?.draftId ?: draftId ?: UUID.randomUUID().toString()
                dao.upsert(
                    AppointmentDraft(
                        draftId = id,
                        ownerKey = ownerKey,
                        schemaVersion = schemaVersion,
                        title = title,
                        plannedDate = plannedDate,
                        plannedTime = plannedTime,
                        projectId = projectId,
                        projectNameSnapshot = projectNameSnapshot,
                        customerId = customerId,
                        customerNameSnapshot = customerNameSnapshot,
                        payloadJson = payloadJson,
                        // สร้างเมื่อไหร่ต้องไม่ขยับตอนแก้ ไม่งั้นอายุ 30 วันจะถูกต่อไปเรื่อย ๆ
                        createdAt = existing?.createdAt ?: now.toString(),
                        updatedAt = now.toString(),
                        expiresAt = now.plus(Duration.ofDays(EXPIRY_DAYS)).toString()
                    )
                )
                DraftSaveResult.Saved(id)
            }
        }.getOrElse { DraftSaveResult.Failed(it) }
    }

    suspend fun deleteDraft(draftId: String) {
        ownerKeyOrNull()?.let { dao.deleteById(it, draftId) }
    }

    /**
     * ลบของหมดอายุ — เรียกตอนเปิดหน้ารายการและหลัง sync ไม่ต้องมี worker แยก
     * เพราะงานนี้เบามากและไม่มีใครเดือดร้อนถ้าช้าไปหนึ่งรอบ
     */
    suspend fun deleteExpired(): Int =
        ownerKeyOrNull()?.let { dao.deleteExpired(it, Instant.now(clock).toString()) } ?: 0

    suspend fun deleteForOwner(ownerKey: String): Int = dao.deleteForOwner(ownerKey)

    /**
     * ย้ายร่างนัดหมายยุค SharedPreferences เข้ามาใน Room (แผนงาน C.4)
     *
     * ย้ายได้เฉพาะเมื่อพิสูจน์ได้ว่าเป็นของบัญชีที่ล็อกอินอยู่ คือ localDataOwner ตรงกับผู้ใช้ปัจจุบัน
     * ถ้าพิสูจน์ไม่ได้ให้ลบทิ้ง ห้ามเอาไปแสดงกับบัญชีใหม่ เพราะร่างของเซลส์คนก่อนอาจมีชื่อลูกค้า
     * และรายละเอียดงานที่คนถัดไปไม่ควรเห็น
     *
     * เรียกซ้ำได้ เพราะลบ key เดิมทิ้งทุกกรณีหลังทำงานเสร็จ
     */
    suspend fun migrateLegacyDrafts(): Int {
        val legacy = draftStore.rawEntriesWithPrefix(LEGACY_PREFIX)
        if (legacy.isEmpty()) return 0

        val currentUser = tokenManager.getUserData()?.userId
        val owner = tokenManager.getLocalDataOwner()
        val provenOwner = currentUser != null && owner != null && currentUser == owner
        if (!provenOwner) {
            draftStore.clearWithPrefix(LEGACY_PREFIX)
            return 0
        }

        var moved = 0
        for ((_, payloadJson) in legacy) {
            val fields = runCatching { JsonParser.parseString(payloadJson).asJsonObject }.getOrNull()
            val result = saveDraft(
                draftId = null,
                schemaVersion = LEGACY_SCHEMA_VERSION,
                payloadJson = payloadJson,
                title = fields.stringOrNull("titleTopic"),
                plannedDate = fields.stringOrNull("plannedDate"),
                plannedTime = fields.stringOrNull("startTime"),
                projectId = fields.stringOrNull("selectedProjectId"),
                customerId = fields.stringOrNull("selectedCustomerId")
            )
            if (result is DraftSaveResult.Saved) moved++
            // เต็มโควตาแล้วหยุด แต่ยังลบ key เก่าทิ้ง ไม่วนพยายามซ้ำทุกครั้งที่เปิดหน้า
            if (result is DraftSaveResult.LimitReached) break
        }
        draftStore.clearWithPrefix(LEGACY_PREFIX)
        return moved
    }

    private fun JsonObject?.stringOrNull(name: String): String? =
        this?.get(name)?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }

    companion object {
        const val MAX_DRAFTS_PER_ACCOUNT = 20
        const val LEGACY_PREFIX = "create_appointment:"
        const val LEGACY_SCHEMA_VERSION = 1
        const val EXPIRY_DAYS = 30L
    }
}
