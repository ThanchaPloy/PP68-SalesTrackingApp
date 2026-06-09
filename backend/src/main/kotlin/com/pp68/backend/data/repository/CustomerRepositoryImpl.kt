package com.pp68.backend.data.repository

import com.pp68.backend.data.database.tables.CustomerTable
import com.pp68.backend.domain.entity.Customer
import com.pp68.backend.domain.repository.CustomerRepository
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant

class CustomerRepositoryImpl : CustomerRepository {

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }

    private fun ResultRow.toCustomer() = Customer(
        custId        = this[CustomerTable.custId],
        companyName   = this[CustomerTable.companyName],
        branchId      = this[CustomerTable.branchId],
        branch        = this[CustomerTable.branch],
        custType      = this[CustomerTable.custType],
        companyAddr   = this[CustomerTable.companyAddr],
        companyLat    = this[CustomerTable.companyLat],
        companyLong   = this[CustomerTable.companyLong],
        companyStatus = this[CustomerTable.companyStatus],
        createdAt     = this[CustomerTable.createdAt]?.toString(),
        userId        = this[CustomerTable.userId]
    )

    override suspend fun findAll(limit: Int): List<Customer> = dbQuery {
        CustomerTable.selectAll().limit(limit).map { it.toCustomer() }
    }

    override suspend fun findById(custId: String): Customer? = dbQuery {
        CustomerTable.select { CustomerTable.custId eq custId }.singleOrNull()?.toCustomer()
    }

    override suspend fun findByIds(custIds: List<String>): List<Customer> = dbQuery {
        CustomerTable.select { CustomerTable.custId inList custIds }.map { it.toCustomer() }
    }

    override suspend fun findByBranch(branchId: String): List<Customer> = dbQuery {
        CustomerTable.select { CustomerTable.branchId eq branchId }.map { it.toCustomer() }
    }

    override suspend fun create(customer: Customer): Customer = dbQuery {
        CustomerTable.insert {
            it[custId]        = customer.custId
            it[companyName]   = customer.companyName
            it[branchId]      = customer.branchId
            it[branch]        = customer.branch
            it[custType]      = customer.custType
            it[companyAddr]   = customer.companyAddr
            it[companyLat]    = customer.companyLat
            it[companyLong]   = customer.companyLong
            it[companyStatus] = customer.companyStatus
            it[userId]        = customer.userId
            it[createdAt]     = Instant.now()
        }
        CustomerTable.select { CustomerTable.custId eq customer.custId }.single().toCustomer()
    }

    override suspend fun update(custId: String, updates: Map<String, Any?>): Customer? = dbQuery {
        CustomerTable.update({ CustomerTable.custId eq custId }) { stmt ->
            updates["company_name"]?.let   { v -> stmt[companyName]   = v as String }
            updates["branch_id"]?.let      { v -> stmt[branchId]      = v as String }
            updates["cust_type"]?.let      { v -> stmt[custType]      = v as String }
            updates["company_addr"]?.let   { v -> stmt[companyAddr]   = v as String }
            updates["company_lat"]?.let    { v -> stmt[companyLat]    = (v as Number).toDouble() }
            updates["company_long"]?.let   { v -> stmt[companyLong]   = (v as Number).toDouble() }
            updates["company_status"]?.let { v -> stmt[companyStatus] = v as String }
        }
        CustomerTable.select { CustomerTable.custId eq custId }.singleOrNull()?.toCustomer()
    }

    override suspend fun delete(custId: String): Boolean = dbQuery {
        CustomerTable.deleteWhere { CustomerTable.custId eq custId } > 0
    }
}