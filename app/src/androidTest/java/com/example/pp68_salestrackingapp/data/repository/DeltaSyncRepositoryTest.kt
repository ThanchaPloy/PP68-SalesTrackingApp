package com.example.pp68_salestrackingapp.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pp68_salestrackingapp.data.local.AppDatabase
import com.example.pp68_salestrackingapp.data.model.Customer
import com.example.pp68_salestrackingapp.data.model.Project
import com.example.pp68_salestrackingapp.data.model.SyncChangeEvent
import com.example.pp68_salestrackingapp.data.model.SyncChangePage
import com.example.pp68_salestrackingapp.data.model.SyncState
import com.example.pp68_salestrackingapp.data.model.SyncSnapshotPage
import com.example.pp68_salestrackingapp.data.remote.ApiService
import com.google.gson.JsonParser
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.lang.reflect.Proxy

@RunWith(AndroidJUnit4::class)
class DeltaSyncRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: DeltaSyncRepository
    private val accountKey = syncAccountKey("U1")

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val unusedApi = Proxy.newProxyInstance(
            ApiService::class.java.classLoader,
            arrayOf(ApiService::class.java)
        ) { _, method, _ -> throw UnsupportedOperationException(method.name) } as ApiService
        repository = DeltaSyncRepository(unusedApi, database, Gson())
        database.syncStateDao().upsert(
            SyncState(
                accountKey = accountKey,
                cursor = 10,
                snapshotCursor = 10,
                bootstrapStatus = SyncState.STATUS_READY
            )
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun bootstrapStoresCursorBeforeSnapshotAndRequiresExplicitCompletion() = runBlocking {
        val cursorApi = Proxy.newProxyInstance(
            ApiService::class.java.classLoader,
            arrayOf(ApiService::class.java)
        ) { _, method, _ ->
            if (method.name == "getSyncCursor") Response.success(
                com.example.pp68_salestrackingapp.data.model.SyncCursorResponse(77)
            ) else throw UnsupportedOperationException(method.name)
        } as ApiService
        val freshRepository = DeltaSyncRepository(cursorApi, database, Gson())

        assertEquals(77L, freshRepository.beginBootstrap("U2").getOrThrow())
        val inProgress = database.syncStateDao().get(syncAccountKey("U2"))
        assertEquals(SyncState.STATUS_SNAPSHOT_IN_PROGRESS, inProgress?.bootstrapStatus)
        assertEquals(77L, inProgress?.snapshotCursor)

        freshRepository.markBootstrapComplete("U2", 77)
        val ready = database.syncStateDao().get(syncAccountKey("U2"))
        assertEquals(SyncState.STATUS_READY, ready?.bootstrapStatus)
        assertEquals(77L, ready?.cursor)
    }

    @Test
    fun upsertAndCursorCommitTogether() = runBlocking {
        val event = event(
            seq = 11,
            type = "lead_customer",
            id = "LEAD-1",
            payload = """{"customer_code":"LEAD-1","customer_name":"Lead One"}"""
        )

        repository.applyPage(accountKey, 10, SyncChangePage(listOf(event), 11, false))

        val customer = database.customerDao().getCustomerById("LEAD-1")
        assertEquals("Lead One", customer?.companyName)
        assertEquals(true, customer?.isLead)
        assertEquals(11L, database.syncStateDao().get(accountKey)?.cursor)
    }

    @Test
    fun pendingConflictKeepsLocalRowStoresServerVersionAndAdvancesCursor() = runBlocking {
        database.projectDao().insertProject(
            Project(projectId = "P-1", projectName = "Local edit", isSynced = false)
        )
        val lead = event(
            11,
            "lead_customer",
            "LEAD-2",
            """{"customer_code":"LEAD-2","customer_name":"Should Roll Back"}"""
        )
        val project = event(
            12,
            "project",
            "P-1",
            """{"project_code":"P-1","project_name":"Server edit"}"""
        )

        repository.applyPage(accountKey, 10, SyncChangePage(listOf(lead, project), 12, false))

        assertEquals("Should Roll Back", database.customerDao().getCustomerById("LEAD-2")?.companyName)
        assertEquals("Local edit", database.projectDao().getProjectById("P-1")?.projectName)
        val conflict = database.syncConflictDao().getForAccount(accountKey).single()
        assertEquals("project", conflict.entityType)
        assertEquals("P-1", conflict.entityId)
        assertEquals(12L, conflict.serverRevision)
        assertEquals(true, conflict.serverPayloadJson?.contains("Server edit"))
        assertEquals(12L, database.syncStateDao().get(accountKey)?.cursor)
    }

    @Test
    fun snapshotInProgressSurvivesRepositoryRecreationAndBlocksDelta() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "delta-process-death-test"
        context.deleteDatabase(dbName)
        val key = syncAccountKey("U-PROCESS-DEATH")
        val beforeProcessDeath = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .build()
        beforeProcessDeath.syncStateDao().upsert(
            SyncState(
                accountKey = key,
                cursor = 90,
                snapshotCursor = 90,
                bootstrapStatus = SyncState.STATUS_SNAPSHOT_IN_PROGRESS
            )
        )
        beforeProcessDeath.close()

        val reopenedDatabase = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .build()
        try {
            val recreated = DeltaSyncRepository(apiReturningChanges(), reopenedDatabase, Gson())
            val result = recreated.syncNextPage("U-PROCESS-DEATH")

            assertFalse(result.isSuccess)
            assertEquals(
                SyncState.STATUS_SNAPSHOT_IN_PROGRESS,
                recreated.getState("U-PROCESS-DEATH")?.bootstrapStatus
            )
            assertEquals(90L, recreated.getState("U-PROCESS-DEATH")?.snapshotCursor)
        } finally {
            reopenedDatabase.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun expiredCursorRequiresFreshSnapshotWithoutDeletingBusinessRows() = runBlocking {
        database.customerDao().insertCustomer(
            Customer(custId = "LEAD-KEEP", companyName = "Keep me", isLead = true, isSynced = true)
        )
        val expiredApi = Proxy.newProxyInstance(
            ApiService::class.java.classLoader,
            arrayOf(ApiService::class.java)
        ) { _, method, _ ->
            if (method.name == "getSyncChanges") {
                Response.error<SyncChangePage>(410, "expired".toResponseBody())
            } else throw UnsupportedOperationException(method.name)
        } as ApiService

        val result = DeltaSyncRepository(expiredApi, database, Gson()).syncNextPage("U1")

        assertFalse(result.isSuccess)
        assertEquals(true, result.exceptionOrNull() is DeltaSyncCursorExpiredException)
        val reset = database.syncStateDao().get(accountKey)
        assertEquals(SyncState.STATUS_NOT_STARTED, reset?.bootstrapStatus)
        assertEquals(0L, reset?.cursor)
        assertNull(reset?.snapshotCursor)
        assertEquals("Keep me", database.customerDao().getCustomerById("LEAD-KEEP")?.companyName)
    }

    @Test
    fun pagedShadowSnapshotCommitsOnlyAfterEveryEntityCompletesAndPreservesPending() = runBlocking {
        database.projectDao().insertProject(
            Project(projectId = "P-PENDING", projectName = "Local edit", isSynced = false)
        )
        database.projectDao().insertProject(
            Project(projectId = "P-STALE", projectName = "Stale", isSynced = true)
        )
        val api = snapshotApi { entity, after ->
            when (entity to after) {
                "project" to null -> SyncSnapshotPage(
                    items = listOf(JsonParser.parseString(
                        """{"project_code":"P-NEW","project_name":"New server row"}"""
                    ).asJsonObject),
                    nextAfter = "P-NEW",
                    hasMore = true
                )
                "project" to "P-NEW" -> SyncSnapshotPage(
                    items = listOf(JsonParser.parseString(
                        """{"project_code":"P-PENDING","project_name":"Server edit"}"""
                    ).asJsonObject),
                    nextAfter = null,
                    hasMore = false
                )
                else -> SyncSnapshotPage(emptyList(), null, false)
            }
        }

        DeltaSyncRepository(api, database, Gson())
            .bootstrapSnapshot("U-SNAPSHOT", pageLimit = 1).getOrThrow()

        assertEquals("New server row", database.projectDao().getProjectById("P-NEW")?.projectName)
        assertEquals("Local edit", database.projectDao().getProjectById("P-PENDING")?.projectName)
        assertNull(database.projectDao().getProjectById("P-STALE"))
        assertEquals(1, database.syncConflictDao().countForAccount(syncAccountKey("U-SNAPSHOT")))
        assertEquals(
            SyncState.STATUS_READY,
            database.syncStateDao().get(syncAccountKey("U-SNAPSHOT"))?.bootstrapStatus
        )
        assertEquals(
            0,
            database.syncSnapshotDao().getItems(syncAccountKey("U-SNAPSHOT"), "project").size
        )
    }

    @Test
    fun failedSnapshotLeavesVisibleCacheUntouchedAndBootstrapIncomplete() = runBlocking {
        database.projectDao().insertProject(
            Project(projectId = "P-OLD", projectName = "Still visible", isSynced = true)
        )
        val api = Proxy.newProxyInstance(
            ApiService::class.java.classLoader,
            arrayOf(ApiService::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getSyncCursor" -> Response.success(
                    com.example.pp68_salestrackingapp.data.model.SyncCursorResponse(77)
                )
                "getSyncSnapshot" -> {
                    val entity = args?.get(0) as String
                    if (entity == "project") {
                        Response.error<SyncSnapshotPage>(503, "temporary".toResponseBody())
                    } else Response.success(SyncSnapshotPage(emptyList(), null, false))
                }
                else -> throw UnsupportedOperationException(method.name)
            }
        } as ApiService
        val snapshotRepository = DeltaSyncRepository(api, database, Gson())

        val result = snapshotRepository.bootstrapSnapshot("U-FAILED")

        assertFalse(result.isSuccess)
        assertEquals("Still visible", database.projectDao().getProjectById("P-OLD")?.projectName)
        assertEquals(
            SyncState.STATUS_SNAPSHOT_IN_PROGRESS,
            snapshotRepository.getState("U-FAILED")?.bootstrapStatus
        )
    }

    @Test
    fun tombstoneDeletesOnlySyncedRowAndAdvancesCursor() = runBlocking {
        database.customerDao().insertCustomer(
            Customer(custId = "LEAD-3", companyName = "Delete me", isLead = true, isSynced = true)
        )
        val deletion = SyncChangeEvent(
            seq = 11,
            entityType = "lead_customer",
            entityId = "LEAD-3",
            operation = "DELETE",
            serverRevision = 11,
            changedAt = "2026-10-05T00:00:00Z",
            payload = null
        )

        repository.applyPage(accountKey, 10, SyncChangePage(listOf(deletion), 15, false))

        assertNull(database.customerDao().getCustomerById("LEAD-3"))
        assertEquals(15L, database.syncStateDao().get(accountKey)?.cursor)
    }

    private fun event(seq: Long, type: String, id: String, payload: String) = SyncChangeEvent(
        seq = seq,
        entityType = type,
        entityId = id,
        operation = "UPSERT",
        serverRevision = seq,
        changedAt = "2026-10-05T00:00:00Z",
        payload = JsonParser.parseString(payload).asJsonObject
    )

    private fun apiReturningChanges(): ApiService = Proxy.newProxyInstance(
        ApiService::class.java.classLoader,
        arrayOf(ApiService::class.java)
    ) { _, method, _ ->
        if (method.name == "getSyncChanges") {
            Response.success(SyncChangePage(emptyList(), 90, false))
        } else throw UnsupportedOperationException(method.name)
    } as ApiService

    private fun snapshotApi(page: (String, String?) -> SyncSnapshotPage): ApiService =
        Proxy.newProxyInstance(
            ApiService::class.java.classLoader,
            arrayOf(ApiService::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getSyncCursor" -> Response.success(
                    com.example.pp68_salestrackingapp.data.model.SyncCursorResponse(77)
                )
                "getSyncSnapshot" -> Response.success(
                    page(args?.get(0) as String, args[1] as String?)
                )
                else -> throw UnsupportedOperationException(method.name)
            }
        } as ApiService
}
