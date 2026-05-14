package com.example.pp68_salestrackingapp.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = "project")
data class Project(
    @PrimaryKey
    @ColumnInfo(name = "projectId")
    @SerializedName("project_id")
    val projectId: String,

    @ColumnInfo(name = "custId")
    @SerializedName("cust_id")
    val custId: String,

    @ColumnInfo(name = "branchId")
    @SerializedName("branch_id")
    val branchId: String? = null,

    @ColumnInfo(name = "billingBranchId")
    @SerializedName("billing_branch_id")
    val billingBranchId: String? = null,

    @ColumnInfo(name = "projectName")
    @SerializedName("project_name")
    val projectName: String,

    @ColumnInfo(name = "expectedValue")
    @SerializedName("expected_value")
    val expectedValue: Double? = null,

    @ColumnInfo(name = "projectStatus")
    @SerializedName("project_status")
    val projectStatus: String? = null,

    @ColumnInfo(name = "startDate")
    @SerializedName("start_date")
    val startDate: String? = null,

    @ColumnInfo(name = "closingDate")
    @SerializedName("closing_date")
    val closingDate: String? = null,

    @ColumnInfo(name = "desiredCompletionDate")
    @SerializedName("desired_completion_date")
    val desiredCompletionDate: String? = null,

    @ColumnInfo(name = "projectLat")
    @SerializedName("project_lat")
    val projectLat: Double? = null,

    @ColumnInfo(name = "projectLong")
    @SerializedName("project_long")
    val projectLong: Double? = null,

    @ColumnInfo(name = "opportunityScore")
    @SerializedName("opportunity_score")
    val opportunityScore: String? = null,

    @ColumnInfo(name = "progressPct")
    @SerializedName("progress_pct")
    val progressPct: Int? = null,

    @ColumnInfo(name = "createdAt")
    @SerializedName("created_at")
    val createdAt: String? = null,

    @ColumnInfo(name = "lossReason")
    @SerializedName("loss_reason")
    val lossReason: String? = null
)
