package com.example.pp68_salestrackingapp.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.paging.PagingSource
import com.example.pp68_salestrackingapp.data.model.Customer
import kotlinx.coroutines.flow.Flow

/** แถวแคบ ๆ สำหรับแปลงรหัสเป็นชื่อ ไม่ใช่ entity เต็ม */
data class CustomerNameRow(
    @ColumnInfo(name = "cust_id") val custId: String,
    @ColumnInfo(name = "company_name") val companyName: String?
)

@Dao
interface CustomerDao {

    @Query("""
        SELECT * FROM customer
        WHERE user_id = :ownerId
          AND (:searchQuery = '' OR company_name LIKE '%' || :searchQuery || '%')
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
    // ดึงข้อมูล ลูกค้า Paging
    fun getCustomersPaging(
        ownerId: String,
        searchQuery: String,
        bizGroup: String?,
        custType: String?,
        tabIndex: Int,
        initial: String?
    ): PagingSource<Int, Customer>

    /**
     * ใช้แปลงรหัสลูกค้าเป็นชื่อบริษัทเท่านั้น ไม่ใช่รายการที่เอาไปโชว์เป็นลิสต์
     *
     * จงใจไม่กรองเจ้าของ เพราะการ "แปลรหัสเป็นชื่อ" ไม่ใช่ขอบเขตการมองเห็น ถ้านัดหมายอ้างถึง
     * ลูกค้าที่ผู้ใช้ไม่ได้เป็นเจ้าของ การ์ดควรขึ้นชื่อได้ ไม่ใช่ว่างเปล่า
     * ห้ามเอาไปใช้เติม dropdown หรือหน้ารายการ — พวกนั้นต้องใช้ตัวที่มี ownerId
     */
    @Query("SELECT cust_id, company_name FROM customer")
    suspend fun getAllForNameLookup(): List<CustomerNameRow>

    @Query("SELECT * FROM customer WHERE user_id = :ownerId ORDER BY company_name ASC, cust_id ASC")
    // ดึงข้อมูล ทั้งหมด ลูกค้า
    fun getAllCustomers(ownerId: String): Flow<List<Customer>>

    /**
     * ลูกค้า lead ของผู้ใช้คนนี้เท่านั้น — คอลัมน์เจ้าของชื่อ user_id ไม่ใช่ create_by
     *
     * Customer.createdBy ผูกกับ @ColumnInfo("user_id") และ @SerializedName("salesperson_code")
     * สามชื่อไม่ตรงกันสักคู่ ห้ามเดาชื่อคอลัมน์จากชื่อ property
     *
     * ฝั่ง server กรองให้แล้ว (GET /lead_customer, findChanges, findSnapshot) ตัวนี้เป็นด่านที่สอง
     * สำหรับตอนออฟไลน์ที่ refresh ล้ม แล้วของเพื่อนร่วมทีมที่ดาวน์โหลดมาก่อนหน้ายังค้างใน Room
     * (บั๊กรูปเดียวกับตาราง project ที่เคยเห็นของทั้งสาขาตอนออฟไลน์)
     */
    @Query("SELECT * FROM customer WHERE is_lead = 1 AND user_id = :ownerId ORDER BY company_name ASC, cust_id ASC")
    fun getAllLeads(ownerId: String): Flow<List<Customer>>

    @Query("SELECT * FROM customer WHERE company_name LIKE '%' || :searchQuery || '%' ORDER BY company_name ASC, cust_id ASC")
    // ค้นหา ลูกค้า
    fun searchCustomers(searchQuery: String): Flow<List<Customer>>

    @Query("SELECT * FROM customer WHERE cust_id = :customerId")
    // ดึงข้อมูล ลูกค้า ตาม รหัส
    suspend fun getCustomerById(customerId: String): Customer?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    // เพิ่ม ลูกค้า Raw
    suspend fun insertCustomersRaw(customers: List<Customer>): List<Long>

    @Update
    // อัปเดต ลูกค้า
    suspend fun updateCustomers(customers: List<Customer>)

    @Query("SELECT cust_id FROM customer WHERE is_synced = 0")
    // ดึงข้อมูล ที่ยังไม่ซิงก์ ลูกค้า รหัส
    suspend fun getUnsyncedCustomerIds(): List<String>

    @Transaction
    // เพิ่ม ลูกค้า
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
    // เพิ่ม ลูกค้า
    suspend fun insertCustomer(customer: Customer)

    @Query("DELETE FROM customer WHERE cust_id = :customerId")
    // ลบ ลูกค้า ตาม รหัส
    suspend fun deleteCustomerById(customerId: String)



    @Query("DELETE FROM customer WHERE is_synced = 1")
    // ลบ ทั้งหมด Synced
    suspend fun deleteAllSynced()

    @Query("DELETE FROM customer WHERE is_synced = 1 AND is_lead = 0")
    // ลบ Synced Erp ลูกค้า
    suspend fun deleteSyncedErpCustomers()

    @Query("DELETE FROM customer WHERE is_synced = 1 AND is_lead = 1")
    // ลบ ทั้งหมด Synced Leads
    suspend fun deleteAllSyncedLeads()

    @Query("DELETE FROM customer WHERE is_synced = 1 AND is_lead = 1 AND cust_id NOT IN (:incomingIds)")
    // ลบ Synced Leads Not ใน
    suspend fun deleteSyncedLeadsNotIn(incomingIds: List<String>)

    @Query("SELECT cust_id FROM customer")
    // ดึงข้อมูล ทั้งหมด ลูกค้า รหัส
    suspend fun getAllCustomerIds(): List<String>

    @Query("SELECT cust_id FROM customer WHERE user_id = :userId")
    // ดึงข้อมูล ลูกค้า รหัส ตาม ผู้ใช้ รหัส
    suspend fun getCustomerIdsByUserId(userId: String): List<String>

    @Transaction
    // ล้าง And Insert
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
    // แทนที่ Lead สแนปช็อต
    suspend fun replaceLeadSnapshot(leads: List<Customer>) {
        val normalized = leads.map { it.copy(isLead = true, isSynced = true) }
        val incomingIds = normalized.map { it.custId }
        if (incomingIds.isEmpty()) deleteAllSyncedLeads()
        else deleteSyncedLeadsNotIn(incomingIds)
        if (normalized.isNotEmpty()) insertCustomers(normalized)
        deleteSyncedErpCustomers()
    }

    @Query("DELETE FROM customer WHERE is_synced = 1 AND cust_id NOT IN (:incomingIds)")
    // ลบ Synced ลูกค้า Not ใน
    suspend fun deleteSyncedCustomersNotIn(incomingIds: List<String>)

    @Query("SELECT * FROM customer WHERE is_synced = 0")
    // ดึงข้อมูล ที่ยังไม่ซิงก์ ลูกค้า
    suspend fun getUnsyncedCustomers(): List<Customer>

    @Query("UPDATE customer SET is_synced = :isSynced WHERE cust_id = :customerId")
    // อัปเดต การซิงก์ สถานะ
    suspend fun updateSyncStatus(customerId: String, isSynced: Boolean)

    @Query("UPDATE customer SET is_lead = :isLead WHERE cust_id = :customerId")
    // อัปเดต Lead สถานะ
    suspend fun updateLeadStatus(customerId: String, isLead: Boolean)

    @Query("UPDATE project SET custId = :realId WHERE custId = :tempId")
    // เปลี่ยนการอ้างอิงของ โครงการ ลูกค้า รหัส
    suspend fun remapProjectCustomerIds(tempId: String, realId: String)

    @Query("UPDATE contact_person SET custId = :realId WHERE custId = :tempId")
    // เปลี่ยนการอ้างอิงของ ผู้ติดต่อ ลูกค้า รหัส
    suspend fun remapContactCustomerIds(tempId: String, realId: String)

    @Query("UPDATE activity_table SET cust_id = :realId WHERE cust_id = :tempId")
    // เปลี่ยนการอ้างอิงของ กิจกรรม ลูกค้า รหัส
    suspend fun remapActivityCustomerIds(tempId: String, realId: String)

    /** Replaces a server-generated customer ID and all local references atomically. */
    @Transaction
    // แทนที่ Temporary ลูกค้า
    suspend fun replaceTemporaryCustomer(tempId: String, replacement: Customer) {
        insertCustomer(replacement)
        remapProjectCustomerIds(tempId, replacement.custId)
        remapContactCustomerIds(tempId, replacement.custId)
        remapActivityCustomerIds(tempId, replacement.custId)
        deleteCustomerById(tempId)
    }
}
