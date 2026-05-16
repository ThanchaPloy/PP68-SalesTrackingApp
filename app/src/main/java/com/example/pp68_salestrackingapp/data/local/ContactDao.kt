package com.example.pp68_salestrackingapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.pp68_salestrackingapp.data.model.ContactPerson
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    // 1. ดึงข้อมูลทั้งหมด เรียงตามชื่อ
    @Query("SELECT * FROM contact_person ORDER BY fullName ASC")
    fun getAllContacts(): Flow<List<ContactPerson>>

    // 2. ฟังก์ชันที่ Repository เรียกใช้ (ชื่อตารางต้องตรงกับ Entity)
    @Query("SELECT * FROM contact_person")
    fun getAllContactPersons(): Flow<List<ContactPerson>>

    // 3. ค้นหาจากชื่อ และชื่อเล่น
    @Query("SELECT * FROM contact_person WHERE fullName LIKE '%' || :searchQuery || '%' OR nickname LIKE '%' || :searchQuery || '%'")
    fun searchContacts(searchQuery: String): Flow<List<ContactPerson>>

    @Query("SELECT * FROM contact_person WHERE contactId = :contactId LIMIT 1")
    suspend fun getContactById(contactId: String): ContactPerson?

    // 4. ดึงตาม ID ลูกค้า (ใช้ custId ให้ตรงกับใน Model)
    @Query("SELECT * FROM contact_person WHERE custId = :customerId")
    fun getContactsByCustomer(customerId: String): Flow<List<ContactPerson>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContacts(contacts: List<ContactPerson>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: ContactPerson)

    @Query("DELETE FROM contact_person WHERE contactId = :contactId")
    suspend fun deleteContactById(contactId: String)

    @Query("DELETE FROM contact_person")
    suspend fun deleteAllContacts()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(contact: List<ContactPerson>)

    @Query("SELECT * FROM contact_person WHERE custId = :custId")
    suspend fun getContactsByCustomerId(custId: String): List<ContactPerson>


    @Query("DELETE FROM contact_person")
    suspend fun deleteAll()

    // ContactDao.kt
    @Query("SELECT * FROM contact_person WHERE createdBy = :userId ORDER BY fullName ASC")
    fun getContactsByUser(userId: String): Flow<List<ContactPerson>>

    @Query("""
        SELECT cp.* FROM contact_person cp
        INNER JOIN customer c ON cp.custId = c.custId
        WHERE cp.createdBy = :userId AND (cp.fullName LIKE :query OR cp.nickname LIKE :query OR c.companyName LIKE :query)
    """)
    fun searchContactsByUser(query: String, userId: String): Flow<List<ContactPerson>>


    @Transaction
    suspend fun clearAndInsert(contacts: List<ContactPerson>) {
        deleteAll()
        insertAll(contacts)
    }

}