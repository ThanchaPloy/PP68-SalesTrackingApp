package com.example.pp68_salestrackingapp.utils

import android.content.Context
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

// ฉบับร่างของฟอร์มที่ยังไม่ได้บันทึก — local-only ไม่ sync ขึ้น server ไม่ต้องใช้ Room เพราะไม่ต้อง
// query/join กับอะไร เก็บเป็น JSON ก้อนเดียวต่อฟอร์ม 1 ตัว key ด้วยชื่อฟอร์ม+id ของรายการที่กำลังแก้
// (หรือ "new" ถ้ากำลังสร้างใหม่) หมดอายุใน 7 วัน เช็คแบบ lazy ตอนอ่าน ไม่มี background job
@Singleton
class DraftStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun <T> save(key: String, draft: T) {
        val envelope = Envelope(savedAt = Instant.now().toString(), json = gson.toJson(draft))
        prefs.edit().putString(key, gson.toJson(envelope)).apply()
    }

    fun <T> load(key: String, clazz: Class<T>): T? {
        val raw = prefs.getString(key, null) ?: return null
        val envelope = runCatching { gson.fromJson(raw, Envelope::class.java) }.getOrNull() ?: return null
        if (isExpired(envelope.savedAt)) {
            clear(key)
            return null
        }
        return runCatching { gson.fromJson(envelope.json, clazz) }.getOrNull()
    }

    fun exists(key: String): Boolean = peekExists(prefs, gson, key)

    fun clear(key: String) {
        prefs.edit().remove(key).apply()
    }

    companion object {
        private const val PREFS_NAME = "form_drafts"

        // เช็คแบบเบาๆ ว่ามีฉบับร่างค้างอยู่ไหม (ยังไม่หมดอายุ) — ให้หน้าลิสต์ที่ไม่มี ViewModel
        // ของตัวเองผูกกับฟอร์มนั้นโดยตรงเรียกใช้ได้โดยไม่ต้อง inject DraftStore เต็มรูปแบบ
        fun peekExists(context: Context, key: String): Boolean =
            peekExists(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE), Gson(), key)

        private fun peekExists(prefs: android.content.SharedPreferences, gson: Gson, key: String): Boolean {
            val raw = prefs.getString(key, null) ?: return false
            val envelope = runCatching { gson.fromJson(raw, Envelope::class.java) }.getOrNull() ?: return false
            return !isExpired(envelope.savedAt)
        }

        private fun isExpired(savedAtIso: String): Boolean =
            runCatching { Instant.parse(savedAtIso).isBefore(Instant.now().minus(7, ChronoUnit.DAYS)) }
                .getOrElse { true }
    }

    private fun isExpired(savedAtIso: String): Boolean = Companion.isExpired(savedAtIso)

    private data class Envelope(val savedAt: String, val json: String)
}
