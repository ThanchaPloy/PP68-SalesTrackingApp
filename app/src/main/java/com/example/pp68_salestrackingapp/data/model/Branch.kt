package com.example.pp68_salestrackingapp.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = "branch")
data class Branch(
    @PrimaryKey
    @ColumnInfo(name = "branch_id")
    @SerializedName("branch_id")
    val branchId: String,

    @ColumnInfo(name = "branch_name")
    @SerializedName("branch_name")
    val branchName: String,

    @ColumnInfo(name = "region")
    @SerializedName("region")
    val region: String? = null
)