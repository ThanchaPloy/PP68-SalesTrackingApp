package com.example.pp68_salestrackingapp.data.model

import com.google.gson.annotations.SerializedName

data class DealFactorOptionDto(
    @SerializedName("code")     val code: String,
    @SerializedName("label")    val label: String,
    @SerializedName("sequence") val sequence: Int
)

data class DealFactorQuestionDto(
    @SerializedName("question_key") val questionKey: String,
    @SerializedName("label")        val label: String,
    @SerializedName("sequence")     val sequence: Int,
    @SerializedName("default_code") val defaultCode: String,
    @SerializedName("options")      val options: List<DealFactorOptionDto>
)
