package com.example.pp68_salestrackingapp.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(
    tableName = "contact_person",
    indices = [
        Index(value = ["custId"], name = "index_contact_customer_id"),
        Index(value = ["is_synced"], name = "index_contact_is_synced")
    ]
)
data class ContactPerson(
    @PrimaryKey
    @ColumnInfo(name = "contactId")
    @SerializedName("contact_id")
    val contactId: String,

    @ColumnInfo(name = "custId")
    @SerializedName("customer_code")
    val custId: String,

    @ColumnInfo(name = "fullName")
    @SerializedName("contact_name")
    val fullName: String? = null,

    @ColumnInfo(name = "nickname")
    @SerializedName("nickname")
    val nickname: String? = null,

    @ColumnInfo(name = "position")
    @SerializedName("position")
    val position: String? = null,

    @ColumnInfo(name = "phoneNumber")
    @SerializedName("mobile_phone")
    val phoneNumber: String? = null,

    @ColumnInfo(name = "email")
    @SerializedName("email")
    val email: String? = null,

    @ColumnInfo(name = "line")
    @SerializedName("line")
    val line: String? = null,

    @ColumnInfo(name = "isActive")
    @SerializedName("is_active")
    val isActive: Boolean? = true,

    @ColumnInfo(name = "isDmConfirmed")
    @SerializedName("is_dm_confirmed")
    val isDmConfirmed: Boolean? = false,

    @ColumnInfo(name = "createdBy")
    @SerializedName("created_by")
    val createdBy: String? = null,

    @ColumnInfo(name = "is_synced")
    val isSynced: Boolean = true,

    // Keep this new optional field last so existing positional Kotlin callers remain compatible.
    // It is only an offline display snapshot, not a cached ERP customer record.
    @ColumnInfo(name = "customer_name")
    @SerializedName("customer_name")
    val customerName: String? = null,

    // เวลา/ผู้แก้ไขล่าสุด — อ่านอย่างเดียว เซิร์ฟเวอร์ตั้งค่าเองเสมอ (ดู MIGRATION_ROADMAP.md เฟส 1)
    // ต่อท้ายตามคอนเวนชันเดียวกับ customerName ด้านบน เพื่อไม่ให้ positional caller เลื่อน
    @ColumnInfo(name = "updated_at")
    @SerializedName("updated_at")
    val updatedAt: String? = null,

    @ColumnInfo(name = "updated_by")
    @SerializedName("updated_by")
    val updatedBy: String? = null
)
