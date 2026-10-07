package com.example.pp68_salestrackingapp.utils

import android.content.Context
import com.example.pp68_salestrackingapp.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * เก็บเฉพาะ metadata สำหรับวิเคราะห์ปัญหา ห้ามใส่ token, payload, ชื่อ, เบอร์โทร,
 * พิกัด หรือรหัสรายการจริง รายการเก่าถูกตัดทิ้งเพื่อไม่ให้ไฟล์โตไม่จำกัด
 */
@Singleton
class SyncDiagnostics @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("sync_diagnostics", Context.MODE_PRIVATE)

    /**
     * [flush] = เขียนลงดิสก์ทันทีแทน apply() แบบ async
     * ใช้ตอนบันทึก crash เพราะ process กำลังจะตาย ถ้ารอ apply() เขียนไม่ทัน บันทึกจะหายไป
     */
    @Synchronized
    fun record(event: String, fields: Map<String, Any?> = emptyMap(), flush: Boolean = false) {
        val events = readEvents()
        events.put(JSONObject().apply {
            put("timestamp", Instant.now().toString())
            put("event", event.take(80))
            fields.forEach { (key, value) ->
                if (key in ALLOWED_FIELDS && value != null) put(key, value.toString().take(160))
            }
        })
        val bounded = JSONArray()
        val start = (events.length() - MAX_EVENTS).coerceAtLeast(0)
        for (index in start until events.length()) bounded.put(events.getJSONObject(index))
        val editor = prefs.edit().putString(KEY_EVENTS, bounded.toString())
        if (flush) editor.commit() else editor.apply()
    }

    fun exportFile(): File {
        val payload = JSONObject().apply {
            put("schema_version", 1)
            put("generated_at", Instant.now().toString())
            put("app_version", BuildConfig.VERSION_NAME)
            put("build", BuildConfig.VERSION_CODE)
            put("android_api", android.os.Build.VERSION.SDK_INT)
            put("events", readEvents())
        }
        return File(context.cacheDir, "sales-tracking-diagnostics.json").apply {
            writeText(payload.toString(2), Charsets.UTF_8)
        }
    }

    fun markSuccessfulSync(at: String = Instant.now().toString()) {
        prefs.edit().putString(KEY_LAST_SUCCESS, at).apply()
    }

    fun lastSuccessfulSync(): String? = prefs.getString(KEY_LAST_SUCCESS, null)

    @Synchronized
    private fun readEvents(): JSONArray = try {
        JSONArray(prefs.getString(KEY_EVENTS, "[]") ?: "[]")
    } catch (_: Exception) {
        JSONArray()
    }

    private companion object {
        const val KEY_EVENTS = "events"
        const val KEY_LAST_SUCCESS = "last_success"
        const val MAX_EVENTS = 100
        val ALLOWED_FIELDS = setOf(
            "run_id", "trigger", "attempted", "succeeded", "temporary_failures",
            "permanent_failures", "rejected_pending", "still_pending", "worker_attempt",
            "error_type", "failure_types",
            // ของที่เพิ่มมาเพื่อให้ไฟล์มีร่องรอยของ error ที่เกิดตอนใช้งานจริง ไม่ใช่แค่ของ worker
            // path เก็บเฉพาะส่วน path ไม่เอา query string เพราะในนั้นมีรหัสรายการจริง
            "method", "path", "http_code", "thread",
            // failure_type ถูก record มาตั้งแต่ต้นแต่ไม่เคยอยู่ในลิสต์นี้ จึงถูกตัดทิ้งเงียบ ๆ ทุกครั้ง
            // json_path คือเส้นทางในข้อความของ Gson เช่น $.items[2].payload.is_latest
            // เก็บเฉพาะชื่อฟิลด์ ไม่เอาค่า เพราะไฟล์นี้ออกนอกเครื่อง
            "failure_type", "json_path"
        )
    }
}

private val GSON_PATH = Regex("path (\\S+)")

/**
 * ดึงเฉพาะ path จากข้อความของ Gson — ไม่มี path ก็คืน null
 *
 * JsonSyntaxException ที่หลุดจาก delta sync บอกไม่ได้เลยว่าฟิลด์ไหนพัง เพราะ worker
 * บันทึกแค่ชื่อคลาส แถวใน feed เรียงตาม seq พอชนแถวที่ parse ไม่ได้ก็ตายทุกรอบ
 * (poison pill) การรู้ชื่อฟิลด์คือสิ่งเดียวที่แยก "ชนิดข้อมูลในดีบีไม่ตรงกับโมเดล" ออกมาได้
 */
fun jsonPathOf(error: Throwable): String? {
    var current: Throwable? = error
    while (current != null) {
        GSON_PATH.find(current.message.orEmpty())?.let { return it.groupValues[1] }
        current = current.cause.takeIf { it !== current }
    }
    return null
}
