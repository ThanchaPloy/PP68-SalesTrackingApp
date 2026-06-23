package com.example.pp68_salestrackingapp.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName

@Entity(tableName = "contact_person")
data class ContactPerson(
    @PrimaryKey
    @ColumnInfo(name = "contactId")
    @JsonAdapter(IntOrStringTypeAdapter::class)
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
    val isSynced: Boolean = true
)
