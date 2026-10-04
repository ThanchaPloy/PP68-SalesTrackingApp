package com.example.pp68_salestrackingapp.data.local

import android.content.Context
import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pp68_salestrackingapp.data.model.Project
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProjectPagingQueryTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: ProjectDao

    @Before
    fun createDatabase() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.projectDao()
        dao.insertProjects(
            listOf(
                project("P-A", "Alpha hot", "Quotation", "HOT", "2026-10-03"),
                project("P-B", "Beta warm", "Quotation", "WARM", "2026-10-03"),
                project("P-C", "Won alpha", "PO", "HOT", "2026-10-04"),
                project("P-D", "Lost alpha", "Lost", "COLD", "2026-10-05"),
                project("P-E", "Failed", "Failed", null, "2026-10-06"),
                project("P-F", "No status", null, null, "2026-10-02"),
                project("P-OLD", "Old active", "Lead", "HOT", "2025-01-01")
            )
        )
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun activeTabUsesStableDateDescendingAndIdAscendingOrder() = runBlocking {
        val rows = load(tabIndex = 0)

        assertEquals(listOf("P-A", "P-B", "P-F", "P-OLD"), rows.map(Project::projectId))
    }

    @Test
    fun tabsSeparateWonFromLostAndFailed() = runBlocking {
        assertEquals(listOf("P-C"), load(tabIndex = 1).map(Project::projectId))
        assertEquals(listOf("P-E", "P-D"), load(tabIndex = 2).map(Project::projectId))
    }

    @Test
    fun searchStatusAndScoreAreAppliedInsideThePagingQuery() = runBlocking {
        val rows = load(
            tabIndex = 0,
            searchQuery = "alpha",
            statuses = listOf("Quotation"),
            scores = listOf("HOT")
        )

        assertEquals(listOf("P-A"), rows.map(Project::projectId))
    }

    private suspend fun load(
        tabIndex: Int,
        searchQuery: String = "",
        statuses: List<String> = emptyList(),
        scores: List<String> = emptyList()
    ): List<Project> {
        val result = dao.getProjectsPaging(
            searchQuery = searchQuery,
            tabIndex = tabIndex,
            closedStatuses = listOf("PO", "Lost", "Failed"),
            wonStatuses = listOf("PO"),
            lostStatuses = listOf("Lost", "Failed"),
            applyStatusFilter = statuses.isNotEmpty(),
            selectedStatuses = statuses,
            applyScoreFilter = scores.isNotEmpty(),
            selectedScores = scores
        ).load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 100,
                placeholdersEnabled = false
            )
        )
        check(result is PagingSource.LoadResult.Page) {
            "Expected a page but got $result"
        }
        return result.data
    }

    private fun project(
        id: String,
        name: String,
        status: String?,
        score: String?,
        startDate: String
    ) = Project(
        projectId = id,
        projectName = name,
        projectStatus = status,
        opportunityScore = score,
        startDate = startDate
    )
}
