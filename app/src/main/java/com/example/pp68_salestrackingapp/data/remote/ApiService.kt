package com.example.pp68_salestrackingapp.data.remote

import com.example.pp68_salestrackingapp.data.model.*
import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*


interface ApiService {

    // ── User ─────────────────────────────────────────────────────
    @GET("user")
    suspend fun getUserById(
        @Query("user_id") userId: String,
        @Query("select") select: String = "user_id,full_name,branch_id,role,email,phone_number",
        @Query("limit") limit: Int = 1
    ): Response<List<UserDto>>

    @GET("user")
    suspend fun getUsersByIds(
        @Query("user_id") userIds: String,
        @Query("select") select: String = "user_id,full_name",
        @Query("limit") limit: Int = 1000
    ): Response<List<UserDto>>

    @GET("user")
    suspend fun getUsersByBranch(
        @Query("branch_id") branchId: String,
        @Query("select") select: String = "user_id,full_name,role",
        @Query("is_active") isActive: String? = null,
        @Query("limit") limit: Int = 100
    ): Response<List<UserDto>>

    @PATCH("user")
    @Headers("Prefer: return=representation", "Content-Profile: public")
    suspend fun updateFcmToken(@Query("user_id") userId: String, @Body updates: Map<String, String>): Response<List<UserDto>>

    @PATCH("user")
    @Headers("Prefer: return=representation", "Content-Profile: public")
    suspend fun updateUserProfile(@Query("user_id") userId: String, @Body updates: Map<String, String>): Response<List<UserDto>>

    @POST("project_sales_member")
    @Headers("Prefer: return=representation", "Content-Profile: public")
    suspend fun addProjectMembers(@Body members: List<ProjectMemberInsertDto>): Response<List<ProjectMemberInsertDto>>

    @DELETE("project_sales_member")
    suspend fun deleteProjectMembers(@Query("project_code") projectId: String): Response<Unit>

    @GET("project_sales_member")
    suspend fun getProjectMembers(@Query("project_code") projectId: String, @Query("select") select: String = "emp_code,sales_role"): Response<List<ProjectMemberDto>>

    @GET("project_team_member")
    suspend fun getProjectTeamMemberCodes(
        @Query("select") select: String = "emp_code",
        @Query("limit") limit: Int = 1000
    ): Response<List<Map<String, String>>>

    @GET("employee")
    suspend fun getEmployeesByIds(
        @Query("emp_code") empCodes: String,
        @Query("select") select: String = "emp_code,emp_name",
        @Query("limit") limit: Int = 1000
    ): Response<List<Map<String, String>>>

    // ── Branch ───────────────────────────────────────────────────
    @GET("branch")
    suspend fun getBranches(@Query("limit") limit: Int = 100): Response<List<Branch>>

    @GET("branch")
    suspend fun getBranchById(@Query("branch_code") branchId: String, @Query("limit") limit: Int = 1): Response<List<Branch>>

    // ── Customer ─────────────────────────────────────────────────
    @GET("customer")
    suspend fun getCustomers(@Query("limit") limit: Int = 1000): Response<List<Customer>>

    @GET("customer")
    suspend fun getCustomersByIds(@Query("customer_code") custIds: String, @Query("limit") limit: Int = 1000): Response<List<Customer>>

    @GET("customer")
    suspend fun getCustomersBySalespersonCodes(
        @Query("salesperson_code") codes: String,   // format: in.(code1,code2,...)
        @Query("limit") limit: Int = 2000
    ): Response<List<Customer>>

    @GET("employee")
    suspend fun getEmployeeCodesByBranch(
        @Query("emp_brch_code") branchCode: String,  // format: eq.90HO
        @Query("select") select: String = "emp_code",
        @Query("stat") stat: String = "eq.1"
    ): Response<List<Map<String, String>>>

    @GET("employee")
    suspend fun getProjectEmployeeCodes(
        @Query("emp_type") empType: String = "eq.Project",
        @Query("select") select: String = "emp_code",
        @Query("stat") stat: String = "eq.1"
    ): Response<List<Map<String, String>>>

    @GET("customer")
    suspend fun getCustomersWithEmptySalesperson(
        @Query("salesperson_code") code: String = "eq.",
        @Query("limit") limit: Int = 5000
    ): Response<List<Customer>>

    @GET("customer")
    suspend fun getCustomerById(@Query("customer_code") custId: String, @Query("limit") limit: Int = 1): Response<List<Customer>>

    @POST("customer")
    @Headers("Prefer: return=representation", "Content-Profile: public")
    suspend fun addCustomer(@Body fields: @JvmSuppressWildcards Map<String, Any?>): Response<List<Customer>>

    @PATCH("customer")
    @Headers("Prefer: return=representation", "Content-Profile: public")
    suspend fun updateCustomer(
        @Query("customer_code") custId: String,
        @Body updates: @JvmSuppressWildcards Map<String, Any?>
    ): Response<List<Customer>>

    @DELETE("customer")
    suspend fun deleteCustomer(@Query("customer_code") custId: String): Response<Unit>

    // ── Contact Person ───────────────────────────────────────────
    @GET("contact_person")
    suspend fun getContactPersons(
        @Query("customer_code") custId: String? = null,
        @Query("limit") limit: Int = 1000
    ): Response<List<ContactPerson>>

    @GET("contact_person")
    suspend fun getContactsByCustomer(
        @Query("customer_code") custId: String,
        @Query("limit") limit: Int = 1000
    ): Response<List<ContactPerson>>

    @GET("contact_person")
    suspend fun getContactsByCustomerIds(
        @Query("customer_code") custIds: String,
        @Query("limit") limit: Int = 2000
    ): Response<List<ContactPerson>>

    @POST("contact_person")
    @Headers("Prefer: return=representation", "Content-Profile: public")
    suspend fun addContact(@Body fields: @JvmSuppressWildcards Map<String, Any?>): Response<List<ContactPerson>>

    @DELETE("contact_person")
    suspend fun deleteContact(@Query("contact_id") contactId: String): Response<Unit>

    @DELETE("contact_person")
    suspend fun deleteContactsByCustomer(@Query("customer_code") custId: String): Response<Unit>

    @PATCH("contact_person")
    @Headers("Prefer: return=representation", "Content-Profile: public")
    suspend fun updateContact(
        @Query("contact_id") contactId: String,
        @Body updates: @JvmSuppressWildcards Map<String, Any?>
    ): Response<List<ContactPerson>>

    // ── Project ──────────────────────────────────────────────────
    @GET("project")
    suspend fun getProjectsByIds(@Query("project_code") projectIds: String, @Query("limit") limit: Int = 1000): Response<List<Project>>

    @GET("project")
    suspend fun getProjectById(@Query("project_code") projectId: String, @Query("limit") limit: Int = 1): Response<List<Project>>

    @POST("project")
    @Headers("Prefer: return=representation", "Content-Profile: public")
    suspend fun addProject(@Body fields: @JvmSuppressWildcards Map<String, Any?>): Response<List<Project>>

    @PATCH("project")
    @Headers("Prefer: return=representation", "Content-Profile: public")
    suspend fun updateProject(@Query("project_code") projectId: String, @Body updates: @JvmSuppressWildcards Map<String, Any?>): Response<List<Project>>

    @DELETE("project")
    suspend fun deleteProject(@Query("project_code") projectId: String): Response<Unit>

    @DELETE("project")
    suspend fun deleteProjectsByCustomer(@Query("customer_code") custId: String): Response<Unit>

    // ── Project Contact ──────────────────────────────────────────
    @GET("project_contact")
    suspend fun getProjectContacts(@Query("project_code") projectId: String, @Query("select") select: String = "contact_id,contact_person(full_name)"): Response<List<ProjectContactResponse>>

    @POST("project_contact")
    @Headers("Content-Profile: public")
    suspend fun addProjectContacts(@Body contacts: List<ProjectContact>): Response<Unit>

    @DELETE("project_contact")
    suspend fun deleteProjectContacts(@Query("project_code") projectId: String): Response<Unit>

    // ── Products ─────────────────────────────────────────────────
    @GET("item_silver")
    suspend fun getProductMaster(
        @Query("limit") limit: Int = 1000
    ): Response<List<ProductMasterDto>>

    @GET("item_silver")
    suspend fun getProductsByBrand(
        @Query("product_brand_no") brandNo: String,
        @Query("limit") limit: Int = 2000
    ): Response<List<ProductMasterDto>>

    @GET("item_silver")
    suspend fun getProductsByIds(
        @Query("item_no") ids: String,
        @Query("limit") limit: Int = 1000
    ): Response<List<ProductMasterDto>>

    @GET("silver_productbrand_dx")
    suspend fun getProductBrands(@Query("limit") limit: Int = 500): Response<List<ProductBrandDxDto>>

    @GET("silver_productgroup_dx")
    suspend fun getProductGroups(@Query("limit") limit: Int = 500): Response<List<ProductGroupDxDto>>

    @GET("silver_productsubgroup_dx")
    suspend fun getProductSubgroups(@Query("limit") limit: Int = 500): Response<List<ProductSubgroupDxDto>>

    @GET("silver_productcolor_dx")
    suspend fun getProductColors(@Query("limit") limit: Int = 500): Response<List<ProductColorDxDto>>

    @GET("item_unit_of_measure")
    suspend fun getUnitOfMeasures(
        @Query("select") select: String = "code",
        @Query("limit") limit: Int = 1000
    ): Response<List<UnitOfMeasureDto>>

    @GET("project_product")
    suspend fun getProjectProducts(@Query("project_code") projectId: String, @Query("limit") limit: Int = 100): Response<List<ProjectProductDto>>

    @POST("project_product")
    @Headers("Prefer: return=representation", "Content-Profile: public")
    suspend fun addProductToProject(@Body item: ProjectProductInsertDto): Response<List<ProjectProductInsertDto>>

    @PATCH("project_product")
    @Headers("Prefer: return=representation", "Content-Profile: public")
    suspend fun updateProjectProduct(@Query("project_code") projectId: String, @Query("product_id") productId: String, @Body updates: @JvmSuppressWildcards Map<String, Any?>): Response<List<ProjectProductDto>>

    @GET("project_product")
    suspend fun getProjectProductsByStatus(@Query("project_code") projectId: String, @Query("status") status: String): Response<List<ProjectProductDto>>

    @DELETE("project_product")
    suspend fun deleteProjectProduct(@Query("project_code") projectId: String, @Query("product_id") productId: String): Response<Unit>

    // ── Appointment ──────────────────────────────────────────────
    @GET("appointment")
    suspend fun getMyAppointments(@Query("emp_code") userId: String, @Query("limit") limit: Int = 1000, @Query("order") order: String = "planned_date.desc"): Response<List<SalesActivity>>

    @POST("appointment")
    @Headers("Prefer: return=representation", "Content-Profile: public")
    suspend fun addActivity(@Body activity: SalesActivity): Response<List<SalesActivity>>

    @POST("appointment")
    @Headers("Prefer: return=representation", "Content-Profile: public")
    suspend fun addActivityMap(@Body fields: @JvmSuppressWildcards Map<String, Any?>): Response<List<SalesActivity>>

    @PATCH("appointment")
    @Headers("Prefer: return=representation", "Content-Profile: public")
    suspend fun updateActivity(@Query("appointment_id") appointmentId: String, @Body updates: @JvmSuppressWildcards Map<String, Any>): Response<List<SalesActivity>>

    @DELETE("appointment")
    suspend fun deleteActivity(@Query("appointment_id") appointmentId: String): Response<Unit>

    @DELETE("appointment")
    suspend fun deleteActivitiesByCustomer(@Query("cust_code") custId: String): Response<Unit>

    @GET("appointment")
    suspend fun getAppointmentById(@Query("appointment_id") appointmentId: String, @Query("limit") limit: Int = 1): Response<List<SalesActivity>>

    @GET("activity_master")
    suspend fun getMasterActivities(@Query("is_active") isActive: String = "eq.true", @Query("limit") limit: Int = 100): Response<List<ActivityMasterDto>>

    @GET("project_sales_member")
    suspend fun getMyProjectIds(@Query("emp_code") userId: String, @Query("select") select: String = "project_code", @Query("limit") limit: Int = 1000): Response<List<ProjectMemberDto>>

    // ── Activity Result ──────────────────────────────────────────
    @POST("activity_result")
    @Headers("Prefer: return=representation", "Content-Profile: public")
    suspend fun insertActivityResult(@Body result: ActivityResult): Response<List<ActivityResult>>

    @POST("activity_result")
    @Headers("Prefer: return=representation", "Content-Profile: public")
    suspend fun insertActivityResultMap(@Body body: @JvmSuppressWildcards Map<String, Any?>): Response<List<ActivityResult>>

    @POST("activity_result")
    @Headers("Prefer: return=representation,resolution=merge-duplicates", "Content-Profile: public")
    suspend fun upsertActivityResult(@Body result: @JvmSuppressWildcards Map<String, Any?>): Response<List<ActivityResult>>

    @GET("activity_result")
    suspend fun getActivityResult(@Query("appointment_id") appointmentId: String, @Query("limit") limit: Int = 1): Response<List<ActivityResult>>

    @GET("activity_result")
    suspend fun getResultsByUser(@Query("created_by") userId: String, @Query("limit") limit: Int = 1000): Response<List<ActivityResult>>

    @GET("activity_result")
    suspend fun getResultById(@Query("result_id") resultId: String, @Query("limit") limit: Int = 1): Response<List<ActivityResult>>

    // ── Checklist ────────────────────────────────────────────────
    @GET("appointment_checklist")
    suspend fun getChecklistByAppointment(@Query("appointment_id") appointmentId: String, @Query("limit") limit: Int = 100): Response<List<ChecklistInsertDto>>

    @POST("appointment_checklist")
    @Headers("Prefer: return=representation", "Content-Profile: public")
    suspend fun insertChecklist(@Body items: List<ChecklistInsertDto>): Response<List<ChecklistInsertDto>>

    @PATCH("appointment_checklist")
    @Headers("Prefer: return=representation", "Content-Profile: public")
    suspend fun updateChecklist(
        @Query("appointment_id") appointmentId: String,
        @Query("master_id") masterId: String,
        @Body updates: @JvmSuppressWildcards Map<String, Any>
    ): Response<List<ChecklistInsertDto>>

    @POST("call_log")
    @Headers("Prefer: return=representation", "Content-Profile: public")
    suspend fun insertCallLog(@Body body: @JvmSuppressWildcards Map<String, String>): Response<Unit>

    @POST("rpc/set_app_context")
    suspend fun setAppContext(@Body body: @JvmSuppressWildcards Map<String, String>): Response<Unit>

    @POST("rpc/get_branch_members")
    @Headers("Content-Profile: public")
    suspend fun getBranchMembers(
        @Body body: @JvmSuppressWildcards Map<String, String>
    ): Response<List<Map<String, String>>>
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
    suspend fun uploadVisitPhoto(
        @Part("appointment_id") appointmentId: RequestBody,
        @Part photo: MultipartBody.Part
    ): Response<UploadPhotoResponse>
}

data class UploadPhotoResponse(@SerializedName("photo_url") val photoUrl: String)

data class ProductBrandDxDto(@SerializedName("product_brand_no") val code: String, @SerializedName("name") val name: String)
data class ProductGroupDxDto(@SerializedName("product_group_no") val code: String, @SerializedName("name") val name: String)
data class ProductSubgroupDxDto(@SerializedName("product_subgroup_no") val code: String, @SerializedName("name") val name: String)
data class ProductColorDxDto(@SerializedName("product_color_no") val code: String, @SerializedName("name") val name: String)
data class UnitOfMeasureDto(@SerializedName("code") val code: String)
