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

    // ✅ นำเงื่อนไข companyStatus = 'customer' ออกเพื่อให้แสดงข้อมูลทุกสถานะ (รวมถึง New Lead)
    @Query("SELECT * FROM customer ORDER BY companyName ASC")
    fun getAllCustomers(): Flow<List<Customer>>

    // ✅ นำเงื่อนไข companyStatus = 'customer' ออกเพื่อให้ค้นหาเจอทุกสถานะ
    @Query("SELECT * FROM customer WHERE companyName LIKE '%' || :searchQuery || '%'")
    fun searchCustomers(searchQuery: String): Flow<List<Customer>>

    @Query("SELECT * FROM customer WHERE custId = :customerId")
    suspend fun getCustomerById(customerId: String): Customer?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomers(customers: List<Customer>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: Customer)

    @Query("DELETE FROM customer WHERE custId = :customerId")
    suspend fun deleteCustomerById(customerId: String)

    @Query("DELETE FROM customer")
    suspend fun deleteAllCustomers()

    @Query("DELETE FROM customer")
    suspend fun deleteAll()

    @Transaction
    suspend fun clearAndInsert(customers: List<Customer>) {
        // ❌ ไม่ใช้ deleteAll() เพื่อป้องกันข้อมูลที่บันทึกใหม่แต่ยังไม่ได้ผูกโปรเจกต์หายไปตอนรีเฟรช
        // ใช้ insertCustomers ซึ่งมี OnConflictStrategy.REPLACE เพื่ออัปเดตข้อมูลที่มีอยู่แล้ว
        if (customers.isNotEmpty()) {
            insertCustomers(customers)
        }
    }
}
