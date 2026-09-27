package com.example.pp68_salestrackingapp.data.model

data class DealFactorOption(
    val code: String,
    val label: String = code,
    val sequence: Int = 0
)

data class DealFactorQuestion(
    val questionKey: String,
    val label: String = questionKey,
    val sequence: Int = 0,
    val defaultCode: String,
    val options: List<DealFactorOption> = emptyList()
)
