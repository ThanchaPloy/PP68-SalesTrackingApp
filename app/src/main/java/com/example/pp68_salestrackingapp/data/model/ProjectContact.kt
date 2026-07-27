package com.example.pp68_salestrackingapp.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.google.gson.annotations.SerializedName

@Entity(
    tableName = "project_contact",
    primaryKeys = ["project_id", "contact_id"],
    foreignKeys = [
        ForeignKey(
            entity = Project::class,
            parentColumns = ["projectId"],
            childColumns = ["project_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ContactPerson::class,
            parentColumns = ["contactId"],
            childColumns = ["contact_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("project_id"), Index("contact_id")]
)
data class ProjectContact(
    @SerializedName("project_id")
    val project_id: String,
    
    @SerializedName("contact_id")
    val contact_id: String
)
