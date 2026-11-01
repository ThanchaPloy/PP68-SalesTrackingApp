package com.example.pp68_salestrackingapp.data.remote

import com.example.pp68_salestrackingapp.data.model.*
import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*


interface ApiService {

    // ── Delta sync v2 ───────────────────────────────────────────
    @GET("sync/v2/cursor")
    // ดึงข้อมูล การซิงก์ Cursor
    suspend fun getSyncCursor(): Response<SyncCursorResponse>

    @GET("sync/v2/changes")
    // ดึงข้อมูล การซิงก์ Changes
    suspend fun getSyncChanges(
        @Query("after") after: Long,
        @Query("limit") limit: Int = 100
    ): Response<SyncChangePage>

    @GET("sync/v2/snapshot/{entity}")
    // ดึงข้อมูล การซิงก์ สแนปช็อต
    suspend fun getSyncSnapshot(
        @Path("entity") entity: String,
        @Query("after") after: String? = null,
        @Query("limit") limit: Int = 200
    ): Response<SyncSnapshotPage>

    // ── Place search (proxy ไป Geoapify ที่ backend — API key ไม่อยู่ในแอป) ──
    @GET("places/autocomplete")
    // ค้นหา Places
    suspend fun searchPlaces(@Query("text") text: String): Response<List<PlaceSuggestion>>

    @GET("places/reverse")
    // แปลงพิกัดเป็นรายละเอียดสถานที่ผ่าน API
    suspend fun reverseGeocode(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double
    ): Response<List<PlaceSuggestion>>

    // ── User ─────────────────────────────────────────────────────
    // Real DB table is "employee", but the deployed Ktor backend exposes it at /user
    // with params user_id / branch_id and raw emp_code/emp_name/... JSON keys (see UserDto).
    @GET("user")
    // ดึงข้อมูล ผู้ใช้ ตาม รหัส
    suspend fun getUserById(@Query("user_id") userId: String): Response<List<UserDto>>


    @PATCH("user")
    // อัปเดต ผู้ใช้ Profile
    suspend fun updateUserProfile(@Query("user_id") userId: String, @Body updates: Map<String, String>): Response<List<UserDto>>

    @GET("project_team_member")
    // ดึงข้อมูล โครงการ Team Member Codes
    suspend fun getProjectTeamMemberCodes(
        @Query("select") select: String = "emp_code",
        @Query("limit") limit: Int = 1000
    ): Response<List<Map<String, String>>>

    // ── Branch ───────────────────────────────────────────────────
    @GET("branch")
    // ดึงข้อมูล สาขา
    suspend fun getBranches(@Query("limit") limit: Int = 100): Response<List<Branch>>

    @GET("branch")
    // ดึงข้อมูล สาขา ตาม รหัส
    suspend fun getBranchById(@Query("branch_code") branchId: String, @Query("limit") limit: Int = 1): Response<List<Branch>>

    // ── Customer ─────────────────────────────────────────────────
    @GET("customer")
    // ดึงข้อมูล ลูกค้า
    suspend fun getCustomers(@Query("limit") limit: Int = 5000): Response<List<Customer>>

    /** Phase 3 snapshot source: only app-created leads may be stored in Room. */
    @GET("lead_customer")
    // ดึงข้อมูล Leads
    suspend fun getLeads(): Response<List<Customer>>

    /** Authenticated, company-wide ERP lookup. Results are display-only and must not enter Room. */
    @GET("customer/search")
    // ค้นหา Erp ลูกค้า
    suspend fun searchErpCustomers(
        @Query("q") query: String,
        @Query("after") after: String? = null,
        @Query("limit") limit: Int = 20
    ): Response<CustomerLookupPage>

    @GET("customer/lookup")
    // ค้นหารายละเอียด Erp ลูกค้า
    suspend fun lookupErpCustomer(
        @Query("customer_code") customerCode: String
    ): Response<CustomerLookup>

    @GET("customer")
    // ดึงข้อมูล ลูกค้า ตาม รหัส
    suspend fun getCustomersByIds(@Query("customer_code") custIds: String, @Query("limit") limit: Int = 1000): Response<List<Customer>>

    @GET("customer")
    // ดึงข้อมูล ลูกค้า ตาม Salesperson Codes
    suspend fun getCustomersBySalespersonCodes(
        @Query("salesperson_code") codes: String,   // format: in.(code1,code2,...)
        @Query("limit") limit: Int,
        @Query("offset") offset: Int
    ): Response<List<Customer>>

    @GET("user")
    // ดึงข้อมูล พนักงาน Codes ตาม สาขา
    suspend fun getEmployeeCodesByBranch(
        @Query("branch_id") branchCode: String  // format: eq.90HO
    ): Response<List<Map<String, String>>>

    @GET("customer")
    // ponytail: backend's /customer route checks customer_code before salesperson_code and
    // has no ilike support — sending both together always 404s (customer_code treated as a
    // literal exact-match lookup). Filter by branch suffix client-side instead (see CustomerRepository).
    suspend fun getCustomersWithEmptySalesperson(
        @Query("salesperson_code") code: String = "eq.",
        @Query("limit") limit: Int,
        @Query("offset") offset: Int
    ): Response<List<Customer>>

    @GET("customer")
    // ดึงข้อมูล ลูกค้า ตาม รหัส
    suspend fun getCustomerById(@Query("customer_code") custId: String, @Query("limit") limit: Int = 1): Response<List<Customer>>

    @POST("lead_customer")
    @Headers("Prefer: return=representation", "Content-Profile: public")
    // เพิ่ม ลูกค้า
    suspend fun addCustomer(
        @Body fields: @JvmSuppressWildcards Map<String, Any?>,
        @Header("Idempotency-Key") operationId: String? = null
    ): Response<List<Customer>>

    @PATCH("customer")
    @Headers("Prefer: return=representation", "Content-Profile: public")
    // อัปเดต ลูกค้า
    suspend fun updateCustomer(
        @Query("customer_code") custId: String,
        @Body updates: @JvmSuppressWildcards Map<String, Any?>
    ): Response<List<Customer>>

    @PATCH("lead_customer")
    @Headers("Prefer: return=representation", "Content-Profile: public")
    // อัปเดต Lead ลูกค้า
    suspend fun updateLeadCustomer(
        @Query("customer_code") custId: String,
        @Body updates: @JvmSuppressWildcards Map<String, Any?>
    ): Response<List<Customer>>

    @DELETE("customer")
    // ลบ ลูกค้า
    suspend fun deleteCustomer(@Query("customer_code") custId: String): Response<Unit>

    // ── Contact Person ───────────────────────────────────────────
    @GET("contact_person")
    // ดึงข้อมูล ผู้ติดต่อ Persons
    suspend fun getContactPersons(
        @Query("customer_code") custId: String? = null,
        @Query("limit") limit: Int = 1000
    ): Response<List<ContactPerson>>

    @GET("contact_person")
    // ดึงข้อมูล ผู้ติดต่อ ตาม ลูกค้า
    suspend fun getContactsByCustomer(
        @Query("customer_code") custId: String,
        @Query("limit") limit: Int = 1000
    ): Response<List<ContactPerson>>

    @GET("contact_person")
    // ดึงข้อมูล ผู้ติดต่อ ตาม ลูกค้า รหัส
    suspend fun getContactsByCustomerIds(
        @Query("customer_code") custIds: String,
        @Query("limit") limit: Int = 2000
    ): Response<List<ContactPerson>>

    @GET("contact_person")
    // ดึงข้อมูล ผู้ติดต่อ ตาม รหัส
    suspend fun getContactsByIds(
        @Query("contact_id") contactIds: String,
        @Query("limit") limit: Int = 1000
    ): Response<List<ContactPerson>>

    @POST("contact_person")
    @Headers("Prefer: return=representation", "Content-Profile: public")
    // เพิ่ม ผู้ติดต่อ
    suspend fun addContact(@Body fields: @JvmSuppressWildcards Map<String, Any?>): Response<List<ContactPerson>>

    @DELETE("contact_person")
    // ลบ ผู้ติดต่อ
    suspend fun deleteContact(@Query("contact_id") contactId: String): Response<Unit>

    @DELETE("contact_person")
    // ลบ ผู้ติดต่อ ตาม ลูกค้า
    suspend fun deleteContactsByCustomer(@Query("customer_code") custId: String): Response<Unit>

    @PATCH("contact_person")
    @Headers("Prefer: return=representation", "Content-Profile: public")
    // อัปเดต ผู้ติดต่อ
    suspend fun updateContact(
        @Query("contact_id") contactId: String,
        @Body updates: @JvmSuppressWildcards Map<String, Any?>
    ): Response<List<ContactPerson>>

    // ── Project ──────────────────────────────────────────────────
    @GET("project")
    // ดึงข้อมูล โครงการ ตาม Creator
    suspend fun getProjectsByCreator(@Query("create_by") userId: String, @Query("limit") limit: Int = 5000): Response<List<Project>>

    @GET("project")
    // ดึงข้อมูล โครงการ ตาม รหัส
    suspend fun getProjectById(@Query("project_id") projectId: String, @Query("limit") limit: Int = 1): Response<List<Project>>

    @POST("project")
    @Headers("Prefer: return=representation", "Content-Profile: public")
    // เพิ่ม โครงการ
    suspend fun addProject(
        @Body fields: @JvmSuppressWildcards Map<String, Any?>,
        @Header("Idempotency-Key") operationId: String? = null
    ): Response<List<Project>>

    @PATCH("project")
    @Headers("Prefer: return=representation", "Content-Profile: public")
    // อัปเดต โครงการ
    suspend fun updateProject(@Query("project_code") projectId: String, @Body updates: @JvmSuppressWildcards Map<String, Any?>): Response<List<Project>>

    @DELETE("project")
    // ลบ โครงการ
    suspend fun deleteProject(@Query("project_code") projectId: String): Response<Unit>

    @DELETE("project")
    // ลบ โครงการ ตาม ลูกค้า
    suspend fun deleteProjectsByCustomer(@Query("customer_code") custId: String): Response<Unit>

    // ── Project Contact ──────────────────────────────────────────
    // ประวัติการแก้ไขปัจจัยข้อ 4-9 — อ่านอย่างเดียว แถวถูกเขียนโดย DB trigger ฝั่ง backend
    @GET("project_factor_log")
    // ดึงข้อมูล โครงการ Factor Log
    suspend fun getProjectFactorLog(@Query("project_id") projectId: String): Response<List<com.example.pp68_salestrackingapp.data.model.ProjectFactorLog>>

    @GET("project_contact")
    // ดึงข้อมูล โครงการ ผู้ติดต่อ
    suspend fun getProjectContacts(@Query("project_id") projectId: String, @Query("select") select: String = "contact_id"): Response<List<ProjectContactResponse>>

    @POST("project_contact")
    @Headers("Content-Profile: public")
    // เพิ่ม โครงการ ผู้ติดต่อ
    suspend fun addProjectContacts(@Body contacts: List<ProjectContact>): Response<Unit>

    @DELETE("project_contact")
    // ลบ โครงการ ผู้ติดต่อ
    suspend fun deleteProjectContacts(@Query("project_code") projectId: String): Response<Unit>

    // ── Products ─────────────────────────────────────────────────
    @GET("product")
    // ดึงข้อมูล Product ข้อมูลมาสเตอร์
    suspend fun getProductMaster(
        @Query("description") description: String? = null,
        @Query("product_brand_no") brandNo: String? = null,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
        @Query("order") order: String = "description.asc"
    ): Response<List<ProductMasterDto>>

    @GET("product")
    // ดึงข้อมูล Products ตาม Brand
    suspend fun getProductsByBrand(
        @Query("product_brand_no") brandNo: String,
        @Query("limit") limit: Int = 2000
    ): Response<List<ProductMasterDto>>

    @GET("product")
    // ดึงข้อมูล Products ตาม รหัส
    suspend fun getProductsByIds(
        @Query("item_no") ids: String,
        @Query("limit") limit: Int = 1000
    ): Response<List<ProductMasterDto>>

    @GET("product_brand")
    // ดึงข้อมูล Product Brands
    suspend fun getProductBrands(@Query("limit") limit: Int = 500): Response<List<ProductBrandDxDto>>

    @GET("product_group")
    // ดึงข้อมูล Product Groups
    suspend fun getProductGroups(@Query("limit") limit: Int = 500): Response<List<ProductGroupDxDto>>

    @GET("product_subgroup")
    // ดึงข้อมูล Product Subgroups
    suspend fun getProductSubgroups(@Query("limit") limit: Int = 500): Response<List<ProductSubgroupDxDto>>

    @GET("product_color")
    // ดึงข้อมูล Product Colors
    suspend fun getProductColors(@Query("limit") limit: Int = 500): Response<List<ProductColorDxDto>>

    @GET("product")
    // ดึงข้อมูล Unit ของ Measures
    suspend fun getUnitOfMeasures(
        @Query("select") select: String = "base_unit_of_measure",
        @Query("limit") limit: Int = 2000,
        @Query("offset") offset: Int = 0
    ): Response<List<UnitOfMeasureDto>>

    // ── Appointment ──────────────────────────────────────────────
    @GET("appointment")
    // ดึงรายการนัดหมายของผู้ใช้ปัจจุบัน
    suspend fun getMyAppointments(@Query("user_id") userId: String, @Query("limit") limit: Int = 5000, @Query("order") order: String = "planned_date.desc"): Response<List<SalesActivity>>

    @POST("appointment")
    @Headers("Prefer: return=representation", "Content-Profile: public")
    // เพิ่ม กิจกรรม
    suspend fun addActivity(
        @Body activity: SalesActivity,
        @Header("Idempotency-Key") operationId: String? = null
    ): Response<List<SalesActivity>>

    @POST("appointment")
    @Headers("Prefer: return=representation", "Content-Profile: public")
    // เพิ่ม กิจกรรม แผนที่ข้อมูล
    suspend fun addActivityMap(
        @Body fields: @JvmSuppressWildcards Map<String, Any?>,
        @Header("Idempotency-Key") operationId: String? = null
    ): Response<List<SalesActivity>>

    @PATCH("appointment")
    @Headers("Prefer: return=representation", "Content-Profile: public")
    // อัปเดต กิจกรรม
    suspend fun updateActivity(@Query("appointment_id") appointmentId: String, @Body updates: @JvmSuppressWildcards Map<String, Any?>): Response<List<SalesActivity>>

    @DELETE("appointment")
    // ลบ กิจกรรม
    suspend fun deleteActivity(@Query("appointment_id") appointmentId: String): Response<Unit>

    @DELETE("appointment")
    // ลบ กิจกรรม ตาม ลูกค้า
    suspend fun deleteActivitiesByCustomer(@Query("cust_id") custId: String): Response<Unit>

    @GET("appointment")
    // ดึงข้อมูล นัดหมาย ตาม รหัส
    suspend fun getAppointmentById(@Query("appointment_id") appointmentId: String, @Query("limit") limit: Int = 1): Response<List<SalesActivity>>

    @GET("activity_master")
    // ดึงข้อมูล ข้อมูลมาสเตอร์ กิจกรรม
    suspend fun getMasterActivities(@Query("is_active") isActive: String = "eq.true", @Query("limit") limit: Int = 100): Response<List<ActivityMasterDto>>

    // W5a: master data สำหรับตัวเลือก stage/เหตุผล — ข้อมูลอ้างอิงร่วมกันทุกคน
    @GET("project_stage_master")
    // ดึงข้อมูล โครงการ Stage Masters
    suspend fun getProjectStageMasters(@Query("is_active") isActive: String = "eq.true"): Response<List<ProjectStageMasterDto>>

    @GET("loss_reason_master")
    // ดึงข้อมูล Loss Reason Masters
    suspend fun getLossReasonMasters(@Query("is_active") isActive: String = "eq.true"): Response<List<LossReasonMasterDto>>

    // W5b: คำถามปัจจัยข้อ 4-7 ของหน้าบันทึกผลการขาย
    @GET("deal_factor_question")
    // ดึงข้อมูล Deal Factor Questions
    suspend fun getDealFactorQuestions(): Response<List<DealFactorQuestionDto>>

    // ── Activity Result ──────────────────────────────────────────
    @POST("activity_result")
    @Headers("Prefer: return=representation", "Content-Profile: public")
    // เพิ่ม กิจกรรม ผลการขาย
    suspend fun insertActivityResult(
        @Body result: ActivityResult,
        @Header("Idempotency-Key") operationId: String? = null
    ): Response<List<ActivityResult>>

    @POST("activity_result")
    @Headers("Prefer: return=representation", "Content-Profile: public")
    // เพิ่ม กิจกรรม ผลการขาย แผนที่ข้อมูล
    suspend fun insertActivityResultMap(
        @Body body: @JvmSuppressWildcards Map<String, Any?>,
        @Header("Idempotency-Key") operationId: String? = null
    ): Response<List<ActivityResult>>

    @POST("activity_result")
    @Headers("Prefer: return=representation,resolution=merge-duplicates", "Content-Profile: public")
    // เพิ่มหรืออัปเดต กิจกรรม ผลการขาย
    suspend fun upsertActivityResult(@Body result: @JvmSuppressWildcards Map<String, Any?>): Response<List<ActivityResult>>

    @GET("activity_result")
    // ดึงข้อมูล กิจกรรม ผลการขาย
    suspend fun getActivityResult(
        @Query("appointment_id") appointmentId: String,
        @Query("is_latest") isLatest: String = "eq.true",
        @Query("limit") limit: Int = 1
    ): Response<List<ActivityResult>>

    // ✅ ใช้ตอน mark version เก่าว่าไม่ใช่ล่าสุดแล้ว (result_group_id ผูก version เดียวกันเข้าด้วยกัน)
    @PATCH("activity_result")
    @Headers("Prefer: return=minimal", "Content-Profile: public")
    // อัปเดต กิจกรรม ผลการขาย
    suspend fun updateActivityResult(@Query("result_id") resultId: String, @Body updates: @JvmSuppressWildcards Map<String, Any?>): Response<Unit>

    @GET("activity_result")
    // ดึงข้อมูล ผลการขาย ตาม ผู้ใช้
    suspend fun getResultsByUser(@Query("created_by") userId: String, @Query("limit") limit: Int = 5000): Response<List<ActivityResult>>

    @GET("activity_result")
    // ดึงข้อมูล ผลการขาย ตาม รหัส
    suspend fun getResultById(@Query("result_id") resultId: String, @Query("limit") limit: Int = 1): Response<List<ActivityResult>>

    // ── Activity Result Photo (รองรับหลายรูปต่อบันทึกผลการขาย) ────
    @GET("activity_result_photo")
    // ดึงข้อมูล ผลการขาย รูปภาพ
    suspend fun getResultPhotos(
        @Query("result_id") resultId: String,
        @Query("order") order: String = "photo_order.asc",
        @Query("limit") limit: Int = 20
    ): Response<List<ActivityResultPhoto>>

    @POST("activity_result_photo")
    @Headers("Prefer: return=representation", "Content-Profile: public")
    // เพิ่ม ผลการขาย รูปภาพ
    suspend fun addResultPhotos(@Body photos: List<ActivityResultPhoto>): Response<List<ActivityResultPhoto>>

    @DELETE("activity_result_photo")
    // ลบ ผลการขาย รูปภาพ
    suspend fun deleteResultPhotos(@Query("result_id") resultId: String): Response<Unit>

    // ── Appointment Contact ───────────────────────────────────────
    @GET("appointment_contact")
    // ดึงข้อมูล นัดหมาย ผู้ติดต่อ
    suspend fun getAppointmentContacts(
        @Query("appointment_id") appointmentId: String,
        @Query("limit") limit: Int = 100
    ): Response<List<AppointmentContact>>

    @POST("appointment_contact")
    @Headers("Content-Profile: public")
    // เพิ่ม นัดหมาย ผู้ติดต่อ
    suspend fun addAppointmentContacts(@Body contacts: List<AppointmentContact>): Response<Unit>

    @DELETE("appointment_contact")
    // ลบ นัดหมาย ผู้ติดต่อ
    suspend fun deleteAppointmentContacts(@Query("appointment_id") appointmentId: String): Response<Unit>

    // ── Checklist ────────────────────────────────────────────────
    @GET("appointment_checklist")
    // ดึงข้อมูล เช็กลิสต์ ตาม นัดหมาย
    suspend fun getChecklistByAppointment(@Query("appointment_id") appointmentId: String, @Query("limit") limit: Int = 100): Response<List<ChecklistInsertDto>>

    @POST("appointment_checklist")
    @Headers("Prefer: return=representation", "Content-Profile: public")
    // เพิ่ม เช็กลิสต์
    suspend fun insertChecklist(@Body items: List<ChecklistInsertDto>): Response<List<ChecklistInsertDto>>

    @PATCH("appointment_checklist")
    @Headers("Prefer: return=representation", "Content-Profile: public")
    // อัปเดต เช็กลิสต์
    suspend fun updateChecklist(
        @Query("appointment_id") appointmentId: String,
        @Query("master_id") masterId: String,
        @Body updates: @JvmSuppressWildcards Map<String, Any?>
    ): Response<List<ChecklistInsertDto>>

    @DELETE("appointment_checklist")
    // ลบ เช็กลิสต์ ตาม นัดหมาย
    suspend fun deleteChecklistByAppointment(@Query("appointment_id") appointmentId: String): Response<Unit>


    @POST("rpc/set_app_context")
    // กำหนด App Context
    suspend fun setAppContext(@Body body: @JvmSuppressWildcards Map<String, String>): Response<Unit>

    // Mobile phase 2 ข้อ 2 — เขียนทางเดียว ไม่มี GET (ดู CLAUDE.md: endpoint ใหม่ เป็น REST ธรรมดา
    // ไม่ใช่สไตล์ PostgREST) emp_code บังคับจาก JWT ฝั่ง server เสมอ ไม่ต้องส่งมาในนี้
    @POST("call_log")
    suspend fun insertCallLog(@Body body: @JvmSuppressWildcards Map<String, String?>): Response<Unit>

}

data class ProductMasterDto(
    @SerializedName("item_no")              val productId:        String,
    @SerializedName("description")          val description:      String? = null,
    @SerializedName("variant_code")         val variantCode:      String? = null,
    @SerializedName("product_brand_no")     val productBrandNo:   String? = null,
    @SerializedName("product_group_no")     val productGroupNo:   String? = null,
    @SerializedName("product_subgroup_no")  val productSubgroupNo: String? = null,
    @SerializedName("product_color_no")     val productColorNo:   String? = null,
    @SerializedName("base_unit_of_measure") val unit:             String? = null,
    @SerializedName("product_weight")       val weight:           String? = null,
    @SerializedName("brand_name")           val brandName:        String? = null,
    @SerializedName("group_name")           val groupName:        String? = null,
    @SerializedName("subgroup_name")        val subgroupName:     String? = null,
    @SerializedName("color_name")           val colorName:        String? = null
)

data class ProjectContactResponse(
    @SerializedName("contact_id") val contactId: String,
    @SerializedName("contact_person") val contactPerson: ContactPerson? = null
)

interface UploadApiService {
    @Multipart
    @POST("upload-visit-photo")
    // อัปโหลด Visit รูปภาพ
    suspend fun uploadVisitPhoto(
        @Header("Idempotency-Key") operationId: String,
        @Header("X-Content-SHA256") sha256: String,
        @Part("appointment_id") appointmentId: RequestBody,
        @Part photo: MultipartBody.Part
    ): Response<UploadPhotoResponse>
}

data class UploadPhotoResponse(@SerializedName("photo_url") val photoUrl: String)

data class ProductBrandDxDto(@SerializedName("product_brand_no") val code: String, @SerializedName("name") val name: String)
data class ProductGroupDxDto(@SerializedName("product_group_no") val code: String, @SerializedName("name") val name: String)
data class ProductSubgroupDxDto(@SerializedName("product_subgroup_no") val code: String, @SerializedName("name") val name: String)
data class ProductColorDxDto(@SerializedName("product_color_no") val code: String, @SerializedName("name") val name: String)
data class UnitOfMeasureDto(@SerializedName("base_unit_of_measure") val code: String?)
