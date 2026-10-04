package com.example.pp68_salestrackingapp.data.local

import android.content.Context
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
class CustomerContactPagingQueryTest {
    private lateinit var database: AppDatabase

    @Before
    fun createDatabase() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        database.customerDao().insertCustomers(
            listOf(
                customer("C-B", "บริษัท Beta", "R", "Dealer", false),
                customer("C-A2", "บริษัท Alpha", "R", "Dealer", true),
                customer("C-A1", "Alpha", "W", "Developer", true)
            )
        )
        database.contactDao().insertAll(
            listOf(
                ContactPerson(contactId = "P-B", custId = "C-B", fullName = "คุณ Beta"),
                ContactPerson(contactId = "P-A2", custId = "C-A2", fullName = "นาย Alpha"),
                ContactPerson(contactId = "P-A1", custId = "C-A1", fullName = "Alpha"),
                ContactPerson(contactId = "P-FALLBACK", custId = "C-B", fullName = null)
            )
        )
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun customerQuerySortsWithoutThaiPrefixAndUsesIdAsStableTieBreaker() = runBlocking {
        val rows = loadCustomers()
        assertEquals(listOf("C-A1", "C-A2", "C-B"), rows.map(Customer::custId))
    }

    @Test
    fun customerFiltersAreAppliedInsidePagingQuery() = runBlocking {
        assertEquals(
            listOf("C-A2"),
            loadCustomers(bizGroup = "R", custType = "Dealer", tabIndex = 1, initial = "A")
                .map(Customer::custId)
        )
        assertEquals(listOf("C-B"), loadCustomers(tabIndex = 2).map(Customer::custId))
    }

    @Test
    fun contactQuerySupportsPrefixInitialSearchAndCustomerNameFallback() = runBlocking {
        assertEquals(
            listOf("P-A1", "P-A2"),
            loadContacts(initial = "A").map(ContactPerson::contactId)
        )
        assertEquals(
            listOf("P-B", "P-FALLBACK"),
            loadContacts(searchQuery = "Beta").map(ContactPerson::contactId)
        )
        assertEquals(
            "บริษัท Beta",
            loadContacts(searchQuery = "บริษัท").first { it.contactId == "P-FALLBACK" }.fullName
        )
    }

    private suspend fun loadCustomers(
        searchQuery: String = "",
        bizGroup: String? = null,
        custType: String? = null,
        tabIndex: Int = 0,
        initial: String? = null
    ): List<Customer> = page(
        database.customerDao().getCustomersPaging(
            searchQuery, bizGroup, custType, tabIndex, initial
        )
    )

    private suspend fun loadContacts(
        searchQuery: String = "",
        initial: String? = null
    ): List<ContactPerson> = page(
        database.contactDao().getContactsPaging(searchQuery, initial)
    )

    private suspend fun <T : Any> page(source: PagingSource<Int, T>): List<T> {
        val result = source.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 100,
                placeholdersEnabled = false
            )
        )
        check(result is PagingSource.LoadResult.Page)
        return result.data
    }

    private fun customer(
        id: String,
        name: String,
        bizGroup: String,
        custType: String,
        isLead: Boolean
    ) = Customer(
        custId = id,
        companyName = name,
        bizPostingGroup = bizGroup,
        custType = custType,
        isLead = isLead
    )
}
