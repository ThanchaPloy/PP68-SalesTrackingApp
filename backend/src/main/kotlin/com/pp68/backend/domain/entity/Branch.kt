package com.pp68.backend.domain.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Branch(
    @SerialName("branch_code") val branchCode: String,
    @SerialName("name")        val name:       String,
    @SerialName("region")      val region:     String? = null
)
