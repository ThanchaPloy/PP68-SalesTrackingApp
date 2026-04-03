package com.example.pp68_salestrackingapp.data.remote

import com.example.pp68_salestrackingapp.data.model.*
import com.google.gson.annotations.SerializedName
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
    suspend fun getUsersByBranch(
        @Query("branch_id") branchId: String,
        @Query("select") select: String = "user_id,full_name,role",
        @Query("is_active") isActive: String = "eq.true",
        @Query("limit") limit: Int = 100
    ): Response<List<UserDto>>

    @PATCH("user")
    @Headers("Prefer: return=representation")
    suspend fun updateFcmToken(
        @Query("user_id") userId: String,
        @Body updates: Map<String, String>
    ): Response<List<UserDto>>

    @PATCH("user")
    @Headers("Prefer: return=representation")
    suspend fun updateUserProfile(
        @Query("user_id") userId: String,
        @Body updates: Map<String, String>
    ): Response<List<UserDto>>

    // ── Project Sales Member ──────────────────────────────────────
    @POST("project_sales_member")
    @Headers("Prefer: return=representation")
    suspend fun addProjectMembers(
        @Body members: List<ProjectMemberInsertDto>
    ): Response<List<ProjectMemberInsertDto>>

    // ── Branch ───────────────────────────────────────────────────
    @GET("branch")
    suspend fun getBranches(
        @Query("limit") limit: Int = 100
    ): Response<List<Branch>>

    @GET("branch")
    suspend fun getBranchById(
        @Query("branch_id") branchId: String,
        @Query("limit") limit: Int = 1
    ): Response<List<Branch>>

    // ── Customer ─────────────────────────────────────────────
    @GET("customer")
    suspend fun getCustomers(
        @Query("limit") limit: Int = 1000
    ): Response<List<Customer>>

    @GET("customer")
    suspend fun getCustomersByIds(
        @Query("cust_id") custIds: String,
        @Query("limit") limit: Int = 1000
    ): Response<List<Customer>>

    @GET("customer")
    suspend fun getCustomerById(
        @Query("cust_id") custId: String,
        @Query("limit") limit: Int = 1
    ): Response<List<Customer>>

    @POST("customer")
    @Headers("Prefer: return=representation")
    suspend fun addCustomer(@Body customer: Customer): Response<List<Customer>>

    // ── Contact Person ───────────────────────────────────────
    @GET("contact_person")
    suspend fun getContactsByCustomerIds(
        @Query("cust_id") custIds: String,
        @Query("limit") limit: Int = 1000
    ): Response<List<ContactPerson>>

    @POST("contact_person")
    @Headers("Prefer: return=representation")
    suspend fun addContact(@Body contact: ContactPerson): Response<List<ContactPerson>>

    // ── Project ──────────────────────────────────────────────
    @GET("project")
    suspend fun getProjectsByIds(
        @Query("project_id") projectIds: String,
        @Query("limit") limit: Int = 1000
    ): Response<List<Project>>

    @GET("project")
    suspend fun getProjectById(
        @Query("project_id") projectId: String,
        @Query("limit") limit: Int = 1
    ): Response<List<Project>>

    @POST("project")
    @Headers("Prefer: return=representation")
    suspend fun addProject(@Body project: Project): Response<List<Project>>

    @PATCH("project")
    @Headers("Prefer: return=representation")
    suspend fun updateProject(
        @Query("project_id") projectId: String,
        @Body updates: Map<String, String>
    ): Response<List<Project>>

    // ── Project Contact (Multi-Contact) ──────────────────────────
    @GET("project_contact")
    suspend fun getProjectContacts(
        @Query("project_id") projectId: String,
        @Query("select") select: String = "contact_id,contact_person(full_name)"
    ): Response<List<ProjectContactResponse>>

    @POST("project_contact")
    suspend fun addProjectContacts(
        @Body contacts: List<ProjectContact>
    ): Response<Unit>

    @DELETE("project_contact")
    suspend fun deleteProjectContacts(
        @Query("project_id") projectId: String
    ): Response<Unit>

    // ── Product ───────────────────────────────────────────────────
    @GET("products")
    suspend fun getProductMaster(
        @Query("limit") limit: Int = 1000
    ): Response<List<ProductMasterDto>>

    @GET("project_product")
    suspend fun getProjectProducts(
        @Query("project_id") projectId: String,
        @Query("limit") limit: Int = 100
    ): Response<List<ProjectProductDto>>

    @POST("project_product")
    @Headers("Prefer: return=representation")
    suspend fun addProductToProject(
        @Body item: ProjectProductInsertDto
    ): Response<List<ProjectProductInsertDto>>

    // ── Appointment ──────────────────────────────────────────
    @GET("appointment")
    suspend fun getMyAppointments(
        @Query("user_id") userId: String,
        @Query("limit") limit: Int = 1000,
        @Query("order") order: String = "planned_date.desc"
    ): Response<List<SalesActivity>>

    @GET("appointment")
    suspend fun getAppointmentById(
        @Query("appointment_id") appointmentId: String,
        @Query("limit") limit: Int = 1
    ): Response<List<SalesActivity>>

    @POST("appointment")
    @Headers("Prefer: return=representation")
    suspend fun addActivity(@Body activity: SalesActivity): Response<List<SalesActivity>>

    @PATCH("appointment")
    @Headers("Prefer: return=representation")
    suspend fun updateActivity(
        @Query("appointment_id") appointmentId: String,
        @Body updates: @JvmSuppressWildcards Map<String, Any>
    ): Response<List<SalesActivity>>

    @DELETE("appointment")
    suspend fun deleteActivity(
        @Query("appointment_id") appointmentId: String
    ): Response<Unit>

    // ── Activity Master & Checklist ──────────────────────────
    @GET("activity_master")
    suspend fun getMasterActivities(
        @Query("is_active") isActive: String = "eq.true",
        @Query("limit") limit: Int = 100
    ): Response<List<ActivityMasterDto>>

    @GET("activity_checklist")
    suspend fun getChecklistByAppointment(
        @Query("appointment_id") appointmentId: String,
        @Query("select") select: String = "master_id,is_done",
        @Query("limit") limit: Int = 50
    ): Response<List<ChecklistItemDto>>

    @POST("activity_checklist")
    @Headers("Prefer: return=representation")
    suspend fun insertChecklist(
        @Body items: List<ChecklistInsertDto>
    ): Response<List<ChecklistInsertDto>>

    @PATCH("activity_checklist")
    @Headers("Prefer: return=representation")
    suspend fun updateChecklist(
        @Query("appointment_id") appointmentId: String,
        @Query("master_id")      masterId:      String,
        @Body updates: @JvmSuppressWildcards Map<String, Any>
    ): Response<List<ChecklistInsertDto>>

    @GET("project_sales_member")
    suspend fun getMyProjectIds(
        @Query("user_id") userId: String,
        @Query("select") select: String = "project_id,project_number",
        @Query("limit") limit: Int = 1000
    ): Response<List<ProjectMemberDto>>

    // ── Call Logs ─────────────────────────────────────────────────
    @POST("call_logs")
    @Headers("Prefer: return=representation")
    suspend fun insertCallLog(
        @Body log: Map<String, String>
    ): Response<List<Map<String, String>>>

    // ── Activity Result ───────────────────────────────────────────
    @POST("activity_result")
    @Headers("Prefer: return=representation")
    suspend fun insertActivityResult(
        @Body result: ActivityResult
    ): Response<List<ActivityResult>>

    @PATCH("activity_result")
    @Headers("Prefer: return=representation")
    suspend fun updateActivityResult(
        @Query("appointment_id") appointmentId: String,
        @Body result: ActivityResult
    ): Response<List<ActivityResult>>

    @GET("activity_result")
    suspend fun getActivityResult(
        @Query("appointment_id") appointmentId: String,
        @Query("limit") limit: Int = 1
    ): Response<List<ActivityResult>>
}


// ── DTOs ─────────────────────────────────────────────────────
data class ProductMasterDto(
    @SerializedName("product_id")       val productId:       String,
    @SerializedName("product_group")    val productGroup:    String?,
    @SerializedName("product_type")     val productType:     String?,
    @SerializedName("product_subgroup") val productSubgroup: String?,
    @SerializedName("product_brand")    val brand:           String?,
    @SerializedName("unit")             val unit:            String?
)

data class ProjectContactResponse(
    @SerializedName("contact_id") val contactId: String,
    @SerializedName("contact_person") val contactPerson: ContactPerson? = null
)
