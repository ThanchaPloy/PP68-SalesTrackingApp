package com.example.pp68_salestrackingapp.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pp68_salestrackingapp.data.model.ActivityPlanItem
import com.example.pp68_salestrackingapp.data.model.ActivityResult
import com.example.pp68_salestrackingapp.data.model.ActivityResultPhoto
import com.example.pp68_salestrackingapp.data.model.AttachmentOutbox
import com.example.pp68_salestrackingapp.data.model.AppointmentContact
import com.example.pp68_salestrackingapp.data.model.Customer
import com.example.pp68_salestrackingapp.data.model.Project
import com.example.pp68_salestrackingapp.data.model.SalesActivity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IdRemapTransactionTest {
    private lateinit var database: AppDatabase

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun customerReplacementMovesProjectAndAppointmentReferences() = runBlocking<Unit> {
        database.customerDao().insertCustomer(Customer("TEMP-C", "Customer", isSynced = false))
        database.projectDao().insertProject(Project("P1", custId = "TEMP-C", projectName = "Project"))
        database.activityDao().insertActivity(activity("A1", customerId = "TEMP-C"))

        database.customerDao().replaceTemporaryCustomer(
            "TEMP-C",
            Customer("C1", "Customer", isSynced = true)
        )

        assertNull(database.customerDao().getCustomerById("TEMP-C"))
        assertEquals("C1", database.projectDao().getProjectById("P1")?.custId)
        assertEquals("C1", database.activityDao().getActivityById("A1")?.customerId)
    }

    @Test
    fun projectReplacementMovesAppointmentReference() = runBlocking<Unit> {
        database.projectDao().insertProject(Project("TEMP-P", projectName = "Project", isSynced = false))
        database.activityDao().insertActivity(activity("A1", projectId = "TEMP-P"))

        database.projectDao().replaceTemporaryProject(
            "TEMP-P",
            Project("P1", projectName = "Project", isSynced = false)
        )

        assertNull(database.projectDao().getProjectById("TEMP-P"))
        assertEquals("P1", database.activityDao().getActivityById("A1")?.projectId)
    }

    @Test
    fun appointmentReplacementMovesChecklistAndParticipants() = runBlocking<Unit> {
        database.activityDao().insertActivity(activity("TEMP-A"))
        database.activityPlanItemDao().insertPlanItems(
            listOf(ActivityPlanItem(appointmentId = "TEMP-A", masterId = 1, actName = "Check"))
        )
        database.appointmentContactDao().insertAppointmentContacts(
            listOf(AppointmentContact("TEMP-A", "CONTACT-1"))
        )

        database.activityDao().replaceTemporaryActivity("TEMP-A", activity("A1"))

        assertNull(database.activityDao().getActivityById("TEMP-A"))
        assertEquals(1, database.activityPlanItemDao().getPlanItemsByAppointmentId("A1").size)
        assertEquals("A1", database.appointmentContactDao().getAll().single().appointmentId)
    }

    @Test
    fun resultReplacementMovesPhotosBeforeDeletingTemporaryParent() = runBlocking<Unit> {
        database.activityResultDao().insertResult(ActivityResult("TEMP-R", isSynced = false))
        database.activityResultPhotoDao().insertPhotos(
            listOf(ActivityResultPhoto("TEMP-R", 0, "file:///pending.jpg"))
        )
        database.attachmentOutboxDao().insertAll(
            listOf(
                AttachmentOutbox(
                    operationId = "op-1",
                    ownerId = "U1",
                    resultId = "TEMP-R",
                    photoOrder = 1,
                    localPath = "/pending/photo.jpg",
                    mimeType = "image/jpeg",
                    sha256 = "abc",
                    sizeBytes = 3,
                    createdAt = "2026-10-05T00:00:00Z",
                    updatedAt = "2026-10-05T00:00:00Z"
                )
            )
        )

        database.activityResultDao().replaceTemporaryResult(
            "TEMP-R",
            ActivityResult("R1", isSynced = true)
        )

        assertNull(database.activityResultDao().getResultById("TEMP-R"))
        assertEquals("R1", database.activityResultPhotoDao().getPhotosByResultId("R1").single().resultId)
        assertEquals("R1", database.attachmentOutboxDao().getByResultId("R1").single().resultId)
    }

    private fun activity(
        id: String,
        customerId: String? = null,
        projectId: String? = null
    ) = SalesActivity(
        activityId = id,
        userId = "U1",
        customerId = customerId,
        projectId = projectId,
        activityType = "visit",
        activityDate = "2026-10-04",
        status = "planned"
    )
}
