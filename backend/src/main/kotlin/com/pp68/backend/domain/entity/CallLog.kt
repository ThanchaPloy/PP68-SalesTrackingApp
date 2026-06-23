package com.pp68.backend.domain.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CallLog(
    @SerialName("log_id")       val logId:       String  = "",
    @SerialName("user_id")      val userId:      String,
    @SerialName("cust_id")      val custId:      String? = null,
    @SerialName("phone_number") val phoneNumber: String? = null,
    @SerialName("start_time")   val startTime:   String? = null,
    @SerialName("end_time")     val endTime:     String? = null,
    @SerialName("duration")     val duration:    Int?    = null,
    @SerialName("is_sync")      val isSync:      Boolean = false
)
