package com.example.pp68_salestrackingapp.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import com.google.gson.annotations.SerializedName

@Entity(
    tableName = "project_sales_member",
    primaryKeys = ["project_code", "emp_code"]
)
data class ProjectSalesMember(
    @ColumnInfo(name = "project_code")
    @SerializedName("project_code")
    val projectId: String,

    @ColumnInfo(name = "emp_code")
    @SerializedName("emp_code")
    val empCode: String,

    @ColumnInfo(name = "sales_role")
    @SerializedName("sales_role")
    val saleRole: String = "support"
)
