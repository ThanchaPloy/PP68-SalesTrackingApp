package com.example.pp68_salestrackingapp.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pp68_salestrackingapp.data.model.Customer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * ลูกค้า lead เห็นเฉพาะคนที่สร้าง — ฝั่ง server กรองให้แล้วทั้งสามเส้น ตัวนี้เป็นด่านที่สอง
 * สำหรับตอนออฟไลน์ที่ refresh ล้ม แล้วของเพื่อนร่วมทีมที่เคยดาวน์โหลดมายังค้างใน Room
 * (บั๊กรูปเดียวกับตาราง project ที่เคยเห็นของทั้งสาขาตอนออฟไลน์)
 *
 * ★ คอลัมน์เจ้าของชื่อ user_id ไม่ใช่ create_by — Customer.createdBy ผูกกับ
 *   @ColumnInfo("user_id") และ @SerializedName("salesperson_code") สามชื่อไม่ตรงกันสักคู่
 */
@RunWith(AndroidJUnit4::class)
class CustomerLeadOwnerQueryTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: CustomerDao

    @Before
    fun createDatabase() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.customerDao()
        dao.insertCustomers(
            listOf(
                lead("C-MINE", "บริษัทของฉัน", owner = OWNER),
                lead("C-TEAM", "บริษัทของเพื่อนร่วมทีม", owner = "U2"),
                lead("C-NOONE", "บริษัทไร้เจ้าของ", owner = null)
            )
        )
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun `the company dropdown shows only leads the user owns`() = runBlocking {
        val ids = dao.getAllLeads(OWNER).first().map { it.custId }

        assertTrue("ต้องเห็นของตัวเอง", "C-MINE" in ids)
        assertFalse("ต้องไม่เห็นของเพื่อนร่วมทีม", "C-TEAM" in ids)
        assertFalse("แถวไร้เจ้าของไม่แสดงกับใคร เหมือนที่ทำกับโครงการ", "C-NOONE" in ids)
    }

    @Test
    fun `a user with no leads of their own sees an empty list`() = runBlocking {
        assertTrue(dao.getAllLeads("U-NOBODY").first().isEmpty())
    }

    /**
     * การแปลรหัสเป็นชื่อไม่ใช่ขอบเขตการมองเห็น — การ์ดนัดหมายที่อ้างถึงลูกค้าของคนอื่น
     * ต้องยังขึ้นชื่อบริษัทได้ ไม่ใช่ว่างเปล่า
     */
    @Test
    fun `name lookup is deliberately not filtered by owner`() = runBlocking {
        val names = dao.getAllForNameLookup().associateBy { it.custId }

        assertEquals(3, names.size)
        assertEquals("บริษัทของเพื่อนร่วมทีม", names["C-TEAM"]?.companyName)
    }

    private fun lead(id: String, name: String, owner: String?) = Customer(
        custId = id,
        companyName = name,
        createdBy = owner,
        isLead = true,
        isSynced = true
    )

    private companion object {
        const val OWNER = "U1"
    }
}
