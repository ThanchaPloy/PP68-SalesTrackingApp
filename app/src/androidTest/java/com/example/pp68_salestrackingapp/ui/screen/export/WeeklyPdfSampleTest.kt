package com.example.pp68_salestrackingapp.ui.screen.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pp68_salestrackingapp.ui.viewmodels.export.ExportActivityItem
import com.example.pp68_salestrackingapp.ui.viewmodels.export.ExportResultDetail
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * สร้างไฟล์ PDF ตัวอย่างจากข้อมูลสมมติ แล้ว render ออกมาเป็น PNG หน้าละไฟล์
 *
 * มีไว้เพื่อ "ดูผลจริง" โดยไม่ต้องลงแอปแล้วกด export เอง — PdfDocument เป็นคลาสของ Android
 * รันบน JVM ธรรมดาไม่ได้ จึงต้องรันบน emulator ผ่าน connectedAndroidTest
 *
 * ไฟล์ที่ได้อยู่ใน getExternalFilesDir("pdf-sample") ดึงออกมาด้วย adb pull
 */
@RunWith(AndroidJUnit4::class)
class WeeklyPdfSampleTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** รูปปลอมที่อ่านออกจริง: สี่เหลี่ยมสีพร้อมเลขกำกับ บีบเป็น JPEG เหมือนรูปจากกล้อง */
    private fun fakePhoto(index: Int, w: Int, h: Int, color: Int): String {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(bmp).apply {
            drawColor(color)
            drawText(
                "PHOTO $index",
                w / 2f - 120f,
                h / 2f,
                Paint().apply { this.color = Color.WHITE; textSize = 64f; isFakeBoldText = true }
            )
        }
        val file = File(context.cacheDir, "sample_photo_$index.jpg")
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return file.absolutePath
    }

    @Test
    fun generateSamplePdf() = runBlocking {
        // รูปแนวนอน แนวตั้ง และจัตุรัส ปนกัน เพื่อดูว่าการคงสัดส่วนทำงานจริง
        val photos = listOf(
            fakePhoto(1, 1600, 1200, Color.parseColor("#1E6B38")),
            fakePhoto(2, 1200, 1600, Color.parseColor("#AE2138")),
            fakePhoto(3, 1400, 1400, Color.parseColor("#1A73E8")),
            fakePhoto(4, 1600, 1200, Color.parseColor("#B8860B")),
            fakePhoto(5, 1200, 1600, Color.parseColor("#6A1B9A"))
        )

        val activities = listOf(
            // 5 รูป -> ต้องได้ 3 + 2 สองแถว
            ExportActivityItem(
                date = "2026-09-30",
                projectName = "ติดตั้งระบบโซลาร์ อาคาร A",
                companyName = "บริษัท ทดสอบ จำกัด",
                topic = "เข้าพบเพื่อนำเสนอราคา",
                note = "ลูกค้าสนใจมาก",
                status = "completed",
                activityType = "onsite",
                contactName = "คุณสมชาย ใจดี",
                checkInTime = "10:30",
                checkInStatus = "ตรงจุด",
                locationName = "นิคมอุตสาหกรรมลาดกระบัง",
                resultDetails = listOf(
                    ExportResultDetail(
                        summary = "นำเสนอราคาเรียบร้อย ลูกค้าขอเวลาพิจารณา 2 สัปดาห์",
                        newStatus = "Quotation",
                        opportunityScore = "HOT",
                        dmInvolved = true,
                        isProposalSent = true,
                        proposalDate = "2026-09-30",
                        competitorCount = 2,
                        previousSolution = "no_solution",
                        photoUrls = photos
                    )
                )
            ),
            // บันทึกเดียวรูปเดียว -> แถวเดียวรูปเดียว
            ExportActivityItem(
                date = "2026-09-29",
                projectName = "ปรับปรุงระบบไฟฟ้า",
                companyName = "ห้างหุ้นส่วน ตัวอย่าง",
                topic = "ตรวจหน้างาน",
                note = null,
                status = "completed",
                activityType = "onsite",
                contactName = "คุณสมหญิง",
                resultDetails = listOf(
                    ExportResultDetail(
                        summary = "ตรวจหน้างานเสร็จ รอสรุปแบบ",
                        newStatus = "New Project",
                        opportunityScore = "WARM",
                        photoUrls = listOf(photos[0])
                    )
                )
            ),
            // แถวที่เนื้อหายาวมาก + หลายบันทึกมีรูป -> บีบให้ต้องไหลข้ามหน้า
            ExportActivityItem(
                date = "2026-09-28",
                projectName = "โครงการที่มีรายละเอียดยาวมาก",
                companyName = "บริษัท ยาวมาก จำกัด",
                topic = "ประชุมสรุปหลายวาระ",
                note = "ก".repeat(400),
                status = "completed",
                activityType = "onsite",
                contactName = "คุณทดสอบ",
                resultDetails = (1..4).map { n ->
                    ExportResultDetail(
                        summary = "สรุปผลรอบที่ $n: " + "รายละเอียดยาว ".repeat(20),
                        newStatus = "Bidding",
                        opportunityScore = "HOT",
                        dmInvolved = true,
                        competitorCount = n,
                        photoUrls = photos.take(5)
                    )
                }
            ),
            // ไม่มีรูปเลย -> ต้องไม่มีหัวข้อรูปโผล่มา
            ExportActivityItem(
                date = "2026-09-27",
                projectName = "งานไม่มีรูป",
                companyName = "บริษัท ไร้ภาพ",
                topic = "โทรติดตาม",
                note = null,
                status = "completed",
                activityType = "call",
                resultDetails = listOf(
                    ExportResultDetail(summary = "โทรติดตาม ลูกค้ายังไม่ตัดสินใจ", newStatus = "Lead")
                )
            )
        )

        val pdf = buildWeeklyPdf(context, "weekly_sample", activities)
        assertTrue("ไม่ได้ไฟล์ PDF ออกมา", pdf.exists() && pdf.length() > 0)

        val outDir = File(context.getExternalFilesDir(null), "pdf-sample").apply {
            deleteRecursively(); mkdirs()
        }
        pdf.copyTo(File(outDir, "weekly_sample.pdf"), overwrite = true)

        // render กลับเป็นรูปเพื่อดูหน้าตาจริงได้โดยไม่ต้องเปิดแอป
        ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer ->
                assertTrue("ต้องมีมากกว่า 1 หน้า ถึงจะพิสูจน์ว่าไหลข้ามหน้าได้", renderer.pageCount >= 2)
                for (i in 0 until renderer.pageCount) {
                    renderer.openPage(i).use { page ->
                        // x2 เพื่อให้อ่านตัวหนังสือออกตอนเปิดดู
                        val bmp = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                        Canvas(bmp).drawColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        FileOutputStream(File(outDir, "page_${i + 1}.png")).use {
                            bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
                        }
                    }
                }
            }
        }
    }
}
