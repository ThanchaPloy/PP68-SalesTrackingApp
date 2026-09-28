package com.example.pp68_salestrackingapp.utils

import android.content.Context
import android.content.SharedPreferences
import io.mockk.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class DraftStoreTest {

    private val editor = mockk<SharedPreferences.Editor>(relaxed = true)
    private val prefs = mockk<SharedPreferences>()
    private val context = mockk<Context> {
        every { getSharedPreferences(any(), any()) } returns prefs
    }
    private val storage = mutableMapOf<String, String?>()
    private lateinit var store: DraftStore

    data class Sample(val name: String = "", val count: Int = 0)

    @Before
    fun setUp() {
        storage.clear()
        every { prefs.edit() } returns editor
        every { editor.putString(any(), any()) } answers {
            storage[firstArg()] = secondArg()
            editor
        }
        every { editor.remove(any()) } answers {
            storage.remove(firstArg<String>())
            editor
        }
        every { editor.apply() } just Runs
        every { prefs.getString(any(), any()) } answers { storage[firstArg()] ?: secondArg() }
        every { prefs.contains(any()) } answers { storage.containsKey(firstArg<String>()) }
        store = DraftStore(context)
    }

    @Test
    fun `save then load round-trips the same data`() {
        store.save("k1", Sample("hello", 5))
        assertEquals(Sample("hello", 5), store.load("k1", Sample::class.java))
    }

    @Test
    fun `load returns null when nothing was ever saved`() {
        assertNull(store.load("missing", Sample::class.java))
    }

    @Test
    fun `clear removes the saved draft`() {
        store.save("k1", Sample("hello", 5))
        store.clear("k1")
        assertNull(store.load("k1", Sample::class.java))
    }

    @Test
    fun `exists is true right after saving and false after clearing`() {
        store.save("k1", Sample())
        assertTrue(store.exists("k1"))
        store.clear("k1")
        assertFalse(store.exists("k1"))
    }

    // ยังไม่ครบ 7 วัน (ปัดขึ้นนับรวมวันนี้) — ต้องยังใช้ได้
    @Test
    fun `a draft saved 6 days ago is still valid`() {
        storage["k1"] = """{"savedAt":"${Instant.now().minus(6, ChronoUnit.DAYS)}","json":"{\"name\":\"x\",\"count\":1}"}"""
        assertEquals(Sample("x", 1), store.load("k1", Sample::class.java))
    }

    // เลย 7 วันไปแล้ว — ต้องอ่านไม่ได้ และต้องล้างตัวเองทิ้งไปด้วย (lazy cleanup)
    @Test
    fun `a draft saved 8 days ago is expired and self-clears`() {
        storage["k1"] = """{"savedAt":"${Instant.now().minus(8, ChronoUnit.DAYS)}","json":"{\"name\":\"x\",\"count\":1}"}"""
        assertNull(store.load("k1", Sample::class.java))
        assertFalse(store.exists("k1"))
    }

    @Test
    fun `peekExists reflects the same expiry rule without a DraftStore instance`() {
        storage["k1"] = """{"savedAt":"${Instant.now().minus(8, ChronoUnit.DAYS)}","json":"{}"}"""
        assertFalse(DraftStore.peekExists(context, "k1"))

        storage["k2"] = """{"savedAt":"${Instant.now()}","json":"{}"}"""
        assertTrue(DraftStore.peekExists(context, "k2"))
    }
}
