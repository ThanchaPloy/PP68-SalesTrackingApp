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

    @Query("SELECT * FROM customer WHERE companyStatus = 'customer' ORDER BY companyName ASC")
    fun getAllCustomers(): Flow<List<Customer>>

    @Query("SELECT * FROM customer WHERE companyName LIKE '%' || :searchQuery || '%' AND companyStatus = 'customer'")
    fun searchCustomers(searchQuery: String): Flow<List<Customer>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomers(customers: List<Customer>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: Customer)

    @Query("DELETE FROM customer")
    suspend fun deleteAllCustomers()

    @Query("DELETE FROM customer")
    suspend fun deleteAll()


    @Transaction
    suspend fun clearAndInsert(customers: List<Customer>) {
        deleteAll()          // ✅ ลบทั้งหมดก่อน
        if (customers.isNotEmpty()) {
            insertCustomers(customers)
        }
    }
}
