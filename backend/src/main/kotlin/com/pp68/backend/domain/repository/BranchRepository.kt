package com.pp68.backend.domain.repository

import com.pp68.backend.domain.entity.Branch

interface BranchRepository {
    suspend fun findAll(): List<Branch>
    suspend fun findById(branchId: String): Branch?
}