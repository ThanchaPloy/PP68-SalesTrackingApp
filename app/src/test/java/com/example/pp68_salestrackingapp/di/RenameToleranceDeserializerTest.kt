package com.example.pp68_salestrackingapp.di

import com.example.pp68_salestrackingapp.data.model.Customer
import com.example.pp68_salestrackingapp.data.model.Project
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * delta sync ประกอบ payload ด้วย `to_jsonb(row)` ตรง ๆ (ดู SyncChangeRepositoryImpl.findSnapshot
 * และ add_sync_change_feed.sql) — key ของ JSON คือ "ชื่อคอลัมน์ใน DB ตอนนี้" เป๊ะ ๆ ไม่ผ่านชั้น
 * @SerialName ของ backend เหมือนเส้น REST ปกติ
 *
 * backend/MIGRATION_ROADMAP.md เฟส 3 วางแผน rename `project.create_by` -> `created_by` และ
 * `customer.create_date`/`lead_customer.create_date` -> `created_at` ซึ่งจะเปลี่ยน key พวกนี้ทันที
 * ที่รันคำสั่ง rename — ไม่มีช่วงเปลี่ยนผ่านให้ฝั่งแอป เทสนี้ยืนยันว่า APK รุ่นนี้อ่านได้ทั้งชื่อเก่า
 * (ที่ยังใช้งานจริงตอนนี้) และชื่อใหม่ (ที่จะเริ่มส่งมาทันทีหลัง rename) ก่อนจะอนุญาตให้ rename จริง
 */
class RenameToleranceDeserializerTest {

    private val gson = NetworkModule.gson

    @Test
    fun `project owner reads from the current column name create_by`() {
        val json = """{"project_code":"P1","create_by":"U1"}"""
        assertEquals("U1", gson.fromJson(json, Project::class.java).createBy)
    }

    @Test
    fun `project owner also reads from the planned post-rename name created_by`() {
        val json = """{"project_code":"P1","created_by":"U1"}"""
        assertEquals("U1", gson.fromJson(json, Project::class.java).createBy)
    }

    @Test
    fun `customer created date reads from the current column name create_date`() {
        val json = """{"customer_code":"C1","customer_name":"บริษัท ก","create_date":"2026-11-07"}"""
        assertEquals("2026-11-07", gson.fromJson(json, Customer::class.java).createdAt)
    }

    @Test
    fun `customer created date also reads from the planned post-rename name created_at`() {
        val json = """{"customer_code":"C1","customer_name":"บริษัท ก","created_at":"2026-11-07"}"""
        assertEquals("2026-11-07", gson.fromJson(json, Customer::class.java).createdAt)
    }
}
