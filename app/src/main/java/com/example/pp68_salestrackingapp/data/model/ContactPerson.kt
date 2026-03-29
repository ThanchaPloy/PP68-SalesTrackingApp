package com.example.pp68_salestrackingapp.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = "contact_person")
data class ContactPerson(
    @PrimaryKey
    @SerializedName("contact_id") val contactId: String,

    @SerializedName("cust_id") val custId: String,
    @SerializedName("full_name") val fullName: String?,
    @SerializedName("nickname") val nickname: String?,
    @SerializedName("position") val position: String?,
    @SerializedName("phone_number") val phoneNumber: String?,
    @SerializedName("email") val email: String?,
    @SerializedName("line") val line: String?,
    @SerializedName("is_active") val isActive: Boolean? = true,
    @SerializedName("is_dm_confirmed") val isDmConfirmed: Boolean? = false
)