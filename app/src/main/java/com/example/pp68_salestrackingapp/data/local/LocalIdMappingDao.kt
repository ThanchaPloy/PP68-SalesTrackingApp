package com.example.pp68_salestrackingapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.pp68_salestrackingapp.data.model.LocalIdMapping
import com.example.pp68_salestrackingapp.data.model.Project
import com.example.pp68_salestrackingapp.data.model.SalesActivity
import com.example.pp68_salestrackingapp.data.model.Customer
import com.example.pp68_salestrackingapp.data.model.ContactPerson
import com.example.pp68_salestrackingapp.data.model.ActivityResult
import com.example.pp68_salestrackingapp.data.model.ActivityResultPhoto
import com.example.pp68_salestrackingapp.data.model.AttachmentOutbox
import com.example.pp68_salestrackingapp.data.model.ActivityPlanItem
import com.example.pp68_salestrackingapp.data.model.ProjectContact
import com.example.pp68_salestrackingapp.data.model.AppointmentContact
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalIdMappingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(mapping: LocalIdMapping)

    @Query("SELECT real_id FROM local_id_mapping WHERE entity_type = :entityType AND temp_id = :tempId LIMIT 1")
    suspend fun findRealId(entityType: String, tempId: String): String?

    @Query("SELECT real_id FROM local_id_mapping WHERE entity_type = :entityType AND temp_id = :tempId LIMIT 1")
    fun observeRealId(entityType: String, tempId: String): Flow<String?>

    /**
     * Resolves an ID retained by a screen after the sync worker has replaced its TEMP row.
     * If the row has not reached the server yet, the TEMP ID remains valid and is returned.
     */
    suspend fun resolveMappedId(entityType: String, id: String): String =
        if (id.startsWith("TEMP-")) findRealId(entityType, id) ?: id else id

    /**
     * Write/delete guard: a TEMP ID must either still exist locally or have a durable mapping.
     * This prevents a stale screen from silently writing to a row that no longer exists.
     */
    @Transaction
    suspend fun resolveExistingId(entityType: String, id: String): String =
        requireNotNull(resolveExisting(entityType, id))

    @Query("SELECT EXISTS(SELECT 1 FROM customer WHERE cust_id = :id)")
    suspend fun customerExists(id: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM contact_person WHERE contactId = :id)")
    suspend fun contactExists(id: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM project WHERE projectId = :projectId)")
    suspend fun projectExists(projectId: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM activity_table WHERE appointment_id = :id)")
    suspend fun activityExists(id: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM activity_result WHERE result_id = :id)")
    suspend fun resultExists(id: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomerRaw(customer: Customer)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertContactRaw(contact: ContactPerson): Long

    @Update
    suspend fun updateContactRaw(contact: ContactPerson)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertProjectRaw(project: Project): Long

    @Update
    suspend fun updateProjectRaw(project: Project)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertActivityRaw(activity: SalesActivity): Long

    @Update
    suspend fun updateActivityRaw(activity: SalesActivity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertResultRaw(result: ActivityResult): Long

    @Update
    suspend fun updateResultRaw(result: ActivityResult)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResultPhotosRaw(photos: List<ActivityResultPhoto>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAttachmentOutboxRaw(items: List<AttachmentOutbox>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlanItemsRaw(items: List<ActivityPlanItem>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProjectContactsRaw(items: List<ProjectContact>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppointmentContactsRaw(items: List<AppointmentContact>)

    @Query("UPDATE project SET custId = :realId WHERE custId = :tempId")
    suspend fun remapProjectCustomerIds(tempId: String, realId: String)

    @Query("UPDATE contact_person SET custId = :realId WHERE custId = :tempId")
    suspend fun remapContactCustomerIds(tempId: String, realId: String)

    @Query("UPDATE activity_table SET cust_id = :realId WHERE cust_id = :tempId")
    suspend fun remapActivityCustomerIds(tempId: String, realId: String)

    @Query("UPDATE OR IGNORE project_contact SET contact_id = :realId WHERE contact_id = :tempId")
    suspend fun remapProjectContactContactIds(tempId: String, realId: String)

    @Query("UPDATE OR IGNORE appointment_contact SET contact_id = :realId WHERE contact_id = :tempId")
    suspend fun remapAppointmentContactContactIds(tempId: String, realId: String)

    @Query("UPDATE activity_table SET project_id = :realId WHERE project_id = :tempId")
    suspend fun remapActivityProjectIds(tempId: String, realId: String)

    @Query("UPDATE activity_result SET project_id = :realId WHERE project_id = :tempId")
    suspend fun remapResultProjectIds(tempId: String, realId: String)

    @Query("UPDATE project_contact SET project_id = :realId WHERE project_id = :tempId")
    suspend fun remapProjectContactIds(tempId: String, realId: String)

    @Query("UPDATE appointment_contact SET appointment_id = :realId WHERE appointment_id = :tempId")
    suspend fun remapAppointmentContactAppointmentIds(tempId: String, realId: String)

    @Query("UPDATE activity_plan_item SET appointmentId = :realId WHERE appointmentId = :tempId")
    suspend fun remapPlanItemAppointmentIds(tempId: String, realId: String)

    @Query("UPDATE activity_result SET appointment_id = :realId WHERE appointment_id = :tempId")
    suspend fun remapResultAppointmentIds(tempId: String, realId: String)

    @Query("UPDATE activity_result_photo SET result_id = :realId WHERE result_id = :tempId")
    suspend fun remapResultPhotoIds(tempId: String, realId: String)

    @Query("UPDATE attachment_outbox SET result_id = :realId WHERE result_id = :tempId")
    suspend fun remapAttachmentResultIds(tempId: String, realId: String)

    @Query("UPDATE activity_result SET result_group_id = :realId WHERE result_group_id = :tempId")
    suspend fun remapResultGroupIds(tempId: String, realId: String)

    @Query("DELETE FROM project WHERE projectId = :projectId")
    suspend fun deleteProject(projectId: String)

    @Query("DELETE FROM customer WHERE cust_id = :id")
    suspend fun deleteCustomer(id: String)

    @Query("DELETE FROM contact_person WHERE contactId = :id")
    suspend fun deleteContact(id: String)

    @Query("DELETE FROM activity_table WHERE appointment_id = :id")
    suspend fun deleteActivity(id: String)

    @Query("DELETE FROM activity_result WHERE result_id = :id")
    suspend fun deleteResult(id: String)

    @Query("DELETE FROM project_contact WHERE project_id = :projectId")
    suspend fun deleteProjectContacts(projectId: String)

    @Query("DELETE FROM appointment_contact WHERE appointment_id = :appointmentId")
    suspend fun deleteAppointmentContacts(appointmentId: String)

    @Query("DELETE FROM activity_plan_item WHERE appointmentId = :appointmentId")
    suspend fun deletePlanItems(appointmentId: String)

    @Query("UPDATE activity_result SET is_latest = 0 WHERE result_id = :resultId")
    suspend fun markResultNotLatest(resultId: String)

    @Query("UPDATE activity_table SET project_id = :projectId WHERE appointment_id = :activityId")
    suspend fun updateActivityProjectId(activityId: String, projectId: String)

    @Query("UPDATE activity_table SET cust_id = :customerId WHERE appointment_id = :activityId")
    suspend fun updateActivityCustomerId(activityId: String, customerId: String)

    @Query("UPDATE activity_result SET appointment_id = :activityId, project_id = :projectId WHERE result_id = :resultId")
    suspend fun updateResultParents(resultId: String, activityId: String?, projectId: String?)

    @Transaction
    suspend fun replaceTemporaryCustomer(tempId: String, replacement: Customer) {
        require(tempId.startsWith("TEMP-")) { "Expected temporary customer ID" }
        upsert(LocalIdMapping(LocalIdMapping.ENTITY_CUSTOMER, tempId, replacement.custId))
        insertCustomerRaw(replacement)
        remapProjectCustomerIds(tempId, replacement.custId)
        remapContactCustomerIds(tempId, replacement.custId)
        remapActivityCustomerIds(tempId, replacement.custId)
        deleteCustomer(tempId)
    }

    @Transaction
    suspend fun replaceTemporaryContact(tempId: String, replacement: ContactPerson) {
        require(tempId.startsWith("TEMP-")) { "Expected temporary contact ID" }
        upsert(LocalIdMapping(LocalIdMapping.ENTITY_CONTACT, tempId, replacement.contactId))
        val inserted = insertContactRaw(replacement)
        if (inserted == -1L) updateContactRaw(replacement)
        remapProjectContactContactIds(tempId, replacement.contactId)
        remapAppointmentContactContactIds(tempId, replacement.contactId)
        deleteContact(tempId)
    }

    /**
     * Whichever transaction wins is safe: an existing child is remapped here, while a child saved
     * after this transaction resolves the durable mapping in [insertActivityResolvingProject].
     */
    @Transaction
    suspend fun replaceTemporaryProject(tempId: String, replacement: Project) {
        require(tempId.startsWith("TEMP-")) { "Expected temporary project ID" }
        upsert(
            LocalIdMapping(
                entityType = LocalIdMapping.ENTITY_PROJECT,
                tempId = tempId,
                realId = replacement.projectId
            )
        )
        val inserted = insertProjectRaw(replacement)
        if (inserted == -1L) updateProjectRaw(replacement)
        remapActivityProjectIds(tempId, replacement.projectId)
        remapResultProjectIds(tempId, replacement.projectId)
        remapProjectContactIds(tempId, replacement.projectId)
        deleteProject(tempId)
    }

    @Transaction
    suspend fun replaceTemporaryActivity(tempId: String, replacement: SalesActivity) {
        require(tempId.startsWith("TEMP-")) { "Expected temporary appointment ID" }
        upsert(LocalIdMapping(LocalIdMapping.ENTITY_ACTIVITY, tempId, replacement.activityId))
        val inserted = insertActivityRaw(replacement)
        if (inserted == -1L) updateActivityRaw(replacement)
        remapAppointmentContactAppointmentIds(tempId, replacement.activityId)
        remapPlanItemAppointmentIds(tempId, replacement.activityId)
        remapResultAppointmentIds(tempId, replacement.activityId)
        deleteActivity(tempId)
    }

    @Transaction
    suspend fun replaceTemporaryResult(tempId: String, replacement: ActivityResult) {
        require(tempId.startsWith("TEMP-")) { "Expected temporary result ID" }
        upsert(LocalIdMapping(LocalIdMapping.ENTITY_RESULT, tempId, replacement.resultId))
        val inserted = insertResultRaw(replacement)
        if (inserted == -1L) updateResultRaw(replacement)
        remapResultPhotoIds(tempId, replacement.resultId)
        remapAttachmentResultIds(tempId, replacement.resultId)
        remapResultGroupIds(tempId, replacement.resultId)
        deleteResult(tempId)
    }

    private suspend fun resolveExisting(entityType: String, id: String?): String? {
        if (id == null || !id.startsWith("TEMP-")) return id
        findRealId(entityType, id)?.let { return it }
        val exists = when (entityType) {
            LocalIdMapping.ENTITY_CUSTOMER -> customerExists(id)
            LocalIdMapping.ENTITY_CONTACT -> contactExists(id)
            LocalIdMapping.ENTITY_PROJECT -> projectExists(id)
            LocalIdMapping.ENTITY_ACTIVITY -> activityExists(id)
            LocalIdMapping.ENTITY_RESULT -> resultExists(id)
            else -> false
        }
        check(exists) { "ไม่พบข้อมูลที่เลือก กรุณาเลือกรายการอีกครั้ง" }
        return id
    }

    @Transaction
    suspend fun insertProjectResolvingCustomer(project: Project): Project {
        val resolved = project.copy(
            custId = resolveExisting(LocalIdMapping.ENTITY_CUSTOMER, project.custId)
        )
        val inserted = insertProjectRaw(resolved)
        if (inserted == -1L) updateProjectRaw(resolved)
        return resolved
    }

    @Transaction
    suspend fun insertContactResolvingCustomer(contact: ContactPerson): ContactPerson {
        val resolved = contact.copy(
            custId = requireNotNull(resolveExisting(LocalIdMapping.ENTITY_CUSTOMER, contact.custId))
        )
        val inserted = insertContactRaw(resolved)
        if (inserted == -1L) updateContactRaw(resolved)
        return resolved
    }

    /** Resolves a stale TEMP project selection and writes the appointment in one transaction. */
    @Transaction
    suspend fun insertActivityResolvingProject(activity: SalesActivity): SalesActivity {
        val resolved = activity.copy(
            projectId = resolveExisting(LocalIdMapping.ENTITY_PROJECT, activity.projectId),
            customerId = resolveExisting(LocalIdMapping.ENTITY_CUSTOMER, activity.customerId)
        )
        val inserted = insertActivityRaw(resolved)
        if (inserted == -1L) updateActivityRaw(resolved)
        return resolved
    }

    /** Defensive repair for a pending row created by an older app version. */
    @Transaction
    suspend fun resolvePendingActivityProject(activity: SalesActivity): SalesActivity? {
        var resolved = activity
        activity.projectId?.takeIf { it.startsWith("TEMP-") }?.let { tempId ->
            val realId = findRealId(LocalIdMapping.ENTITY_PROJECT, tempId) ?: return null
            updateActivityProjectId(activity.activityId, realId)
            resolved = resolved.copy(projectId = realId)
        }
        activity.customerId?.takeIf { it.startsWith("TEMP-") }?.let { tempId ->
            val realId = findRealId(LocalIdMapping.ENTITY_CUSTOMER, tempId) ?: return null
            updateActivityCustomerId(activity.activityId, realId)
            resolved = resolved.copy(customerId = realId)
        }
        return resolved
    }

    @Transaction
    suspend fun resolvePendingProjectCustomer(project: Project): Project? {
        val tempId = project.custId?.takeIf { it.startsWith("TEMP-") } ?: return project
        val realId = findRealId(LocalIdMapping.ENTITY_CUSTOMER, tempId) ?: return null
        remapProjectCustomerIds(tempId, realId)
        return project.copy(custId = realId)
    }

    @Transaction
    suspend fun resolvePendingContactCustomer(contact: ContactPerson): ContactPerson? {
        val tempId = contact.custId.takeIf { it.startsWith("TEMP-") } ?: return contact
        val realId = findRealId(LocalIdMapping.ENTITY_CUSTOMER, tempId) ?: return null
        remapContactCustomerIds(tempId, realId)
        return contact.copy(custId = realId)
    }

    @Transaction
    suspend fun resolvePendingResultParents(result: ActivityResult): ActivityResult? {
        var resolved = result
        result.activityId?.takeIf { it.startsWith("TEMP-") }?.let { tempId ->
            val realId = findRealId(LocalIdMapping.ENTITY_ACTIVITY, tempId) ?: return null
            resolved = resolved.copy(activityId = realId)
        }
        result.projectId?.takeIf { it.startsWith("TEMP-") }?.let { tempId ->
            val realId = findRealId(LocalIdMapping.ENTITY_PROJECT, tempId) ?: return null
            resolved = resolved.copy(projectId = realId)
        }
        if (resolved != result) {
            updateResultParents(result.resultId, resolved.activityId, resolved.projectId)
        }
        return resolved
    }

    @Transaction
    suspend fun replaceProjectContactsResolvingIds(
        projectId: String,
        contactIds: List<String>
    ): Pair<String, List<ProjectContact>> {
        val resolvedProjectId = requireNotNull(resolveExisting(LocalIdMapping.ENTITY_PROJECT, projectId))
        val rows = contactIds.distinct().map { contactId ->
            ProjectContact(
                resolvedProjectId,
                requireNotNull(resolveExisting(LocalIdMapping.ENTITY_CONTACT, contactId))
            )
        }
        deleteProjectContacts(resolvedProjectId)
        if (rows.isNotEmpty()) insertProjectContactsRaw(rows)
        return resolvedProjectId to rows
    }

    @Transaction
    suspend fun replaceAppointmentContactsResolvingIds(
        appointmentId: String,
        contactIds: List<String>
    ): Pair<String, List<AppointmentContact>> {
        val resolvedAppointmentId = requireNotNull(resolveExisting(LocalIdMapping.ENTITY_ACTIVITY, appointmentId))
        val rows = contactIds.distinct().map { contactId ->
            AppointmentContact(
                resolvedAppointmentId,
                requireNotNull(resolveExisting(LocalIdMapping.ENTITY_CONTACT, contactId))
            )
        }
        deleteAppointmentContacts(resolvedAppointmentId)
        if (rows.isNotEmpty()) insertAppointmentContactsRaw(rows)
        return resolvedAppointmentId to rows
    }

    @Transaction
    suspend fun replacePlanItemsResolvingAppointment(
        appointmentId: String,
        items: List<ActivityPlanItem>
    ): Pair<String, List<ActivityPlanItem>> {
        val resolvedId = requireNotNull(resolveExisting(LocalIdMapping.ENTITY_ACTIVITY, appointmentId))
        val resolvedItems = items.map { it.copy(appointmentId = resolvedId) }
        deletePlanItems(resolvedId)
        if (resolvedItems.isNotEmpty()) insertPlanItemsRaw(resolvedItems)
        return resolvedId to resolvedItems
    }

    @Transaction
    suspend fun insertResultWithAttachmentsResolvingParents(
        previousResultId: String?,
        result: ActivityResult,
        remotePhotos: List<ActivityResultPhoto>,
        attachments: List<AttachmentOutbox>
    ): ActivityResult {
        val resolved = result.copy(
            activityId = resolveExisting(LocalIdMapping.ENTITY_ACTIVITY, result.activityId),
            projectId = resolveExisting(LocalIdMapping.ENTITY_PROJECT, result.projectId),
            resultGroupId = result.resultGroupId?.let { groupId ->
                if (groupId.startsWith("TEMP-")) {
                    findRealId(LocalIdMapping.ENTITY_RESULT, groupId) ?: groupId
                } else groupId
            }
        )
        previousResultId?.let { previousId ->
            val resolvedPrevious = if (previousId.startsWith("TEMP-")) {
                findRealId(LocalIdMapping.ENTITY_RESULT, previousId) ?: previousId
            } else previousId
            markResultNotLatest(resolvedPrevious)
        }
        val inserted = insertResultRaw(resolved)
        if (inserted == -1L) updateResultRaw(resolved)
        if (remotePhotos.isNotEmpty()) {
            insertResultPhotosRaw(remotePhotos.map { it.copy(resultId = resolved.resultId) })
        }
        if (attachments.isNotEmpty()) {
            insertAttachmentOutboxRaw(attachments.map { it.copy(resultId = resolved.resultId) })
        }
        return resolved
    }
}
