package com.pp68.backend.domain.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ContactPerson(
    @SerialName("contact_id")    val contactId:    Long    = 0,
    @SerialName("customer_code") val customerCode: String,
    @SerialName("contact_name")  val contactName:  String? = null,
    @SerialName("phone")         val phone:        String? = null,
    @SerialName("mobile_phone")  val mobilePhone:  String? = null,
    @SerialName("email")         val email:        String? = null,
    @SerialName("fax")           val fax:          String? = null,
    @SerialName("telex_no")      val telexNo:      String? = null,
    @SerialName("is_primary")    val isPrimary:    Boolean = true,
    @SerialName("created_at")    val createdAt:    String? = null,
    @SerialName("updated_at")    val updatedAt:    String? = null
)
