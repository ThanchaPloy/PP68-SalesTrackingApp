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
        prefs.edit().putString(key, gson.toJson(envelope)).remove(dismissedKey(key)).apply()
    }

    fun <T> load(key: String, clazz: Class<T>): T? {
        val raw = prefs.getString(key, null) ?: return null
        val envelope = runCatching { gson.fromJson(raw, Envelope::class.java) }.getOrNull() ?: return null
        if (Companion.isExpired(envelope.savedAt)) {
            clear(key)
            return null
        }
        return runCatching { gson.fromJson(envelope.json, clazz) }.getOrNull()
    }

    fun exists(key: String): Boolean = peekExists(prefs, gson, key)

    fun clear(key: String) {
        prefs.edit().remove(key).remove(dismissedKey(key)).apply()
    }

    // แบนเนอร์ในหน้าลิสต์กด "ปิด" แล้วเดิมแค่ล้าง state ในจอ ไม่เคยจำไว้เลยว่าปิดไปแล้ว พอกลับมาหน้า
    // เดิม (ON_RESUME) ก็เช็คเจอ draft เดิมอีกแล้วโผล่ขึ้นมาใหม่ทันที — ต้องจำไว้ว่าผู้ใช้ปิดไปแล้ว
    // จริงๆ (ไม่ลบฉบับร่างทิ้ง ผู้ใช้ยังกลับมาแก้ฟอร์มเดิมต่อได้ปกติ) จนกว่าจะมีการบันทึกทับใหม่
    fun dismiss(key: String) {
        prefs.edit().putBoolean(dismissedKey(key), true).apply()
    }

    private fun dismissedKey(key: String) = "$key:dismissed"

    /**
     * อ่านฉบับร่างดิบตาม prefix ไว้ย้ายไปเก็บที่อื่น — คืนเฉพาะตัวที่ยังไม่หมดอายุ
     * มีไว้สำหรับการย้ายร่างนัดหมายไป Room (แผนงาน C.4) ไม่ใช่สำหรับใช้งานปกติ
     */
    fun rawEntriesWithPrefix(prefix: String): Map<String, String> =
        prefs.all.keys
            .filter { it.startsWith(prefix) && !it.endsWith(":dismissed") }
            .mapNotNull { key ->
                val raw = prefs.getString(key, null) ?: return@mapNotNull null
                val envelope = runCatching { gson.fromJson(raw, Envelope::class.java) }.getOrNull()
                    ?: return@mapNotNull null
                if (Companion.isExpired(envelope.savedAt)) null else key to envelope.json
            }
            .toMap()

    /** ลบทุก key ตาม prefix รวมทั้งธง dismissed ของมัน */
    fun clearWithPrefix(prefix: String) {
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(prefix) }.forEach { editor.remove(it) }
        editor.apply()
    }

    companion object {
        private const val PREFS_NAME = "form_drafts"

        // เช็คแบบเบาๆ ว่ามีฉบับร่างค้างอยู่ไหม (ยังไม่หมดอายุ และยังไม่ถูกปิดแบนเนอร์ไปแล้ว) — ให้หน้า
        // ลิสต์ที่ไม่มี ViewModel ของตัวเองผูกกับฟอร์มนั้นโดยตรงเรียกใช้ได้โดยไม่ต้อง inject DraftStore
        fun peekExists(context: Context, key: String): Boolean =
            peekExists(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE), Gson(), key)

        // ให้หน้าลิสต์ที่ไม่ได้ inject DraftStore เต็มตัวเรียกปิดแบนเนอร์แบบจำถาวรได้ เหมือน peekExists
        fun dismiss(context: Context, key: String) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean("$key:dismissed", true).apply()
        }

        private fun peekExists(prefs: android.content.SharedPreferences, gson: Gson, key: String): Boolean {
            if (prefs.getBoolean("$key:dismissed", false)) return false
            val raw = prefs.getString(key, null) ?: return false
            val envelope = runCatching { gson.fromJson(raw, Envelope::class.java) }.getOrNull() ?: return false
            return !isExpired(envelope.savedAt)
        }

        private fun isExpired(savedAtIso: String): Boolean =
            runCatching { Instant.parse(savedAtIso).isBefore(Instant.now().minus(7, ChronoUnit.DAYS)) }
                .getOrElse { true }
    }

    private data class Envelope(val savedAt: String, val json: String)
}
