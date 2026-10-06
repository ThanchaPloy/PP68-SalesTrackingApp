package com.example.pp68_salestrackingapp.di

import com.example.pp68_salestrackingapp.data.model.SalesActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Gson สร้าง object ผ่าน reflection ไม่ผ่าน constructor ของ Kotlin จึงยัด null ลงฟิลด์ที่ประกาศว่า
 * non-null ได้โดยไม่มี compile error เตือน แล้วไประเบิดตอนเรียกใช้ที่ไหนสักแห่ง
 *
 * `planned_date` กับ `plan_status` เป็น nullable ทั้งในตาราง appointment และใน entity ฝั่ง backend
 * แต่ `SalesActivity.activityDate`/`status` ฝั่งแอปประกาศเป็น String ห้าม null — ช่องว่างนี้ถูกอุด
 * ด้วย salesActivityDeserializer ใน NetworkModule เท่านั้น และไม่เคยมีอะไรคุมไว้ว่ามันยังทำงานอยู่
 */
class SalesActivityDeserializerTest {

    private val gson = NetworkModule.gson

    @Test
    fun `a null planned_date becomes an empty string instead of a null in a non-null field`() {
        val json = """{"appointment_id":"A1","emp_code":"U1","planned_date":null,"plan_status":"planned","type":"onsite"}"""

        val activity = gson.fromJson(json, SalesActivity::class.java)

        assertNotNull(activity)
        assertEquals("", activity.activityDate)
    }

    @Test
    fun `a null plan_status becomes an empty string`() {
        val json = """{"appointment_id":"A1","emp_code":"U1","planned_date":"2026-09-30","plan_status":null,"type":"onsite"}"""

        assertEquals("", gson.fromJson(json, SalesActivity::class.java).status)
    }

    @Test
    fun `a null type becomes an empty string`() {
        val json = """{"appointment_id":"A1","emp_code":"U1","planned_date":"2026-09-30","plan_status":"planned","type":null}"""

        assertEquals("", gson.fromJson(json, SalesActivity::class.java).activityType)
    }

    // ฟิลด์ที่ขาดไปเลย (ไม่ใช่ null) ก็ต้องรอดเหมือนกัน — PostgREST ตัดคอลัมน์ที่เป็น null ออกได้
    @Test
    fun `missing fields are filled in rather than left null`() {
        val json = """{"appointment_id":"A1","emp_code":"U1"}"""

        val activity = gson.fromJson(json, SalesActivity::class.java)

        assertEquals("", activity.activityDate)
        assertEquals("", activity.status)
        assertEquals("", activity.activityType)
    }

    @Test
    fun `normal values are left alone`() {
        val json = """{"appointment_id":"A1","emp_code":"U1","planned_date":"2026-09-30","plan_status":"planned","type":"onsite"}"""

        val activity = gson.fromJson(json, SalesActivity::class.java)

        assertEquals("2026-09-30", activity.activityDate)
        assertEquals("planned", activity.status)
        assertEquals("onsite", activity.activityType)
    }
    /**
     * ลูกค้า ERP เป็น remote-only ตั้งแต่ Phase 3 ตาราง customer ในเครื่องจึงไม่มีให้ join หาชื่อ
     * server แนบ customer_name มากับนัดหมายแทน ถ้าตัวนี้ไม่เข้า companyName หน้ารายละเอียดนัด
     * จะโชว์ช่องบริษัทว่างทั้งที่ผู้ใช้เลือกบริษัทไว้แล้ว
     */
    @Test
    fun `the customer name the server attaches lands in companyName`() {
        val json = """{"appointment_id":"A1","emp_code":"U1","cust_code":"C001","customer_name":"บริษัท ตัวอย่าง จำกัด"}"""

        val activity = gson.fromJson(json, SalesActivity::class.java)

        assertEquals("บริษัท ตัวอย่าง จำกัด", activity.companyName)
        assertEquals("C001", activity.customerId)
    }

    @Test
    fun `an appointment without a customer name is still readable`() {
        val json = """{"appointment_id":"A1","emp_code":"U1"}"""

        assertEquals(null, gson.fromJson(json, SalesActivity::class.java).companyName)
    }
}
