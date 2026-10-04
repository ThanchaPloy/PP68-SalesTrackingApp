package com.example.pp68_salestrackingapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.paging.PagingSource
import com.example.pp68_salestrackingapp.data.model.ContactPerson
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Query("""
        WITH contact_rows AS (
            SELECT cp.contactId, cp.custId,
                   COALESCE(cp.fullName, c.company_name) AS fullName,
                   cp.nickname, cp.position, cp.phoneNumber, cp.email,
                   cp.line, cp.isActive, cp.isDmConfirmed, cp.createdBy, cp.is_synced,
                   c.company_name AS companyName,
                   CASE
                       WHEN TRIM(COALESCE(cp.fullName, c.company_name, '')) LIKE 'นางสาว %' THEN SUBSTR(TRIM(COALESCE(cp.fullName, c.company_name, '')), LENGTH('นางสาว ') + 1)
                       WHEN TRIM(COALESCE(cp.fullName, c.company_name, '')) LIKE 'ห้างหุ้นส่วนจำกัด %' THEN SUBSTR(TRIM(COALESCE(cp.fullName, c.company_name, '')), LENGTH('ห้างหุ้นส่วนจำกัด ') + 1)
                       WHEN TRIM(COALESCE(cp.fullName, c.company_name, '')) LIKE 'ห้างหุ้นส่วน %' THEN SUBSTR(TRIM(COALESCE(cp.fullName, c.company_name, '')), LENGTH('ห้างหุ้นส่วน ') + 1)
                       WHEN TRIM(COALESCE(cp.fullName, c.company_name, '')) LIKE 'กิจการร่วมค้า %' THEN SUBSTR(TRIM(COALESCE(cp.fullName, c.company_name, '')), LENGTH('กิจการร่วมค้า ') + 1)
                       WHEN TRIM(COALESCE(cp.fullName, c.company_name, '')) LIKE 'บริษัท %' THEN SUBSTR(TRIM(COALESCE(cp.fullName, c.company_name, '')), LENGTH('บริษัท ') + 1)
                       WHEN TRIM(COALESCE(cp.fullName, c.company_name, '')) LIKE 'หจก. %' THEN SUBSTR(TRIM(COALESCE(cp.fullName, c.company_name, '')), LENGTH('หจก. ') + 1)
                       WHEN TRIM(COALESCE(cp.fullName, c.company_name, '')) LIKE 'บจก. %' THEN SUBSTR(TRIM(COALESCE(cp.fullName, c.company_name, '')), LENGTH('บจก. ') + 1)
                       WHEN TRIM(COALESCE(cp.fullName, c.company_name, '')) LIKE 'นาง %' THEN SUBSTR(TRIM(COALESCE(cp.fullName, c.company_name, '')), LENGTH('นาง ') + 1)
                       WHEN TRIM(COALESCE(cp.fullName, c.company_name, '')) LIKE 'นาย %' THEN SUBSTR(TRIM(COALESCE(cp.fullName, c.company_name, '')), LENGTH('นาย ') + 1)
                       WHEN TRIM(COALESCE(cp.fullName, c.company_name, '')) LIKE 'ด.ช. %' THEN SUBSTR(TRIM(COALESCE(cp.fullName, c.company_name, '')), LENGTH('ด.ช. ') + 1)
                       WHEN TRIM(COALESCE(cp.fullName, c.company_name, '')) LIKE 'ด.ญ. %' THEN SUBSTR(TRIM(COALESCE(cp.fullName, c.company_name, '')), LENGTH('ด.ญ. ') + 1)
                       WHEN TRIM(COALESCE(cp.fullName, c.company_name, '')) LIKE 'คุณ %' THEN SUBSTR(TRIM(COALESCE(cp.fullName, c.company_name, '')), LENGTH('คุณ ') + 1)
                       ELSE TRIM(COALESCE(cp.fullName, c.company_name, ''))
                   END AS sortName
            FROM contact_person cp
            LEFT JOIN customer c ON cp.custId = c.cust_id
        )
        SELECT contactId, custId, fullName, nickname, position, phoneNumber, email,
               line, isActive, isDmConfirmed, createdBy, is_synced
        FROM contact_rows
        WHERE (:searchQuery = ''
               OR fullName LIKE '%' || :searchQuery || '%'
               OR nickname LIKE '%' || :searchQuery || '%'
               OR companyName LIKE '%' || :searchQuery || '%')
          AND (:initial IS NULL OR SUBSTR(sortName, 1, 1) = :initial COLLATE NOCASE)
        ORDER BY sortName COLLATE NOCASE ASC, contactId ASC
    """)
    fun getContactsPaging(
        searchQuery: String,
        initial: String?
    ): PagingSource<Int, ContactPerson>

    @Query("""
        SELECT cp.contactId, cp.custId,
               COALESCE(cp.fullName, c.company_name) AS fullName,
               cp.nickname, cp.position, cp.phoneNumber, cp.email,
               cp.line, cp.isActive, cp.isDmConfirmed, cp.createdBy, cp.is_synced
        FROM contact_person cp
        LEFT JOIN customer c ON cp.custId = c.cust_id
        ORDER BY fullName ASC, cp.contactId ASC
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
        ORDER BY fullName ASC, cp.contactId ASC
    """)
    fun searchContactsWithCompany(query: String): Flow<List<ContactPerson>>

    @Query("SELECT * FROM contact_person WHERE contactId = :contactId LIMIT 1")
    suspend fun getContactById(contactId: String): ContactPerson?

    @Query("SELECT * FROM contact_person WHERE custId = :customerId")
    fun getContactsByCustomer(customerId: String): Flow<List<ContactPerson>>

    @Query("DELETE FROM contact_person WHERE custId = :customerId")
    suspend fun deleteContactsByCustomerId(customerId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllRaw(contacts: List<ContactPerson>): List<Long>

    @Update
    suspend fun updateContacts(contacts: List<ContactPerson>)

    @Query("SELECT contactId FROM contact_person WHERE is_synced = 0")
    suspend fun getUnsyncedContactIds(): List<String>

    @Transaction
    suspend fun insertAll(contacts: List<ContactPerson>) {
        val insertResults = insertAllRaw(contacts)
        val updateList = mutableListOf<ContactPerson>()
        var unsyncedIds: Set<String>? = null
        for (i in insertResults.indices) {
            if (insertResults[i] == -1L) {
                if (unsyncedIds == null) unsyncedIds = getUnsyncedContactIds().toSet()
                if (!unsyncedIds.contains(contacts[i].contactId)) {
                    updateList.add(contacts[i])
                }
            }
        }
        if (updateList.isNotEmpty()) {
            updateContacts(updateList)
        }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: ContactPerson)

    @Query("DELETE FROM contact_person WHERE contactId = :contactId")
    suspend fun deleteContactById(contactId: String)


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
