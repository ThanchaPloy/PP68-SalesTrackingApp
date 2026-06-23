package com.pp68.backend.domain.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ActivityMaster(
    @SerialName("master_id")  val masterId:  Int,
    @SerialName("category")   val category:  String,
    @SerialName("objective")  val objective: String,
    @SerialName("act_name")   val actName:   String,
    @SerialName("is_active")  val isActive:  Boolean = true
)
