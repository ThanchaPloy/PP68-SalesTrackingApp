package com.example.pp68_salestrackingapp.data.repository

import android.util.Log
import com.example.pp68_salestrackingapp.utils.DealFactors
import com.example.pp68_salestrackingapp.utils.LossReasons
import com.example.pp68_salestrackingapp.utils.ProjectStages
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncManager @Inject constructor(
    private val customerRepo: CustomerRepository,
    private val contactRepo: ContactRepository,
    private val projectRepo: ProjectRepository,
    private val activityRepo: ActivityRepository,
    private val masterDataRepo: MasterDataRepository
) {
    /**
     * ส่วนที่โหลดไม่สำเร็จในการซิงค์รอบล่าสุด (ว่าง = ครบดี)
     *
     * เดิมทุกส่วนถูกครอบ try/catch แล้วพิมพ์ลง log อย่างเดียว ผู้ใช้จึง login ผ่านปกติแล้วไปเจอ
     * หน้าจอว่างเปล่าโดยไม่มีอะไรบอกว่าโหลดไม่สำเร็จ — เข้าใจว่า "ข้อมูลหาย" ทั้งที่แค่กดใหม่ก็ได้
     * เก็บเป็น StateFlow บน @Singleton ตัวนี้ เพราะหน้าที่ต้องแสดง (หน้าแรก) อยู่คนละที่กับ
     * จุดที่เรียกซิงค์ (ตอน login) การส่งค่าผ่าน login -> navigation -> หน้าแรกจะพันกันกว่ามาก
     */
    private val _failedParts = MutableStateFlow<List<String>>(emptyList())
    val failedParts: StateFlow<List<String>> = _failedParts.asStateFlow()

    fun clearFailedParts() {
        _failedParts.value = emptyList()
    }

    suspend fun syncAll(userId: String, branchId: String) {
        // 5 งานด้านล่างรันพร้อมกันคนละ coroutine จึงเขียนลงลิสต์เดียวกันข้ามเธรดได้ ต้องล็อก
        val failed = java.util.Collections.synchronizedList(mutableListOf<String>())

        suspend fun step(label: String, block: suspend () -> Unit) {
            try {
                block()
            } catch (e: Exception) {
                failed += label
                Log.e("SyncManager", "Failed to sync $label: ${e.message}")
            }
        }

        supervisorScope {
            launch { step("โครงการ") { projectRepo.refreshProjects(userId) } }

            // Customers then contacts — contacts are scoped to customer_codes in Room
            launch {
                step("ลูกค้าและผู้ติดต่อ") {
                    if (branchId.isNotEmpty()) {
                        customerRepo.refreshCustomers(branchId)
                    }
                    contactRepo.refreshContacts()
                }
            }

            launch { step("นัดหมาย") { activityRepo.refreshActivities(userId) } }

            launch { step("บันทึกผล") { activityRepo.refreshResults(userId) } }

            // W5a: master data ของ stage/เหตุผล — ล้มเหลวแล้วเงียบได้ เพราะ ProjectStages/LossReasons
            // มี fallback เป็นค่าที่ฝังมากับ APK อยู่แล้ว จึงไม่นับเข้ารายการที่แจ้งผู้ใช้
            // (แจ้งไปก็ไม่มีอะไรให้ทำ และหน้าจอยังใช้งานได้ครบ)
            launch {
                try {
                    ProjectStages.applyServerData(masterDataRepo.getProjectStages())
                    LossReasons.applyServerData(masterDataRepo.getLossReasons())
                    DealFactors.applyServerData(masterDataRepo.getDealFactorQuestions())
                } catch (e: Exception) {
                    Log.e("SyncManager", "Failed to sync stage/loss-reason/deal-factor master data: ${e.message}")
                }
            }
        }

        // supervisorScope รอให้ทุก launch จบก่อนหลุดออกมา ตรงนี้จึงได้ผลรวมที่ครบแล้วเสมอ
        _failedParts.value = failed.toList()
    }
}
