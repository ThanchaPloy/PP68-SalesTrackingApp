package com.example.pp68_salestrackingapp.data.model

import com.example.pp68_salestrackingapp.di.NetworkModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * ใช้ Gson ตัวเดียวกับที่ Retrofit ใช้จริง ถ้าสร้างตัวใหม่ในเทสต์จะไม่ได้ทดสอบของจริงเลย
 *
 * payload ประกาศเป็น com.google.gson.JsonObject? — Gson อ่าน JSON null ของฟิลด์ชนิด
 * JsonElement เป็น JsonNull ไม่ใช่ Java null แล้วยัดลงฟิลด์ชนิด JsonObject ไม่ได้
 * event ชนิด DELETE จึงต้องมาแบบ "ไม่มีคีย์ payload" ฝั่ง server กันไว้ด้วย
 * @EncodeDefault(NEVER) เพราะ encodeDefaults = true ทั้งระบบ
 *
 * ถ้ากติกานี้พัง change feed จะตายที่แถว DELETE แถวแรกทุกรอบ ดาวน์โหลดค้างถาวร
 * และแบนเนอร์หน้าแรกไม่มีวันหาย (เจอจริง 2026-10-07)
 */
class SyncChangePageParsingTest {

    private val gson = NetworkModule.gson

    @Test
    fun `a delete event without a payload key parses`() {
        val json = """
            {"items":[
              {"seq":1,"entity_type":"appointment_contact","entity_id":"6162",
               "operation":"DELETE","server_revision":1,"changed_at":"2026-10-07T08:42:07Z"}
            ],"next_cursor":1,"has_more":false}
        """.trimIndent()

        val page = gson.fromJson(json, SyncChangePage::class.java)

        assertEquals(1, page.items.size)
        assertEquals("DELETE", page.items[0].operation)
        assertNull("DELETE ต้องไม่มี payload", page.items[0].payload)
    }

    @Test
    fun `an upsert event keeps its payload object`() {
        val json = """
            {"items":[
              {"seq":2,"entity_type":"lead_customer","entity_id":"C001",
               "operation":"UPSERT","server_revision":2,"changed_at":"2026-10-07T08:42:08Z",
               "payload":{"customer_code":"C001","customer_name":"ทดสอบ"}}
            ],"next_cursor":2,"has_more":true}
        """.trimIndent()

        val page = gson.fromJson(json, SyncChangePage::class.java)

        assertEquals("C001", page.items[0].payload?.get("customer_code")?.asString)
        assertEquals(2L, page.nextCursor)
    }
}
