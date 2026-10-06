package com.example.pp68_salestrackingapp.data.repository

import androidx.room.withTransaction
import com.example.pp68_salestrackingapp.data.local.AppDatabase
import com.example.pp68_salestrackingapp.data.model.ActivityResult
import com.example.pp68_salestrackingapp.data.model.AppointmentContact
import com.example.pp68_salestrackingapp.data.model.ContactPerson
import com.example.pp68_salestrackingapp.data.model.Customer
import com.example.pp68_salestrackingapp.data.model.Project
import com.example.pp68_salestrackingapp.data.model.SalesActivity
import com.example.pp68_salestrackingapp.data.model.SyncChangeEvent
import com.example.pp68_salestrackingapp.data.model.SyncChangePage
import com.example.pp68_salestrackingapp.data.model.SyncConflict
import com.example.pp68_salestrackingapp.data.model.SyncState
import com.example.pp68_salestrackingapp.data.model.SyncSnapshotItem
import com.example.pp68_salestrackingapp.data.model.SyncSnapshotPage
import com.example.pp68_salestrackingapp.data.remote.ApiService
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

class DeltaSyncProtocolException(message: String) : IllegalStateException(message)
class DeltaSyncCursorMismatchException(message: String) : IllegalStateException(message)
class DeltaSyncCursorExpiredException(message: String) : IllegalStateException(message)

internal fun syncAccountKey(userId: String): String = MessageDigest.getInstance("SHA-256")
    .digest(userId.trim().toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it) }

@Singleton
class DeltaSyncRepository @Inject constructor(
    private val apiService: ApiService,
    private val database: AppDatabase,
    private val gson: Gson
) {
    private val stateDao get() = database.syncStateDao()

    suspend fun getState(userId: String): SyncState? = stateDao.get(syncAccountKey(userId))

    suspend fun getConflicts(userId: String): List<SyncConflict> =
        database.syncConflictDao().getForAccount(syncAccountKey(userId))

    /**
     * Must run before the legacy/keyset snapshot starts. A process death leaves the state marked
     * IN_PROGRESS, so the caller knows it must restart the snapshot instead of trusting a partial DB.
     */
    suspend fun beginBootstrap(userId: String): Result<Long> = runCatchingPreservingCancellation {
        val response = apiService.getSyncCursor()
        if (!response.isSuccessful || response.body() == null) {
            throw IllegalStateException("Sync cursor HTTP ${response.code()}")
        }
        val cursor = requireNotNull(response.body()).cursor
        if (cursor < 0L) throw DeltaSyncProtocolException("Server returned a negative cursor")
        val accountKey = syncAccountKey(userId)
        database.withTransaction {
            database.syncSnapshotDao().deleteForAccount(accountKey)
            stateDao.upsert(
                SyncState(
                    accountKey = accountKey,
                    cursor = cursor,
                    snapshotCursor = cursor,
                    bootstrapStatus = SyncState.STATUS_SNAPSHOT_IN_PROGRESS
                )
            )
        }
        cursor
    }

    /**
     * Downloads every keyset page into a durable shadow table, then atomically swaps the complete
     * snapshot into visible tables. Existing cache remains usable until the final transaction.
     */
    suspend fun bootstrapSnapshot(userId: String, pageLimit: Int = 200): Result<Unit> =
        runCatchingPreservingCancellation {
            require(pageLimit in 1..500) { "Snapshot limit must be between 1 and 500" }
            val accountKey = syncAccountKey(userId)
            val snapshotCursor = beginBootstrap(userId).getOrThrow()
            SNAPSHOT_ENTITY_TYPES.forEach { entityType ->
                downloadSnapshotEntity(accountKey, entityType, pageLimit)
            }
            commitSnapshot(accountKey, snapshotCursor)
        }

    suspend fun syncUntilCaughtUp(
        userId: String,
        pageLimit: Int = 200,
        maxPages: Int = 1_000
    ): Result<Int> = runCatchingPreservingCancellation {
        var pages = 0
        while (true) {
            if (pages >= maxPages) {
                throw DeltaSyncProtocolException("Delta exceeded $maxPages pages in one run")
            }
            val page = syncNextPage(userId, pageLimit).getOrThrow()
            pages++
            if (!page.hasMore) return@runCatchingPreservingCancellation pages
        }
        @Suppress("UNREACHABLE_CODE")
        pages
    }

    private suspend fun downloadSnapshotEntity(
        accountKey: String,
        entityType: String,
        pageLimit: Int
    ) {
        var after: String? = null
        do {
            val response = apiService.getSyncSnapshot(entityType, after, pageLimit)
            if (!response.isSuccessful || response.body() == null) {
                throw IllegalStateException("Snapshot $entityType HTTP ${response.code()}")
            }
            val page = requireNotNull(response.body())
            validateSnapshotPage(entityType, after, page, pageLimit)
            val staged = page.items.map { payload ->
                SyncSnapshotItem(
                    accountKey = accountKey,
                    entityType = entityType,
                    itemKey = snapshotItemKey(entityType, payload),
                    payloadJson = payload.toString()
                )
            }
            database.withTransaction {
                val state = stateDao.get(accountKey)
                    ?: throw DeltaSyncCursorMismatchException("Bootstrap state disappeared")
                if (state.bootstrapStatus != SyncState.STATUS_SNAPSHOT_IN_PROGRESS) {
                    throw DeltaSyncCursorMismatchException("Bootstrap is no longer active")
                }
                if (staged.isNotEmpty()) database.syncSnapshotDao().upsertAll(staged)
            }
            after = page.nextAfter
        } while (page.hasMore)
    }

    private fun validateSnapshotPage(
        entityType: String,
        previousAfter: String?,
        page: SyncSnapshotPage,
        requestedLimit: Int
    ) {
        if (page.items.size > requestedLimit) {
            throw DeltaSyncProtocolException("Snapshot $entityType exceeded requested page size")
        }
        val keys = page.items.map { snapshotItemKey(entityType, it) }
        if (keys.size != keys.toSet().size) {
            throw DeltaSyncProtocolException("Snapshot $entityType contains duplicate keys")
        }
        if (page.hasMore && (page.items.isEmpty() || page.nextAfter.isNullOrBlank())) {
            throw DeltaSyncProtocolException("Snapshot $entityType has_more without next_after")
        }
        if (page.hasMore && page.nextAfter == previousAfter) {
            throw DeltaSyncProtocolException("Snapshot $entityType cursor did not advance")
        }
        if (!page.hasMore && page.nextAfter != null) {
            throw DeltaSyncProtocolException("Snapshot $entityType final page must not have next_after")
        }
    }

    private fun snapshotItemKey(entityType: String, payload: com.google.gson.JsonObject): String {
        fun required(name: String): String = payload.get(name)?.takeIf { it.isJsonPrimitive }
            ?.asString?.takeIf { it.isNotBlank() }
            ?: throw DeltaSyncProtocolException("Snapshot $entityType item has no $name")
        return when (entityType) {
            "lead_customer" -> required("customer_code")
            "contact_person" -> required("contact_id")
            "project" -> required("project_code")
            "appointment" -> required("appointment_id")
            "activity_result" -> required("result_id")
            "appointment_contact" -> encodeRelationId(required("appointment_id"), required("contact_id"))
            else -> throw DeltaSyncProtocolException("Unknown snapshot entity $entityType")
        }
    }

    private suspend fun commitSnapshot(accountKey: String, expectedSnapshotCursor: Long) {
        database.withTransaction {
            val current = stateDao.get(accountKey)
                ?: throw DeltaSyncCursorMismatchException("Bootstrap state is missing")
            if (current.bootstrapStatus != SyncState.STATUS_SNAPSHOT_IN_PROGRESS ||
                current.snapshotCursor != expectedSnapshotCursor
            ) {
                throw DeltaSyncCursorMismatchException("Bootstrap cursor/status changed")
            }

            val leads = staged<Customer>(accountKey, "lead_customer")
                .map { it.copy(isLead = true, isSynced = true) }
            val contacts = staged<ContactPerson>(accountKey, "contact_person")
                .map { it.copy(isSynced = true) }
            val projects = staged<Project>(accountKey, "project")
                .map { it.copy(isSynced = true) }
            val appointments = staged<SalesActivity>(accountKey, "appointment")
                .map { it.copy(isSynced = true) }
            val results = staged<ActivityResult>(accountKey, "activity_result")
                .map { it.copy(isSynced = true) }
            val appointmentContacts = staged<AppointmentContact>(accountKey, "appointment_contact")

            recordSnapshotConflicts(accountKey, expectedSnapshotCursor, leads, contacts, projects, appointments, results)
            database.customerDao().replaceLeadSnapshot(leads)
            database.contactDao().clearAndInsert(contacts)
            database.projectDao().clearAndInsert(projects)
            database.activityDao().clearAndInsert(appointments)
            database.activityResultDao().clearAndInsert(results)
            database.appointmentContactDao().deleteForSyncedAppointments()
            if (appointmentContacts.isNotEmpty()) {
                database.appointmentContactDao().insertAppointmentContacts(appointmentContacts)
            }
            stateDao.upsert(
                current.copy(
                    cursor = expectedSnapshotCursor,
                    bootstrapStatus = SyncState.STATUS_READY,
                    updatedAtEpochMs = System.currentTimeMillis()
                )
            )
            database.syncSnapshotDao().deleteForAccount(accountKey)
        }
    }

    private suspend inline fun <reified T> staged(accountKey: String, entityType: String): List<T> =
        database.syncSnapshotDao().getItems(accountKey, entityType)
            .map { gson.fromJson(it.payloadJson, T::class.java) }

    private suspend fun recordSnapshotConflicts(
        accountKey: String,
        snapshotCursor: Long,
        leads: List<Customer>,
        contacts: List<ContactPerson>,
        projects: List<Project>,
        appointments: List<SalesActivity>,
        results: List<ActivityResult>
    ) {
        val pendingByType = mapOf(
            "lead_customer" to database.customerDao().getUnsyncedCustomerIds().toSet(),
            "contact_person" to database.contactDao().getUnsyncedContactIds().toSet(),
            "project" to database.projectDao().getUnsyncedProjectIds().toSet(),
            "appointment" to database.activityDao().getUnsyncedActivityIds().toSet(),
            "activity_result" to database.activityResultDao().getUnsyncedResultIds().toSet()
        )
        val rowsByType: Map<String, List<Pair<String, Any>>> = mapOf(
            "lead_customer" to leads.map { it.custId to it },
            "contact_person" to contacts.map { it.contactId to it },
            "project" to projects.map { it.projectId to it },
            "appointment" to appointments.map { it.activityId to it },
            "activity_result" to results.map { it.resultId to it }
        )
        rowsByType.forEach { (entityType, rows) ->
            val pending = pendingByType.getValue(entityType)
            rows.filter { it.first in pending }.forEach { (id, row) ->
                database.syncConflictDao().upsert(
                    SyncConflict(
                        accountKey = accountKey,
                        entityType = entityType,
                        entityId = id,
                        operation = "UPSERT",
                        serverRevision = snapshotCursor,
                        serverSeq = snapshotCursor,
                        serverPayloadJson = gson.toJson(row)
                    )
                )
            }
        }
    }

    /** Call only after every required snapshot page has committed successfully. */
    suspend fun markBootstrapComplete(userId: String, expectedSnapshotCursor: Long) {
        val accountKey = syncAccountKey(userId)
        database.withTransaction {
            val current = stateDao.get(accountKey)
                ?: throw DeltaSyncCursorMismatchException("Bootstrap state is missing")
            if (current.bootstrapStatus != SyncState.STATUS_SNAPSHOT_IN_PROGRESS ||
                current.snapshotCursor != expectedSnapshotCursor
            ) {
                throw DeltaSyncCursorMismatchException("Bootstrap cursor/status changed")
            }
            stateDao.upsert(
                current.copy(
                    cursor = expectedSnapshotCursor,
                    bootstrapStatus = SyncState.STATUS_READY,
                    updatedAtEpochMs = System.currentTimeMillis()
                )
            )
        }
    }

    /** Fetches and atomically applies one page. This is not wired into DownloadSyncWorker yet. */
    suspend fun syncNextPage(userId: String, limit: Int = 100): Result<SyncChangePage> =
        runCatchingPreservingCancellation {
            require(limit in 1..500) { "Delta limit must be between 1 and 500" }
            val accountKey = syncAccountKey(userId)
            val state = stateDao.get(accountKey)
                ?: throw DeltaSyncCursorMismatchException("Delta bootstrap has not started")
            if (state.bootstrapStatus != SyncState.STATUS_READY) {
                throw DeltaSyncCursorMismatchException("Delta bootstrap is not complete")
            }
            val response = apiService.getSyncChanges(state.cursor, limit)
            if (response.code() == 409 || response.code() == 410) {
                database.withTransaction {
                    val current = stateDao.get(accountKey)
                    if (current?.cursor == state.cursor) {
                        stateDao.upsert(
                            current.copy(
                                cursor = 0,
                                snapshotCursor = null,
                                bootstrapStatus = SyncState.STATUS_NOT_STARTED,
                                updatedAtEpochMs = System.currentTimeMillis()
                            )
                        )
                    }
                }
                throw DeltaSyncCursorExpiredException(
                    "Delta cursor ${state.cursor} is no longer accepted; snapshot restart required"
                )
            }
            if (!response.isSuccessful || response.body() == null) {
                throw IllegalStateException("Delta sync HTTP ${response.code()}")
            }
            val page = requireNotNull(response.body())
            applyPage(accountKey, state.cursor, page)
            page
        }

    internal suspend fun applyPage(accountKey: String, expectedCursor: Long, page: SyncChangePage) {
        validatePage(expectedCursor, page)
        database.withTransaction {
            val current = stateDao.get(accountKey)
                ?: throw DeltaSyncCursorMismatchException("Sync state disappeared")
            if (current.bootstrapStatus != SyncState.STATUS_READY || current.cursor != expectedCursor) {
                throw DeltaSyncCursorMismatchException(
                    "Expected cursor $expectedCursor but local cursor is ${current.cursor}"
                )
            }
            page.items.forEach { applyEvent(accountKey, it) }
            stateDao.upsert(
                current.copy(
                    cursor = page.nextCursor,
                    updatedAtEpochMs = System.currentTimeMillis()
                )
            )
        }
    }

    private fun validatePage(expectedCursor: Long, page: SyncChangePage) {
        var previous = expectedCursor
        page.items.forEach { event ->
            if (event.seq <= previous) {
                throw DeltaSyncProtocolException("Change seq must be strictly increasing")
            }
            if (event.serverRevision != event.seq) {
                throw DeltaSyncProtocolException("Unexpected server revision for seq ${event.seq}")
            }
            previous = event.seq
        }
        if (page.nextCursor < previous || page.nextCursor < expectedCursor) {
            throw DeltaSyncProtocolException("next_cursor moved backwards")
        }
        if (page.hasMore && (page.items.isEmpty() || page.nextCursor != previous)) {
            throw DeltaSyncProtocolException("has_more page must end at its last item")
        }
    }

    private suspend fun applyEvent(accountKey: String, event: SyncChangeEvent) {
        when (event.operation) {
            "UPSERT" -> applyUpsert(accountKey, event)
            "DELETE" -> applyDelete(accountKey, event)
            else -> throw DeltaSyncProtocolException("Unknown operation ${event.operation}")
        }
    }

    private suspend fun applyUpsert(accountKey: String, event: SyncChangeEvent) {
        val payload = event.payload
            ?: throw DeltaSyncProtocolException("UPSERT ${event.entityType}/${event.entityId} has no payload")
        when (event.entityType) {
            "lead_customer" -> {
                val incoming = gson.fromJson(payload, Customer::class.java)
                requireId(event, incoming.custId)
                val old = database.customerDao().getCustomerById(event.entityId)
                if (retainPendingAsConflict(accountKey, event, old?.isSynced)) return
                database.customerDao().insertCustomers(
                    listOf(incoming.copy(isLead = true, isSynced = true, operationId = old?.operationId))
                )
            }
            "contact_person" -> {
                val incoming = gson.fromJson(payload, ContactPerson::class.java)
                requireId(event, incoming.contactId)
                val old = database.contactDao().getContactById(event.entityId)
                if (retainPendingAsConflict(accountKey, event, old?.isSynced)) return
                database.contactDao().insertAll(listOf(incoming.copy(isSynced = true)))
            }
            "project" -> {
                val incoming = gson.fromJson(payload, Project::class.java)
                requireId(event, incoming.projectId)
                val old = database.projectDao().getProjectById(event.entityId)
                if (retainPendingAsConflict(accountKey, event, old?.isSynced)) return
                database.projectDao().insertProjects(
                    listOf(incoming.copy(isSynced = true, operationId = old?.operationId))
                )
            }
            "appointment" -> {
                val incoming = gson.fromJson(payload, SalesActivity::class.java)
                requireId(event, incoming.activityId)
                val old = database.activityDao().getActivityById(event.entityId)
                if (retainPendingAsConflict(accountKey, event, old?.isSynced)) return
                database.activityDao().insertActivities(
                    listOf(
                        incoming.copy(
                            projectName = old?.projectName,
                            companyName = old?.companyName,
                            contactName = old?.contactName,
                            weeklyNote = old?.weeklyNote,
                            locationName = old?.locationName,
                            isSynced = true,
                            operationId = old?.operationId
                        )
                    )
                )
            }
            "activity_result" -> {
                val incoming = gson.fromJson(payload, ActivityResult::class.java)
                requireId(event, incoming.resultId)
                val old = database.activityResultDao().getResultById(event.entityId)
                if (retainPendingAsConflict(accountKey, event, old?.isSynced)) return
                database.activityResultDao().insertAll(
                    listOf(incoming.copy(isSynced = true, operationId = old?.operationId))
                )
            }
            "appointment_contact" -> {
                val incoming = gson.fromJson(payload, AppointmentContact::class.java)
                val expectedId = encodeRelationId(incoming.appointmentId, incoming.contactId)
                requireId(event, expectedId)
                val parent = database.activityDao().getActivityById(incoming.appointmentId)
                    ?: throw DeltaSyncProtocolException(
                        "appointment_contact parent ${incoming.appointmentId} is missing"
                    )
                if (retainPendingAsConflict(accountKey, event, parent.isSynced)) return
                database.appointmentContactDao().insertAppointmentContacts(listOf(incoming))
            }
            else -> throw DeltaSyncProtocolException("Unknown entity type ${event.entityType}")
        }
        database.syncConflictDao().delete(accountKey, event.entityType, event.entityId)
    }

    private suspend fun applyDelete(accountKey: String, event: SyncChangeEvent) {
        if (event.payload != null) {
            throw DeltaSyncProtocolException("DELETE ${event.entityType}/${event.entityId} must not contain payload")
        }
        when (event.entityType) {
            "lead_customer" -> database.customerDao().getCustomerById(event.entityId)?.let {
                if (retainPendingAsConflict(accountKey, event, it.isSynced)) return
                database.customerDao().deleteCustomerById(event.entityId)
            }
            "contact_person" -> database.contactDao().getContactById(event.entityId)?.let {
                if (retainPendingAsConflict(accountKey, event, it.isSynced)) return
                database.contactDao().deleteContactById(event.entityId)
            }
            "project" -> database.projectDao().getProjectById(event.entityId)?.let {
                if (retainPendingAsConflict(accountKey, event, it.isSynced)) return
                database.projectDao().deleteProjectById(event.entityId)
            }
            "appointment" -> database.activityDao().getActivityById(event.entityId)?.let {
                if (retainPendingAsConflict(accountKey, event, it.isSynced)) return
                database.activityDao().deleteActivityById(event.entityId)
            }
            "activity_result" -> database.activityResultDao().getResultById(event.entityId)?.let {
                if (retainPendingAsConflict(accountKey, event, it.isSynced)) return
                database.activityResultDao().deleteResultById(event.entityId)
            }
            "appointment_contact" -> {
                val (appointmentId, contactId) = decodeRelationId(event.entityId)
                val parent = database.activityDao().getActivityById(appointmentId)
                if (parent != null) {
                    if (retainPendingAsConflict(accountKey, event, parent.isSynced)) return
                    database.appointmentContactDao().delete(appointmentId, contactId)
                }
            }
            else -> throw DeltaSyncProtocolException("Unknown entity type ${event.entityType}")
        }
        database.syncConflictDao().delete(accountKey, event.entityType, event.entityId)
    }

    private fun requireId(event: SyncChangeEvent, decodedId: String?) {
        if (decodedId.isNullOrBlank() || decodedId != event.entityId) {
            throw DeltaSyncProtocolException("Payload ID does not match ${event.entityType}/${event.entityId}")
        }
    }

    private fun encodeRelationId(appointmentId: String, contactId: String): String =
        (appointmentId + "\u0000" + contactId).toByteArray(Charsets.UTF_8)
            .joinToString("") { "%02x".format(it) }

    private fun decodeRelationId(encoded: String): Pair<String, String> {
        if (encoded.length % 2 != 0 || encoded.any { it !in "0123456789abcdefABCDEF" }) {
            throw DeltaSyncProtocolException("Invalid appointment_contact entity ID")
        }
        val decoded = encoded.chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
            .toString(Charsets.UTF_8)
        val separator = decoded.indexOf('\u0000')
        if (separator <= 0 || separator == decoded.lastIndex) {
            throw DeltaSyncProtocolException("Invalid appointment_contact composite ID")
        }
        return decoded.substring(0, separator) to decoded.substring(separator + 1)
    }

    private suspend fun retainPendingAsConflict(
        accountKey: String,
        event: SyncChangeEvent,
        isSynced: Boolean?
    ): Boolean {
        if (isSynced != false) return false
        database.syncConflictDao().upsert(
            SyncConflict(
                accountKey = accountKey,
                entityType = event.entityType,
                entityId = event.entityId,
                operation = event.operation,
                serverRevision = event.serverRevision,
                serverSeq = event.seq,
                serverPayloadJson = event.payload?.toString()
            )
        )
        return true
    }

    private suspend inline fun <T> runCatchingPreservingCancellation(
        crossinline block: suspend () -> T
    ): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private companion object {
        val SNAPSHOT_ENTITY_TYPES = listOf(
            "lead_customer",
            "contact_person",
            "project",
            "appointment",
            "activity_result",
            "appointment_contact"
        )
    }
}
