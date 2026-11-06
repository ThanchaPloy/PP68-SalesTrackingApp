package com.example.pp68_salestrackingapp.data.local

import android.content.Context
import android.util.Log
import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.example.pp68_salestrackingapp.data.model.Project
import java.util.Locale
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Directional stress measurement; emulator time is logged but deliberately has no time threshold. */
@LargeTest
@RunWith(AndroidJUnit4::class)
class ProjectPagingStressTest {
    private lateinit var database: AppDatabase

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun largeTableStillMaterializesOnlyTheRequestedPage() = runBlocking<Unit> {
        val projects = (1..ROW_COUNT).map { index ->
            Project(
                projectId = "P%05d".format(Locale.US, index),
                projectName = "Project %05d".format(Locale.US, index),
                projectStatus = when {
                    index % 10 == 0 -> "PO"
                    index % 10 == 1 -> "Lost"
                    else -> "Quotation"
                },
                opportunityScore = listOf("HOT", "WARM", "COLD")[index % 3],
                startDate = "2026-%02d-%02d".format(
                    Locale.US,
                    (index % 12) + 1,
                    (index % 28) + 1
                ),
                // ต้องเป็นเจ้าของเดียวกับ ownerId ที่ส่งให้คิวรีด้านล่าง ตั้งแต่ getProjectsPaging
                // เพิ่ม WHERE create_by = :ownerId ไม่งั้นถูกกรองทิ้งทั้ง 50,000 แถว แล้ววัดผลไม่ได้
                createBy = "U1"
            )
        }

        val insertMs = measureTimeMillis { database.projectDao().insertProjects(projects) }
        lateinit var page: List<Project>
        val firstPageMs = measureTimeMillis {
            val result = database.projectDao().getProjectsPaging(
                ownerId = "U1",
                searchQuery = "",
                tabIndex = 0,
                closedStatuses = listOf("PO", "Lost", "Failed"),
                wonStatuses = listOf("PO"),
                lostStatuses = listOf("Lost", "Failed"),
                applyStatusFilter = false,
                selectedStatuses = emptyList(),
                applyScoreFilter = false,
                selectedScores = emptyList()
            ).load(
                PagingSource.LoadParams.Refresh(
                    key = null,
                    loadSize = PAGE_SIZE,
                    placeholdersEnabled = false
                )
            )
            check(result is PagingSource.LoadResult.Page) { "Expected a page but got $result" }
            page = result.data
        }

        assertEquals(PAGE_SIZE, page.size)
        Log.i(
            TAG,
            "rows=$ROW_COUNT inserted_ms=$insertMs first_page_rows=${page.size} " +
                "first_page_ms=$firstPageMs"
        )
    }

    private companion object {
        const val TAG = "ProjectPagingStress"

        /**
         * เดิม 50,000 — ลดลงเพราะเทสนี้ใช้ `inMemoryDatabaseBuilder` ฐานข้อมูลทั้งก้อนจึงอยู่ใน RAM
         * พร้อมกับ list ของ Project อีก 50,000 ออบเจกต์ บนเครื่องทดสอบจริง (RAM 3.5G ใช้ไปแล้ว 3.3G)
         * kernel ยิง lowmemorykiller รัวจน process ค้างสนิท (CPU 0% ไม่ขยับ) และ test runner
         * ตั้ง timeout ไว้ 365 วัน จึงค้างตลอดกาลไม่มีทางหลุดเอง
         *
         * ของเดิมไม่เคยเจอปัญหานี้เพราะคิวรีได้ 0 แถวมาตลอด (fixture ไม่มีเจ้าของ ไม่ผ่าน
         * WHERE create_by = :ownerId) เทสจึงพังที่ assertion ก่อนจะได้ materialize ข้อมูลจริงเลย
         *
         * 10,000 แถวยังพิสูจน์เจตนาเดิมได้ครบ: ถ้า paging ทำงานผิดแล้วลากทั้งตารางมา จะเห็น
         * ทันทีว่าได้ 10,000 แทนที่จะเป็น 60
         */
        const val ROW_COUNT = 10_000
        const val PAGE_SIZE = 60
    }
}
