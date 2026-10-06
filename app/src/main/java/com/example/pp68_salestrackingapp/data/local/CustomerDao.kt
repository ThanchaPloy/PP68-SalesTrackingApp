package com.example.pp68_salestrackingapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.paging.PagingSource
import com.example.pp68_salestrackingapp.data.model.Customer
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerDao {

    @Query("""
        SELECT * FROM customer
        WHERE (:searchQuery = '' OR company_name LIKE '%' || :searchQuery || '%')
          AND (:bizGroup IS NULL OR LOWER(branch_id) = LOWER(:bizGroup))
          AND (:custType IS NULL OR cust_type = :custType)
          AND (:tabIndex = 0 OR (:tabIndex = 1 AND is_lead = 1) OR (:tabIndex = 2 AND is_lead = 0))
          AND (:initial IS NULL OR SUBSTR(
              CASE
                  WHEN TRIM(company_name) LIKE 'นางสาว %' THEN SUBSTR(TRIM(company_name), LENGTH('นางสาว ') + 1)
                  WHEN TRIM(company_name) LIKE 'ห้างหุ้นส่วนจำกัด %' THEN SUBSTR(TRIM(company_name), LENGTH('ห้างหุ้นส่วนจำกัด ') + 1)
                  WHEN TRIM(company_name) LIKE 'ห้างหุ้นส่วน %' THEN SUBSTR(TRIM(company_name), LENGTH('ห้างหุ้นส่วน ') + 1)
                  WHEN TRIM(company_name) LIKE 'กิจการร่วมค้า %' THEN SUBSTR(TRIM(company_name), LENGTH('กิจการร่วมค้า ') + 1)
                  WHEN TRIM(company_name) LIKE 'บริษัท %' THEN SUBSTR(TRIM(company_name), LENGTH('บริษัท ') + 1)
                  WHEN TRIM(company_name) LIKE 'หจก. %' THEN SUBSTR(TRIM(company_name), LENGTH('หจก. ') + 1)
                  WHEN TRIM(company_name) LIKE 'บจก. %' THEN SUBSTR(TRIM(company_name), LENGTH('บจก. ') + 1)
                  WHEN TRIM(company_name) LIKE 'นาง %' THEN SUBSTR(TRIM(company_name), LENGTH('นาง ') + 1)
                  WHEN TRIM(company_name) LIKE 'นาย %' THEN SUBSTR(TRIM(company_name), LENGTH('นาย ') + 1)
                  WHEN TRIM(company_name) LIKE 'ด.ช. %' THEN SUBSTR(TRIM(company_name), LENGTH('ด.ช. ') + 1)
                  WHEN TRIM(company_name) LIKE 'ด.ญ. %' THEN SUBSTR(TRIM(company_name), LENGTH('ด.ญ. ') + 1)
                  WHEN TRIM(company_name) LIKE 'คุณ %' THEN SUBSTR(TRIM(company_name), LENGTH('คุณ ') + 1)
                  ELSE TRIM(company_name)
              END, 1, 1) = :initial COLLATE NOCASE)
        ORDER BY
          CASE
              WHEN TRIM(company_name) LIKE 'นางสาว %' THEN SUBSTR(TRIM(company_name), LENGTH('นางสาว ') + 1)
              WHEN TRIM(company_name) LIKE 'ห้างหุ้นส่วนจำกัด %' THEN SUBSTR(TRIM(company_name), LENGTH('ห้างหุ้นส่วนจำกัด ') + 1)
              WHEN TRIM(company_name) LIKE 'ห้างหุ้นส่วน %' THEN SUBSTR(TRIM(company_name), LENGTH('ห้างหุ้นส่วน ') + 1)
              WHEN TRIM(company_name) LIKE 'กิจการร่วมค้า %' THEN SUBSTR(TRIM(company_name), LENGTH('กิจการร่วมค้า ') + 1)
              WHEN TRIM(company_name) LIKE 'บริษัท %' THEN SUBSTR(TRIM(company_name), LENGTH('บริษัท ') + 1)
              WHEN TRIM(company_name) LIKE 'หจก. %' THEN SUBSTR(TRIM(company_name), LENGTH('หจก. ') + 1)
              WHEN TRIM(company_name) LIKE 'บจก. %' THEN SUBSTR(TRIM(company_name), LENGTH('บจก. ') + 1)
              WHEN TRIM(company_name) LIKE 'นาง %' THEN SUBSTR(TRIM(company_name), LENGTH('นาง ') + 1)
              WHEN TRIM(company_name) LIKE 'นาย %' THEN SUBSTR(TRIM(company_name), LENGTH('นาย ') + 1)
              WHEN TRIM(company_name) LIKE 'ด.ช. %' THEN SUBSTR(TRIM(company_name), LENGTH('ด.ช. ') + 1)
              WHEN TRIM(company_name) LIKE 'ด.ญ. %' THEN SUBSTR(TRIM(company_name), LENGTH('ด.ญ. ') + 1)
              WHEN TRIM(company_name) LIKE 'คุณ %' THEN SUBSTR(TRIM(company_name), LENGTH('คุณ ') + 1)
              ELSE TRIM(company_name)
          END COLLATE NOCASE ASC,
          cust_id ASC
    """)
    fun getCustomersPaging(
        searchQuery: String,
        bizGroup: String?,
        custType: String?,
        tabIndex: Int,
        initial: String?
    ): PagingSource<Int, Customer>

    @Query("SELECT * FROM customer")
    fun getAllCustomersFlow(): Flow<List<Customer>>

    @Query("SELECT * FROM customer ORDER BY company_name ASC, cust_id ASC")
    fun getAllCustomers(): Flow<List<Customer>>

    @Query("SELECT * FROM customer WHERE is_lead = 1 ORDER BY company_name ASC, cust_id ASC")
    fun getAllLeads(): Flow<List<Customer>>

    @Query("SELECT * FROM customer WHERE company_name LIKE '%' || :searchQuery || '%' ORDER BY company_name ASC, cust_id ASC")
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



    @Query("DELETE FROM customer WHERE is_synced = 1")
    suspend fun deleteAllSynced()

    @Query("DELETE FROM customer WHERE is_synced = 1 AND is_lead = 0")
    suspend fun deleteSyncedErpCustomers()

    @Query("DELETE FROM customer WHERE is_synced = 1 AND is_lead = 1")
    suspend fun deleteAllSyncedLeads()

    @Query("DELETE FROM customer WHERE is_synced = 1 AND is_lead = 1 AND cust_id NOT IN (:incomingIds)")
    suspend fun deleteSyncedLeadsNotIn(incomingIds: List<String>)

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

    /** Replaces the complete visible lead snapshot and purges previously cached ERP rows. */
    @Transaction
    suspend fun replaceLeadSnapshot(leads: List<Customer>) {
        val normalized = leads.map { it.copy(isLead = true, isSynced = true) }
        val incomingIds = normalized.map { it.custId }
        if (incomingIds.isEmpty()) deleteAllSyncedLeads()
        else deleteSyncedLeadsNotIn(incomingIds)
        if (normalized.isNotEmpty()) insertCustomers(normalized)
        deleteSyncedErpCustomers()
    }

    @Query("DELETE FROM customer WHERE is_synced = 1 AND cust_id NOT IN (:incomingIds)")
    suspend fun deleteSyncedCustomersNotIn(incomingIds: List<String>)

    @Query("SELECT * FROM customer WHERE is_synced = 0")
    suspend fun getUnsyncedCustomers(): List<Customer>

    @Query("UPDATE customer SET is_synced = :isSynced WHERE cust_id = :customerId")
    suspend fun updateSyncStatus(customerId: String, isSynced: Boolean)

    @Query("UPDATE customer SET is_lead = :isLead WHERE cust_id = :customerId")
    suspend fun updateLeadStatus(customerId: String, isLead: Boolean)

    @Query("UPDATE project SET custId = :realId WHERE custId = :tempId")
    suspend fun remapProjectCustomerIds(tempId: String, realId: String)

    @Query("UPDATE contact_person SET custId = :realId WHERE custId = :tempId")
    suspend fun remapContactCustomerIds(tempId: String, realId: String)

    @Query("UPDATE activity_table SET cust_id = :realId WHERE cust_id = :tempId")
    suspend fun remapActivityCustomerIds(tempId: String, realId: String)

    /** Replaces a server-generated customer ID and all local references atomically. */
    @Transaction
    suspend fun replaceTemporaryCustomer(tempId: String, replacement: Customer) {
        insertCustomer(replacement)
        remapProjectCustomerIds(tempId, replacement.custId)
        remapContactCustomerIds(tempId, replacement.custId)
        remapActivityCustomerIds(tempId, replacement.custId)
        deleteCustomerById(tempId)
    }
}
