package com.pp68.backend.domain.repository

import com.pp68.backend.domain.entity.ActivityMaster

interface ActivityMasterRepository {
    suspend fun findActive(): List<ActivityMaster>
    suspend fun findAll(): List<ActivityMaster>
}