package com.example.pp68_salestrackingapp.data.model

data class LossReasonMaster(
    val code: String,
    val label: String = code,
    val sequence: Int = 0
)
