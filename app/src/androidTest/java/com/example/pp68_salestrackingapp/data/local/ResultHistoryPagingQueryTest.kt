package com.example.pp68_salestrackingapp.data.local

import android.content.Context
import androidx.paging.PagingSource
import androidx.room.Room
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pp68_salestrackingapp.data.model.ActivityResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ResultHistoryPagingQueryTest {
    private lateinit var database: AppDatabase

    @Before
    fun createDatabase() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        listOf(
            ActivityResult(resultId = "R-2B", resultGroupId = "G1", version = 2),
            ActivityResult(resultId = "R-1", resultGroupId = "G1", version = 1),
            ActivityResult(resultId = "R-2A", resultGroupId = "G1", version = 2),
            ActivityResult(resultId = "R-OTHER", resultGroupId = "G2", version = 9)
        ).forEach { database.activityResultDao().insertResult(it) }
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun historyIsScopedAndHasStableVersionThenIdOrder() = runBlocking<Unit> {
        val result = database.activityResultDao().getVersionHistoryPaging("G1").load(
            PagingSource.LoadParams.Refresh(null, 100, false)
        )
        check(result is PagingSource.LoadResult.Page)
        assertEquals(listOf("R-2A", "R-2B", "R-1"), result.data.map(ActivityResult::resultId))

        val details = database.openHelper.readableDatabase.query(
            SimpleSQLiteQuery(
                "EXPLAIN QUERY PLAN SELECT * FROM activity_result " +
                    "WHERE result_group_id = 'G1' ORDER BY version DESC, result_id ASC"
            )
        ).use { cursor ->
            val detailIndex = cursor.getColumnIndexOrThrow("detail")
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(detailIndex))
            }
        }
        assertTrue(details.joinToString().contains("index_result_group_version_id"))
        assertTrue(details.none { it.contains("USE TEMP B-TREE") })
    }
}
