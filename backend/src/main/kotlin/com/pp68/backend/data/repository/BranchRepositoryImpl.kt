package com.pp68.backend.data.repository

import com.pp68.backend.data.database.tables.BranchTable
import com.pp68.backend.domain.entity.Branch
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.select

class BranchRepositoryImpl {

    private fun ResultRow.toBranch() = Branch(
        branchCode = this[BranchTable.branchCode],
        name       = this[BranchTable.name],
        region     = this[BranchTable.region]
    )

    suspend fun findAll(): List<Branch> = dbQuery {
        BranchTable.selectAll().map { it.toBranch() }
    }

    suspend fun findById(branchId: String): Branch? = dbQuery {
        BranchTable.select { BranchTable.branchCode eq branchId }.singleOrNull()?.toBranch()
    }
}
