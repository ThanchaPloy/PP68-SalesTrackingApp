package com.example.pp68_salestrackingapp.di

import android.content.Context
import android.content.SharedPreferences
import com.example.pp68_salestrackingapp.data.model.AuthUser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("sales_prefs", Context.MODE_PRIVATE)

    init {
        checkAppVersionAndForceRelogin()
        // Setup password fields are deliberately memory-only. If the process dies halfway through,
        // discard the short-lived setup session and require a fresh login.
        if (prefs.getBoolean("initial_setup_required", false)) clearToken()
    }

    // ตรวจสอบ App เวอร์ชัน And Force Relogin
    private fun checkAppVersionAndForceRelogin() {
        try {
            val lastVersion = prefs.getInt("last_installed_version_code", -1)
            val currentVersion = com.example.pp68_salestrackingapp.BuildConfig.VERSION_CODE

            if (lastVersion != currentVersion) {
                // ✅ APK มีการอัปเดตเวอร์ชันใหม่ -> เคลียร์ Session ให้บังคับผู้ใช้ Login ใหม่
                clearToken()
                // ⚠️ ใช้ .commit() แทน .apply() เพื่อบังคับเซฟลง Disk ทันที
                // ป้องกันปัญหา "ปัดแอปออกแล้วเซฟไม่ทัน" ทำให้มันเคลียร์ Token ซ้ำในรอบหน้า
                prefs.edit().putInt("last_installed_version_code", currentVersion).commit()
            }
        } catch (_: Exception) {}
    }

    // ✅ ยิง event เมื่อ token หมดอายุ/ไม่ถูกต้อง (401 จาก endpoint ที่ต้อง auth) — ให้ NavGraph
    // เด้งกลับไปหน้า Login ทันที แทนที่จะปล่อยให้แอปเงียบๆ ใช้งานไม่ได้โดยไม่มีคำอธิบาย
    private val _sessionExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val sessionExpired: SharedFlow<Unit> = _sessionExpired.asSharedFlow()

    // แจ้ง เซสชัน Expired
    fun notifySessionExpired() {
        clearToken()
        _sessionExpired.tryEmit(Unit)
    }

    // บันทึก โทเคน
    fun saveToken(token: String) {
        prefs.edit().putString("jwt_token", token).apply()
    }

    // ดึงข้อมูล โทเคน
    fun getToken(): String? {
        return prefs.getString("jwt_token", null)
    }

    // บันทึก Initial Setup Requirement
    fun saveInitialSetupRequirement(required: Boolean, phoneRequired: Boolean, noticeVersion: String?) {
        prefs.edit().apply {
            putBoolean("initial_setup_required", required)
            putBoolean("initial_setup_phone_required", phoneRequired)
            if (noticeVersion == null) remove("initial_setup_notice_version")
            else putString("initial_setup_notice_version", noticeVersion)
        }.commit()
    }

    // ดึงข้อมูล Initial Setup Info
    fun getInitialSetupInfo() = com.example.pp68_salestrackingapp.data.model.InitialSetupInfo(
        required = prefs.getBoolean("initial_setup_required", false),
        phoneRequired = prefs.getBoolean("initial_setup_phone_required", false),
        phoneNoticeVersion = prefs.getString("initial_setup_notice_version", null)
    )

    // บันทึก ผู้ใช้ ข้อมูล
    fun saveUserData(user: AuthUser) {
        prefs.edit().apply {
            putString("user_id", user.userId)
            putString("user_email", user.email)
            putString("user_role", user.role)
            putString("user_team", user.teamId)
            putString("user_name",   user.fullName)
            putString("user_branch", user.branchName)
            putString("emp_type",    user.empType)
            if (user.managedRegion == null) remove("managed_region") else putString("managed_region", user.managedRegion)
        }.apply()
    }

    // ดึงข้อมูล ผู้ใช้ ข้อมูล
    fun getUserData(): AuthUser? {
        val userId = prefs.getString("user_id", null) ?: return null
        return AuthUser(
            userId     = userId,
            email      = prefs.getString("user_email",  "") ?: "",
            role       = prefs.getString("user_role",   "sale") ?: "sale",
            teamId     = prefs.getString("user_team",   null),
            fullName   = prefs.getString("user_name",   null),
            branchName = prefs.getString("user_branch", null),
            empType    = prefs.getString("emp_type",    null),
            managedRegion = prefs.getString("managed_region", null)
        )
    }

    // ดึงข้อมูล Emp ประเภท
    fun getEmpType(): String? = prefs.getString("emp_type", null)

    /**
     * ภาคที่ผู้จัดการภาค (ZM) ดูแล และสาขาที่กำลัง "ทำงานในนาม" อยู่ตอนนี้
     *
     * สาขาที่เลือกเป็นแค่ session preference ไม่ sync ขึ้น server — เลือก/เปลี่ยนได้ตลอด
     * ไม่ผูกกับ login ครั้งไหน และต้อง clear ตอน logout/session expire เหมือน field อื่นในเซสชัน
     */
    fun getManagedRegion(): String? = prefs.getString("managed_region", null)

    fun saveActingBranch(branchId: String) {
        prefs.edit().putString("acting_branch_id", branchId).apply()
    }

    fun getActingBranch(): String? = prefs.getString("acting_branch_id", null)

    /**
     * เจ้าของข้อมูลที่ค้างอยู่ใน Room ขณะนี้ — ต้องอยู่รอดข้าม [clearToken] โดยตั้งใจ
     *
     * session หมดอายุจะเรียก clearToken() ซึ่งลบ user_id ไปด้วย ทำให้ AuthRepository.login()
     * หาไม่ได้ว่าใครเป็นเจ้าของงานที่ยังไม่ได้ซิงค์ในเครื่อง แล้วตีเป็น "คนเดิม login ซ้ำ" ทุกครั้ง
     * ผลคือถ้าเซลส์อีกคนมา login บนเครื่องเดียวกันหลัง session หมดอายุ งานออฟไลน์ของคนก่อนจะถูก
     * ดันขึ้นเซิร์ฟเวอร์ด้วย token ของคนใหม่ และ backend บังคับเจ้าของจาก JWT (W1)
     * = งานของคน A ไปติดชื่อคน B
     */
    fun saveLocalDataOwner(userId: String) {
        prefs.edit().putString("local_data_owner", userId).apply()
    }

    // ดึงข้อมูล Local ข้อมูล เจ้าของ
    fun getLocalDataOwner(): String? = prefs.getString("local_data_owner", null)

    /** เรียกตอน logout เท่านั้น — ตอนนั้น Room ถูกล้างแล้ว จึงไม่มีเจ้าของให้จำอีก */
    fun clearLocalDataOwner() {
        prefs.edit().remove("local_data_owner").apply()
    }

    // ล้าง โทเคน
    fun clearToken() {
        prefs.edit().apply {
            remove("jwt_token")
            remove("user_id")
            remove("user_email")
            remove("user_role")
            remove("user_team")
            remove("user_name")
            remove("user_branch")
            remove("emp_type")
            remove("managed_region")
            remove("acting_branch_id")
            remove("initial_setup_required")
            remove("initial_setup_phone_required")
            remove("initial_setup_notice_version")
        }.apply()
    }

    // บันทึก Visit Reminder Enabled
    fun saveVisitReminderEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("visit_reminder_enabled", enabled).apply()
    }

    // ตรวจว่า Visit Reminder Enabled
    fun isVisitReminderEnabled(): Boolean {
        return prefs.getBoolean("visit_reminder_enabled", true) // default = เปิด
    }

}
