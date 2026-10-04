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
    fun fiftyThousandRowsStillMaterializeOnlyTheRequestedPage() = runBlocking<Unit> {
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
                )
            )
        }

        val insertMs = measureTimeMillis { database.projectDao().insertProjects(projects) }
        lateinit var page: List<Project>
        val firstPageMs = measureTimeMillis {
            val result = database.projectDao().getProjectsPaging(
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
        const val ROW_COUNT = 50_000
        const val PAGE_SIZE = 60
    }
}
