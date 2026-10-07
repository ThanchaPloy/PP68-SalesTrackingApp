package com.example.pp68_salestrackingapp.utils

import com.google.gson.JsonSyntaxException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * ข้อความของ Gson คือเบาะแสเดียวที่บอกว่าฟิลด์ไหนใน change feed ชนิดไม่ตรงกับโมเดล
 * worker เดิมบันทึกแค่ชื่อคลาส ทำให้ poison pill ใน feed หาไม่เจอเลย
 */
class JsonPathOfTest {

    @Test
    fun `reads the path out of a gson message`() {
        val error = JsonSyntaxException(
            "java.lang.IllegalStateException: Expected BOOLEAN but was NUMBER " +
                "at line 1 column 842 path \$.items[2].payload.is_latest"
        )
        assertEquals("\$.items[2].payload.is_latest", jsonPathOf(error))
    }

    @Test
    fun `walks the cause chain`() {
        val root = IllegalStateException("Expected a string but was BEGIN_OBJECT at path \$.payload.note")
        assertEquals("\$.payload.note", jsonPathOf(RuntimeException("wrapped", root)))
    }

    @Test
    fun `returns null when there is no path`() {
        assertNull(jsonPathOf(IllegalStateException("boom")))
    }
}
