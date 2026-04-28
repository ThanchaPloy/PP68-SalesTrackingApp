package com.example.pp68_salestrackingapp.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = "contact_person")
data class ContactPerson(
    @PrimaryKey
    @ColumnInfo(name = "contactId")
    @SerializedName("contact_id")
    val contactId: String,

    @ColumnInfo(name = "custId")
    @SerializedName("cust_id")
    val custId: String,

    @ColumnInfo(name = "userId")
    @SerializedName("user_id")
    val userId: String? = null, // ✅ ใส่กลับตาม CSV

    @ColumnInfo(name = "fullName")
    @SerializedName("full_name")
    val fullName: String? = null,

    @ColumnInfo(name = "nickname")
    @SerializedName("nickname")
    val nickname: String? = null,

    @ColumnInfo(name = "position")
    @SerializedName("position")
    val position: String? = null,

    @ColumnInfo(name = "phone_number")
    @SerializedName("phone_number")
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
    val isDmConfirmed: Boolean? = false
)