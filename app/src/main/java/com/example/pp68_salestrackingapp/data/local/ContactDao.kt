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
    @Query("""
        SELECT cp.contactId, cp.custId,
               COALESCE(cp.fullName, c.company_name) AS fullName,
               cp.nickname, cp.position, cp.phoneNumber, cp.email,
               cp.line, cp.isActive, cp.isDmConfirmed, cp.createdBy, cp.is_synced
        FROM contact_person cp
        LEFT JOIN customer c ON cp.custId = c.cust_id
        ORDER BY fullName ASC
    """)
    fun getAllContacts(): Flow<List<ContactPerson>>

    @Query("""
        SELECT cp.contactId, cp.custId,
               COALESCE(cp.fullName, c.company_name) AS fullName,
               cp.nickname, cp.position, cp.phoneNumber, cp.email,
               cp.line, cp.isActive, cp.isDmConfirmed, cp.createdBy, cp.is_synced
        FROM contact_person cp
        LEFT JOIN customer c ON cp.custId = c.cust_id
        WHERE COALESCE(cp.fullName, c.company_name) LIKE :query
           OR cp.nickname LIKE :query
           OR c.company_name LIKE :query
        ORDER BY fullName ASC
    """)
    fun searchContactsWithCompany(query: String): Flow<List<ContactPerson>>

    @Query("SELECT * FROM contact_person WHERE contactId = :contactId LIMIT 1")
    suspend fun getContactById(contactId: String): ContactPerson?

    @Query("SELECT * FROM contact_person WHERE custId = :customerId")
    fun getContactsByCustomer(customerId: String): Flow<List<ContactPerson>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(contacts: List<ContactPerson>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: ContactPerson)

    @Query("DELETE FROM contact_person WHERE contactId = :contactId")
    suspend fun deleteContactById(contactId: String)

    @Query("DELETE FROM contact_person")
    suspend fun deleteAll()

    @Query("DELETE FROM contact_person WHERE is_synced = 1")
    suspend fun deleteAllSynced()

    @Query("SELECT * FROM contact_person WHERE custId = :custId")
    suspend fun getContactsByCustomerId(custId: String): List<ContactPerson>

    @Transaction
    suspend fun clearAndInsert(contacts: List<ContactPerson>) {
        deleteAllSynced()   // คง row is_synced=0 (ออฟไลน์) ไว้
        insertAll(contacts)
    }

    @Query("SELECT * FROM contact_person WHERE is_synced = 0")
    suspend fun getUnsyncedContacts(): List<ContactPerson>

    @Query("UPDATE contact_person SET is_synced = :isSynced WHERE contactId = :contactId")
    suspend fun updateSyncStatus(contactId: String, isSynced: Boolean)

    @Query("UPDATE contact_person SET custId = :newCustId WHERE custId = :oldCustId")
    suspend fun updateCustIdForContacts(oldCustId: String, newCustId: String)
}
