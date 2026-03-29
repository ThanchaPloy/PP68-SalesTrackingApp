package com.example.pp68_salestrackingapp.data.model

data class PlanItemDto(
    val masterId: Int,
    val masterDetails: MasterActDto? = null,
    val isDone: Boolean = false
)

data class MasterActDto(
    val actName: String
)
