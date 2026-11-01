package com.example.pp68_salestrackingapp.data.repository

import android.content.Context
import android.provider.CallLog
import com.example.pp68_salestrackingapp.data.local.CallLogDao
import com.example.pp68_salestrackingapp.data.model.CallLogSync
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** หนึ่งแถวดิบจาก ContentResolver ก่อนกรอง/แปลง — แยกออกมาเพื่อให้ [buildEntries] เทสต์ได้โดย
 *  ไม่ต้องพึ่ง android.provider.CallLog จริง (รีโปนี้ไม่มี Robolectric ตั้งแต่แรก) */
data class RawCallLogRow(val number: String, val dateMs: Long, val duration: Int)

// Mobile phase 2 ข้อ 2 — เขียนใหม่ทั้งตัว (ของเก่าถอดไปที่ commit 7e66070)
//
// ต่างจากของเก่า 2 จุดสำคัญ: (1) เขียนลง Room ก่อนเสมอ ไม่ยิง API ตรงจากที่นี่ — ให้ outbox เดิม
// (utils/SyncManager) จัดการส่งขึ้น server พร้อม retry/idempotency ที่มีอยู่แล้ว (2) กรองสายที่ตรง
// กับผู้ติดต่อในระบบ "ก่อน insert ลง Room" ไม่ใช่กรองตอน sync — ลดความเสี่ยงข้อมูลส่วนตัว (PDPA)
// ตั้งแต่จุดแรกที่อ่านจากเครื่อง ไม่ใช่แค่ตอนส่งขึ้น server
class CallLogRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val callLogDao: CallLogDao,
    private val customerRepo: CustomerRepository
) {

    /** อ่าน call log จากเครื่อง กรองเฉพาะที่ตรงกับผู้ติดต่อในระบบ แล้วเก็บลง Room ให้ outbox ส่งต่อ */
    suspend fun collectAndQueue() {
        withContext(Dispatchers.IO) {
            val contactPhoneMap = customerRepo.getAllContactPhoneMap() // phone_number -> cust_id
            if (contactPhoneMap.isEmpty()) return@withContext // ไม่มีผู้ติดต่อให้จับคู่ ไม่มีอะไรต้องเก็บ

            val rawRows = mutableListOf<RawCallLogRow>()
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DATE, CallLog.Calls.DURATION),
                null, null,
                "${CallLog.Calls.DATE} DESC"
            )
            cursor?.use { c ->
                val numberIdx   = c.getColumnIndex(CallLog.Calls.NUMBER)
                val dateIdx     = c.getColumnIndex(CallLog.Calls.DATE)
                val durationIdx = c.getColumnIndex(CallLog.Calls.DURATION)
                while (c.moveToNext()) {
                    val number = c.getString(numberIdx) ?: continue
                    rawRows.add(RawCallLogRow(number, c.getLong(dateIdx), c.getInt(durationIdx)))
                }
            }

            val entries = buildEntries(rawRows, contactPhoneMap)
            if (entries.isNotEmpty()) callLogDao.insertAll(entries)
        }
    }

    companion object {
        private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)

        // ตัดอักขระอื่นออกเหลือแค่ตัวเลข/+ ให้เบอร์ที่มี - หรือ space เทียบกับที่เก็บในระบบได้ตรงกัน
        private fun normalize(phone: String): String = phone.replace(Regex("[^0-9+]"), "")

        /**
         * ตรรกะจริงทั้งหมด (กรอง + normalize + สร้าง id) แยกจาก ContentResolver เพื่อเทสต์ได้
         *
         * เก็บเฉพาะสายที่ normalize แล้วตรงกับเบอร์ผู้ติดต่อในระบบเท่านั้น (ไม่ตรง = ข้าม ไม่เก็บ
         * เลย) — นี่คือจุด minimization จริง ไม่ใช่กรองทีหลังตอนจะส่งขึ้น server
         */
        fun buildEntries(rawRows: List<RawCallLogRow>, contactPhoneMap: Map<String, String>): List<CallLogSync> {
            val normalizedMap = contactPhoneMap.entries.associate { (phone, custId) -> normalize(phone) to custId }
            return rawRows.mapNotNull { row ->
                val normalized = normalize(row.number)
                val custId = normalizedMap[normalized] ?: return@mapNotNull null
                val startTime = isoFormat.format(Date(row.dateMs))
                val endTime   = isoFormat.format(Date(row.dateMs + row.duration * 1000L))
                // log_id คำนวณจากเบอร์+เวลาเริ่มเสมอ (ไม่ใช่สุ่มใหม่ทุกครั้ง) สายเดียวกันที่อ่านซ้ำ
                // (เปิดแอปหลายรอบ) จึงได้ id เดิม แล้วถูก IGNORE ตอน insert ไม่ซ้ำเข้าคิวส่งอีก
                val logId = "LOG-${"%08X".format((normalized + startTime).hashCode())}"
                CallLogSync(
                    logId       = logId,
                    custId      = custId,
                    phoneNumber = normalized,
                    startTime   = startTime,
                    endTime     = endTime,
                    duration    = row.duration
                )
            }
        }
    }
}
