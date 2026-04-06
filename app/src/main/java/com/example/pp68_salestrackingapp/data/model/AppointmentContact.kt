package com.example.pp68_salestrackingapp.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.google.gson.annotations.SerializedName

@Entity(
    tableName = "appointment_contact",
    primaryKeys = ["appointment_id", "contact_id"],
    foreignKeys = [
        ForeignKey(
            entity = SalesActivity::class,
            parentColumns = ["appointment_id"],
            childColumns = ["appointment_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ContactPerson::class,
            parentColumns = ["contactId"],
            childColumns = ["contact_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("appointment_id"), Index("contact_id")]
)
data class AppointmentContact(
    @ColumnInfo(name = "appointment_id")
    @SerializedName("appointment_id")
    val appointmentId: String,
    
    @ColumnInfo(name = "contact_id")
    @SerializedName("contact_id")
    val contactId: String
)
