package com.example.pp68_salestrackingapp.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class DropdownOptionsTest {

    @Test
    fun `ชื่อไม่ซ้ำ ไม่ถูกแตะ`() {
        val input = listOf("C1" to "บริษัท ก", "C2" to "บริษัท ข")
        assertEquals(input, input.withUniqueLabels())
    }

    @Test
    fun `ชื่อซ้ำ ต่อท้ายด้วยรหัสทุกตัวที่ซ้ำ`() {
        val out = listOf(
            "LD-001"     to "มหาวิทยาลัยพระจอมเกล้า",
            "6709011TN"  to "มหาวิทยาลัยพระจอมเกล้า",
            "C3"         to "บริษัท ข"
        ).withUniqueLabels()

        assertEquals("มหาวิทยาลัยพระจอมเกล้า (LD-001)", out[0].second)
        assertEquals("มหาวิทยาลัยพระจอมเกล้า (6709011TN)", out[1].second)
        assertEquals("บริษัท ข", out[2].second)
        // id ต้องไม่เปลี่ยน — ป้ายเท่านั้นที่ต่างกัน
        assertEquals(listOf("LD-001", "6709011TN", "C3"), out.map { it.first })
    }

    @Test
    fun `ชื่อที่ต่างกันแค่ช่องว่างหัวท้าย ถือว่าซ้ำ`() {
        val out = listOf("A" to "บริษัท ก", "B" to " บริษัท ก ").withUniqueLabels()
        assertEquals(2, out.count { it.second.contains("(") })
    }
}
