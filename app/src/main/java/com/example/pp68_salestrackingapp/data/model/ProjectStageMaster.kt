package com.example.pp68_salestrackingapp.data.model

data class ProjectStageMaster(
    val code: String,
    val label: String = code,
    val sequence: Int = 0,
    val probabilityPct: Int = 0,
    val isClosed: Boolean = false,
    val isWon: Boolean = false
)
