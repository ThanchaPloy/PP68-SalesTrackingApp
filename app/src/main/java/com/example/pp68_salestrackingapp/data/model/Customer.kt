package com.example.pp68_salestrackingapp.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = "customer")
data class Customer(
    @PrimaryKey
    @SerializedName("cust_id") val custId: String,
    
    @SerializedName("company_name") val companyName: String,
    
    @SerializedName("branch_id") val branchId: String? = null,
    
    @SerializedName("branch") val branch: String?,
    
    @SerializedName("cust_type") val custType: String?,
    
    @SerializedName("company_addr") val companyAddr: String?,
    
    @SerializedName("company_lat") val companyLat: Double?,
    
    @SerializedName("company_long") val companyLong: Double?,
    
    @SerializedName("company_status") val companyStatus: String?,
    
    @SerializedName("created_at") val createdAt: String?,
    
    @ColumnInfo(name = "user_id")
    @SerializedName("user_id") 
    val createdBy: String? = null
)