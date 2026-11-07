package com.example.pp68_salestrackingapp.di

import com.example.pp68_salestrackingapp.BuildConfig
import com.example.pp68_salestrackingapp.data.model.LoginRequest
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `app_version_code` มีไว้ให้ดูว่า user ส่วนใหญ่อัปเดต APK แล้วหรือยัง ก่อน rename คอลัมน์ในเฟส 3
 * (backend/MIGRATION_ROADMAP.md) ถ้ามันไม่ได้ถูกส่งขึ้นไปจริง เราจะ "เห็นว่าไม่มีใครอัปเดตเลย"
 * ทั้งที่ทุกคนอัปเดตแล้ว แล้วตัดสินใจผิดบนข้อมูลที่ว่างเปล่า
 *
 * ความเสี่ยงเฉพาะของรีโปนี้: Gson ใน NetworkModule ติด ExclusionStrategy ที่ **ตัดทุกฟิลด์ที่ไม่มี
 * `@SerializedName` ทิ้ง** (กลไกเดียวกับที่กันคอลัมน์ local-only ไม่ให้หลุดขึ้น server) ฟิลด์ใหม่จึง
 * หายเงียบได้โดยไม่มีอะไรเตือน เทสนี้ยืนยันว่ามันอยู่ใน body จริงหลังผ่าน Gson ตัวเดียวกับของจริง
 */
class LoginRequestBodyTest {

    private val gson = NetworkModule.gson

    @Test
    fun `the login body carries the app version code`() {
        val body = JsonParser.parseString(gson.toJson(LoginRequest("99999", "secret"))).asJsonObject

        assertTrue(
            "app_version_code หายไปจาก body — เช็ค @SerializedName และ ExclusionStrategy ใน NetworkModule",
            body.has("app_version_code")
        )
        assertEquals(BuildConfig.VERSION_CODE, body.get("app_version_code").asInt)
    }

    @Test
    fun `the login body still carries the fields the server already required`() {
        val body = JsonParser.parseString(gson.toJson(LoginRequest("99999", "secret"))).asJsonObject

        assertEquals("99999", body.get("emp_code").asString)
        assertEquals("secret", body.get("password").asString)
    }
}
