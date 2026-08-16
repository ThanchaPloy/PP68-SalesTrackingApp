package com.example.pp68_salestrackingapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.pp68_salestrackingapp.data.model.Customer
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerDao {

    @Query("SELECT * FROM customer")
    fun getAllCustomersFlow(): Flow<List<Customer>>

    @Query("SELECT * FROM customer ORDER BY company_name ASC")
    fun getAllCustomers(): Flow<List<Customer>>

    @Query("SELECT * FROM customer WHERE company_name LIKE '%' || :searchQuery || '%' ORDER BY company_name ASC")
    fun searchCustomers(searchQuery: String): Flow<List<Customer>>

    @Query("SELECT * FROM customer WHERE cust_id = :customerId")
    suspend fun getCustomerById(customerId: String): Customer?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCustomersRaw(customers: List<Customer>): List<Long>

    @Update
    suspend fun updateCustomers(customers: List<Customer>)

    @Query("SELECT cust_id FROM customer WHERE is_synced = 0")
    suspend fun getUnsyncedCustomerIds(): List<String>

    @Transaction
    suspend fun insertCustomers(customers: List<Customer>) {
        val insertResults = insertCustomersRaw(customers)
        val updateList = mutableListOf<Customer>()
        var unsyncedIds: Set<String>? = null
        for (i in insertResults.indices) {
            if (insertResults[i] == -1L) {
                if (unsyncedIds == null) unsyncedIds = getUnsyncedCustomerIds().toSet()
                if (!unsyncedIds.contains(customers[i].custId)) {
                    updateList.add(customers[i])
                }
            }
        }
        if (updateList.isNotEmpty()) {
            updateCustomers(updateList)
        }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: Customer)

    @Query("DELETE FROM customer WHERE cust_id = :customerId")
    suspend fun deleteCustomerById(customerId: String)

    @Query("DELETE FROM customer")
    suspend fun deleteAllCustomers()

    @Query("DELETE FROM customer")
    suspend fun deleteAll()

    @Query("DELETE FROM customer WHERE is_synced = 1")
    suspend fun deleteAllSynced()

    @Query("SELECT cust_id FROM customer")
    suspend fun getAllCustomerIds(): List<String>

    @Query("SELECT cust_id FROM customer WHERE user_id = :userId")
    suspend fun getCustomerIdsByUserId(userId: String): List<String>

    @Transaction
    suspend fun clearAndInsert(customers: List<Customer>) {
        val incomingIds = customers.map { it.custId }
        if (incomingIds.isNotEmpty()) {
            deleteSyncedCustomersNotIn(incomingIds)
        } else {
            deleteAllSynced()
        }
        if (customers.isNotEmpty()) {
            insertCustomers(customers)
        }
    }

    @Query("DELETE FROM customer WHERE is_synced = 1 AND cust_id NOT IN (:incomingIds)")
    suspend fun deleteSyncedCustomersNotIn(incomingIds: List<String>)

    @Query("SELECT * FROM customer WHERE is_synced = 0")
    suspend fun getUnsyncedCustomers(): List<Customer>

    @Query("UPDATE customer SET is_synced = :isSynced WHERE cust_id = :customerId")
    suspend fun updateSyncStatus(customerId: String, isSynced: Boolean)

    @Query("UPDATE customer SET is_lead = :isLead WHERE cust_id = :customerId")
    suspend fun updateLeadStatus(customerId: String, isLead: Boolean)
}
