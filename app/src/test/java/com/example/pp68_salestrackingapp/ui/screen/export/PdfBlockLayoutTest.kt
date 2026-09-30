package com.example.pp68_salestrackingapp.ui.screen.export

import android.graphics.Paint
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * คุมกฎ "ห้ามตัดหน้าคั่นกลางแถวรูป"
 *
 * รูปที่อยู่แถวเดียวกันถูกเก็บเป็นหลาย PdfLine โดยตัวที่ไม่ใช่ตัวท้ายมี lineHeight = 0 เพื่อให้ถูกวาด
 * ที่ y เดียวกัน ถ้าตัวแบ่งหน้ามองทีละบรรทัด มันจะตัดกลางกลุ่มแล้วรูปในแถวเดียวกันไปโผล่คนละหน้า
 */
class PdfBlockLayoutTest {

    private fun line(height: Float) = PdfLine("x", 0f, Paint(), height)

    /** รูปในแถว: ทุกตัวยกเว้นตัวท้ายสูง 0 ตัวท้ายเป็นตัวดันบรรทัดลง */
    private fun photoRow(rowHeight: Float, count: Int) =
        List(count - 1) { PdfLine("", 0f, Paint(), 0f) } + PdfLine("", 0f, Paint(), rowHeight)

    @Test
    fun `a plain text line is a block on its own`() {
        val lines = listOf(line(13f), line(14f))

        assertEquals(1, lines.leadingBlockSize())
        assertEquals(13f, lines.leadingBlockHeight())
    }

    @Test
    fun `a row of three photos counts as one unbreakable block`() {
        val lines = photoRow(rowHeight = 84f, count = 3) + line(13f)

        assertEquals(3, lines.leadingBlockSize())
        // ความสูงของก้อนคือความสูงของแถวรูป ไม่ใช่ผลรวมของสามบรรทัด
        assertEquals(84f, lines.leadingBlockHeight())
    }

    // 5 รูป = 3 + 2 ต้องแยกเป็นสองก้อน ตัดหน้าระหว่างสองแถวได้ แต่ห้ามตัดกลางแถว
    @Test
    fun `five photos split into two blocks that can break between them`() {
        val lines = photoRow(84f, 3) + photoRow(84f, 2)

        assertEquals(3, lines.leadingBlockSize())
        val second = lines.drop(3)
        assertEquals(2, second.leadingBlockSize())
        assertEquals(84f, second.leadingBlockHeight())
    }

    // เคสที่ทำให้ IndexOutOfBounds ได้ถ้า coerceAtMost หายไป: ทุกบรรทัดสูง 0
    @Test
    fun `a list of only zero-height lines does not run past the end`() {
        val lines = List(3) { PdfLine("", 0f, Paint(), 0f) }

        assertEquals(3, lines.leadingBlockSize())
        assertEquals(0f, lines.leadingBlockHeight())
    }

    // หัวข้อ "รูปถ่ายยืนยัน (N รูป):" ต้องติดไปกับแถวรูปแรกเสมอ ไม่งั้นหัวข้อค้างท้ายหน้า
    // แล้วรูปไปโผล่หน้าถัดไป ซึ่งอ่านแล้วเหมือนข้อมูลหาย
    @Test
    fun `a header marked keepWithNext is glued to the photo row after it`() {
        val header = PdfLine("รูปถ่ายยืนยัน (3 รูป):", 0f, Paint(), 13f, keepWithNext = true)
        val lines = listOf(header) + photoRow(rowHeight = 84f, count = 3) + line(13f)

        assertEquals(4, lines.leadingBlockSize())
        assertEquals(13f + 84f, lines.leadingBlockHeight())
    }

    @Test
    fun `an empty list asks for nothing`() {
        assertEquals(0, emptyList<PdfLine>().leadingBlockSize())
        assertEquals(0f, emptyList<PdfLine>().leadingBlockHeight())
    }
}
