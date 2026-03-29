package com.example.pp68_salestrackingapp.utils

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.pp68_salestrackingapp.ui.screen.export.ExportActivityItem
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter

object ExportHelper {

    // ==========================================
    // 1. ฟังก์ชันแชร์ไฟล์ CSV
    // ==========================================
    fun shareCsv(context: Context, csvContent: String, fileName: String) {
        try {
            val file = File(context.cacheDir, fileName)
            FileWriter(file).use { it.write(csvContent) }
            shareFileIntent(context, file, "text/csv")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ==========================================
    // 2. ฟังก์ชันสร้างและแชร์ไฟล์ PDF (สำหรับ Activity)
    // ==========================================
    fun shareActivityPdf(context: Context, activities: List<ExportActivityItem>, fileName: String) {
        try {
            val pdfDocument = PdfDocument()
            val paint = Paint()
            val titlePaint = Paint()

            // ตั้งค่าหน้ากระดาษ A4 (กว้าง 595, สูง 842 pixel)
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas: Canvas = page.canvas

            // ตกแต่งหัวข้อ
            titlePaint.textSize = 24f
            titlePaint.isFakeBoldText = true
            titlePaint.color = Color.BLUE
            canvas.drawText("Weekly Activity Report", 50f, 80f, titlePaint)

            // วาดข้อมูลทีละบรรทัด
            paint.textSize = 14f
            paint.color = Color.BLACK
            var yPosition = 130f // ตำแหน่งแกน Y เริ่มต้น

            activities.forEach { item ->
                // ถ้าข้อมูลยาวเกินหน้ากระดาษ ต้องขึ้นหน้าใหม่ (ในที่นี้เขียนแบบง่ายๆ ให้ดูก่อน)
                if (yPosition > 800f) {
                    pdfDocument.finishPage(page)
                    // (จริงๆ ต้องมี logic สร้าง page ใหม่ตรงนี้ถ้าข้อมูลเยอะมากๆ)
                }

                val text = "Date: ${item.date} | Project: ${item.projectName ?: "-"} | Status: ${item.status}"
                canvas.drawText(text, 50f, yPosition, paint)

                val detailText = "   Topic: ${item.topic ?: "-"} | Note: ${item.note ?: "-"}"
                canvas.drawText(detailText, 50f, yPosition + 20f, paint)

                yPosition += 50f // ขยับลงไปบรรทัดต่อไป
            }

            pdfDocument.finishPage(page)

            // บันทึกไฟล์ลง Cache
            val file = File(context.cacheDir, fileName)
            pdfDocument.writeTo(FileOutputStream(file))
            pdfDocument.close()

            // เรียกหน้าต่างแชร์
            shareFileIntent(context, file, "application/pdf")

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ==========================================
    // 3. ฟังก์ชันกลางสำหรับเปิดหน้าต่างแชร์แอป (Line, Email, etc.)
    // ==========================================
    private fun shareFileIntent(context: Context, file: File, mimeType: String) {
        // *อย่าลืมตั้งค่า FileProvider ใน AndroidManifest*
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Report via..."))
    }
}