package com.example.pp68_salestrackingapp.data.local

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pp68_salestrackingapp.data.model.ContactPerson
import com.example.pp68_salestrackingapp.data.model.Customer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CustomerContactPagingStressTest {
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
    fun fiftyThousandRowsReturnOnlyInitialPageForEachList() = runBlocking<Unit> {
        val customers = List(ROW_COUNT) { index ->
            Customer(
                custId = "C%05d".format(index),
                companyName = if (index % 2 == 0) "บริษัท Company %05d".format(index)
                    else "Company %05d".format(index),
                bizPostingGroup = if (index % 2 == 0) "R" else "W",
                custType = "Dealer",
                createdBy = OWNER,
                isLead = index % 3 == 0
            )
        }
        val customerInsertStart = SystemClock.elapsedRealtime()
        database.customerDao().insertCustomers(customers)
        val customerInsertMs = SystemClock.elapsedRealtime() - customerInsertStart

        val customerLoadStart = SystemClock.elapsedRealtime()
        val customerPage = database.customerDao().getCustomersPaging(OWNER, "", null, null, 0, null)
            .load(PagingSource.LoadParams.Refresh(null, INITIAL_LOAD_SIZE, false))
        val customerLoadMs = SystemClock.elapsedRealtime() - customerLoadStart
        check(customerPage is PagingSource.LoadResult.Page)
        assertEquals(INITIAL_LOAD_SIZE, customerPage.data.size)

        val contacts = List(ROW_COUNT) { index ->
            ContactPerson(
                contactId = "P%05d".format(index),
                custId = "C%05d".format(index),
                fullName = if (index % 2 == 0) "คุณ Person %05d".format(index)
                    else "Person %05d".format(index)
            )
        }
        val contactInsertStart = SystemClock.elapsedRealtime()
        database.contactDao().insertAll(contacts)
        val contactInsertMs = SystemClock.elapsedRealtime() - contactInsertStart

        val contactLoadStart = SystemClock.elapsedRealtime()
        val contactPage = database.contactDao().getContactsPaging("", null)
            .load(PagingSource.LoadParams.Refresh(null, INITIAL_LOAD_SIZE, false))
        val contactLoadMs = SystemClock.elapsedRealtime() - contactLoadStart
        check(contactPage is PagingSource.LoadResult.Page)
        assertEquals(INITIAL_LOAD_SIZE, contactPage.data.size)

        Log.i(
            "PagingStress",
            "rows=$ROW_COUNT customerInsertMs=$customerInsertMs customerFirstPageMs=$customerLoadMs " +
                "contactInsertMs=$contactInsertMs contactFirstPageMs=$contactLoadMs"
        )
    }

    private companion object {
        const val OWNER = "U1"
        const val ROW_COUNT = 50_000
        const val INITIAL_LOAD_SIZE = 60
    }
}
