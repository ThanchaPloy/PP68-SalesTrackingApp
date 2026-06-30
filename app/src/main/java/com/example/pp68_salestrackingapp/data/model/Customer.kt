package com.example.pp68_salestrackingapp.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = "customer")
data class Customer(
    @PrimaryKey
    @ColumnInfo(name = "cust_id")
    @SerializedName("customer_code") val custId: String,

    @ColumnInfo(name = "company_name")
    @SerializedName("customer_name") val companyName: String,

    @ColumnInfo(name = "branch_id")
    @SerializedName("gen_bus_posting_group") val branchId: String? = null,

    @ColumnInfo(name = "branch")
    @SerializedName("branch") val branch: String? = null,

    @ColumnInfo(name = "vat_registration_no")
    @SerializedName("vat_registration_no") val vatRegistrationNo: String? = null,

    @ColumnInfo(name = "cust_type")
    @SerializedName("cust_type") val custType: String? = null,

    @ColumnInfo(name = "company_addr")
    @SerializedName("address") val companyAddr: String? = null,

    @ColumnInfo(name = "company_lat")
    @SerializedName("company_lat") val companyLat: Double? = null,

    @ColumnInfo(name = "company_long")
    @SerializedName("company_long") val companyLong: Double? = null,

    @ColumnInfo(name = "company_status")
    @SerializedName("customer_status") val companyStatus: Int? = null,

    @ColumnInfo(name = "created_at")
    @SerializedName("create_date") val createdAt: String? = null,

    @ColumnInfo(name = "user_id")
    @SerializedName("salesperson_code") val createdBy: String? = null,

    @ColumnInfo(name = "grade")
    val grade: Int? = null,

    @ColumnInfo(name = "is_synced")
    val isSynced: Boolean = true
)
