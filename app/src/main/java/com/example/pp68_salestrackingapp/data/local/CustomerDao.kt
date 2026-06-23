package com.example.pp68_salestrackingapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomers(customers: List<Customer>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: Customer)

    @Query("DELETE FROM customer WHERE cust_id = :customerId")
    suspend fun deleteCustomerById(customerId: String)

    @Query("DELETE FROM customer")
    suspend fun deleteAllCustomers()

    @Query("DELETE FROM customer")
    suspend fun deleteAll()

    @Query("SELECT cust_id FROM customer")
    suspend fun getAllCustomerIds(): List<String>

    @Query("SELECT cust_id FROM customer WHERE user_id = :userId")
    suspend fun getCustomerIdsByUserId(userId: String): List<String>

    @Transaction
    suspend fun clearAndInsert(customers: List<Customer>) {
        if (customers.isNotEmpty()) {
            insertCustomers(customers)
        }
    }

    @Query("SELECT * FROM customer WHERE is_synced = 0")
    suspend fun getUnsyncedCustomers(): List<Customer>

    @Query("UPDATE customer SET is_synced = :isSynced WHERE cust_id = :customerId")
    suspend fun updateSyncStatus(customerId: String, isSynced: Boolean)
}
