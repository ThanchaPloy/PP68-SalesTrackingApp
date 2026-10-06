package com.example.pp68_salestrackingapp.ui.viewmodels.project

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ช่อง "มูลค่าที่คาดหวัง" เคยโชว์ 1.0E7 เมื่อเปิดหน้าแก้ไขโครงการที่มูลค่าถึงสิบล้าน
 *
 * ต้นเหตุคือ Double.toString() เปลี่ยนไปใช้รูปวิทยาศาสตร์เองตั้งแต่ 1e7 ขึ้นไป
 * ผู้ใช้เห็นค่าแบบนั้นในช่องกรอกแล้วแก้ต่อไม่ได้ และถ้าเผลอกดบันทึกก็ parse กลับไม่ได้
 */
class PlainAmountTest {

    @Test
    fun `ten million and above stay as ordinary digits`() {
        assertEquals("10000000", 1.0E7.toPlainAmount())
        assertEquals("12500000", 12_500_000.0.toPlainAmount())
        assertEquals("1000000000", 1.0E9.toPlainAmount())
    }

    @Test
    fun `smaller amounts are unchanged and lose the trailing point zero`() {
        assertEquals("500000", 500_000.0.toPlainAmount())
        assertEquals("0", 0.0.toPlainAmount())
    }

    @Test
    fun `decimals survive instead of being rounded away`() {
        assertEquals("1250.75", 1250.75.toPlainAmount())
    }

    /** ค่าที่ออกมาต้องอ่านกลับเป็นตัวเลขเดิมได้ ไม่งั้นกดบันทึกแล้วมูลค่าเพี้ยน */
    @Test
    fun `the text can be parsed back to the same number`() {
        listOf(1.0E7, 12_500_000.0, 500_000.0, 1250.75, 0.0).forEach { original ->
            assertEquals(original, original.toPlainAmount().toDouble(), 0.0)
        }
    }
}
