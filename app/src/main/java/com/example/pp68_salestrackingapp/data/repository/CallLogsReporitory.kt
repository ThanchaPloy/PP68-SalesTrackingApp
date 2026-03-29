package com.example.pp68_salestrackingapp.data.repository

import android.content.Context
import android.provider.CallLog
import com.example.pp68_salestrackingapp.data.remote.ApiService
import com.example.pp68_salestrackingapp.di.TokenManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class CallLogEntry(
    val logId:       String,
    val userId:      String,
    val custId:      String?,
    val phoneNumber: String,
    val startTime:   String,
    val endTime:     String?,
    val duration:    Int,
    val isSynced:    Boolean = false
)

@Singleton
class CallLogRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiService:   ApiService,
    private val tokenManager: TokenManager
) {
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US)
    private val synced    = mutableSetOf<String>() // เก็บ key ที่ sync แล้ว

    // ✅ ดึง call log จากเครื่อง แล้ว match กับ contact ใน DB
    suspend fun syncCallLogs(
        contactPhoneMap: Map<String, String>  // phone_number → cust_id
    ): kotlin.Result<Int> {
        return withContext(Dispatchers.IO) {
            try {
                val userId = tokenManager.getUserData()?.userId
                    ?: return@withContext kotlin.Result.failure(Exception("Not logged in"))

                val entries = mutableListOf<CallLogEntry>()
                val cursor  = context.contentResolver.query(
                    CallLog.Calls.CONTENT_URI,
                    arrayOf(
                        CallLog.Calls.NUMBER,
                        CallLog.Calls.DATE,
                        CallLog.Calls.DURATION,
                        CallLog.Calls.TYPE
                    ),
                    null, null,
                    "${CallLog.Calls.DATE} DESC"
                )

                cursor?.use { c ->
                    val numberIdx   = c.getColumnIndex(CallLog.Calls.NUMBER)
                    val dateIdx     = c.getColumnIndex(CallLog.Calls.DATE)
                    val durationIdx = c.getColumnIndex(CallLog.Calls.DURATION)

                    while (c.moveToNext()) {
                        val number   = c.getString(numberIdx) ?: continue
                        val dateMs   = c.getLong(dateIdx)
                        val duration = c.getInt(durationIdx)

                        // ✅ normalize เบอร์โทร (ลบ - และ space)
                        val normalized = number.replace(Regex("[^0-9+]"), "")

                        // ✅ match กับ contact ใน DB เท่านั้น
                        val custId = contactPhoneMap.entries.find { (phone, _) ->
                            phone.replace(Regex("[^0-9+]"), "") == normalized
                        }?.value ?: continue  // ข้ามถ้าไม่ match

                        val key = "$normalized-$dateMs"
                        if (key in synced) continue  // ข้ามถ้า sync แล้ว

                        val startTime = isoFormat.format(Date(dateMs))
                        val endTime   = isoFormat.format(Date(dateMs + duration * 1000L))

                        entries.add(CallLogEntry(
                            logId       = "LOG-${UUID.randomUUID().toString().take(8).uppercase()}",
                            userId      = userId,
                            custId      = custId,
                            phoneNumber = normalized,
                            startTime   = startTime,
                            endTime     = endTime,
                            duration    = duration
                        ))
                        synced.add(key)
                    }
                }

                // ✅ ส่งขึ้น API ทีละ batch
                if (entries.isNotEmpty()) {
                    entries.forEach { entry ->
                        try {
                            apiService.insertCallLog(
                                mapOf(
                                    "log_id"       to entry.logId,
                                    "user_id"      to entry.userId,
                                    "cust_id"      to (entry.custId ?: ""),
                                    "phone_number" to entry.phoneNumber,
                                    "start_time"   to entry.startTime,
                                    "end_time"     to (entry.endTime ?: entry.startTime),
                                    "duration"     to entry.duration.toString(),
                                    "is_sync"      to "true"
                                )
                            )
                        } catch (e: Exception) {
                            android.util.Log.w("CallLog", "ส่ง log ไม่สำเร็จ: ${e.message}")
                        }
                    }
                }

                kotlin.Result.success(entries.size)
            } catch (e: Exception) {
                kotlin.Result.failure(e)
            }
        }
    }
}