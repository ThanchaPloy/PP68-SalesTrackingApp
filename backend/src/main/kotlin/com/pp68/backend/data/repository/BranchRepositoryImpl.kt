package com.pp68.backend.data.repository

import com.pp68.backend.data.database.tables.BranchTable
import com.pp68.backend.domain.entity.Branch
import com.pp68.backend.domain.repository.BranchRepository
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class BranchRepositoryImpl : BranchRepository {

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }

    private fun ResultRow.toBranch() = Branch(
        branchId   = this[BranchTable.branchId],
        branchName = this[BranchTable.branchName]
    )

    override suspend fun findAll(): List<Branch> = dbQuery {
        BranchTable.selectAll().map { it.toBranch() }
    }

    override suspend fun findById(branchId: String): Branch? = dbQuery {
        BranchTable.select { BranchTable.branchId eq branchId }.singleOrNull()?.toBranch()
    }
}