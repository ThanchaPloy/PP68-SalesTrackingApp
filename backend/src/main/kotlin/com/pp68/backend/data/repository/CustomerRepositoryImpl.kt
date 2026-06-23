package com.pp68.backend.data.repository

import com.pp68.backend.data.database.tables.CustomerTable
import com.pp68.backend.domain.entity.Customer
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

class CustomerRepositoryImpl {

    private fun ResultRow.toCustomer() = Customer(
        customerCode       = this[CustomerTable.customerCode],
        customerName       = this[CustomerTable.customerName],
        createBy           = this[CustomerTable.createBy],
        address            = this[CustomerTable.address],
        salespersonCode    = this[CustomerTable.salespersonCode],
        createDate         = this[CustomerTable.createDate],
        genBusPostingGroup = this[CustomerTable.genBusPostingGroup],
        customerStatus     = this[CustomerTable.customerStatus],
        grade              = this[CustomerTable.grade],
        updatedAt          = this[CustomerTable.updatedAt]?.toString()
    )

    suspend fun findAll(limit: Int): List<Customer> = dbQuery {
        CustomerTable.selectAll().limit(limit).map { it.toCustomer() }
    }

    suspend fun findById(customerCode: String): Customer? = dbQuery {
        CustomerTable.select { CustomerTable.customerCode eq customerCode }.singleOrNull()?.toCustomer()
    }

    suspend fun findByIds(customerCodes: List<String>): List<Customer> = dbQuery {
        CustomerTable.select { CustomerTable.customerCode inList customerCodes }.map { it.toCustomer() }
    }

    suspend fun findBySalesperson(salespersonCode: String): List<Customer> = dbQuery {
        CustomerTable.select { CustomerTable.salespersonCode eq salespersonCode }.map { it.toCustomer() }
    }

    suspend fun create(customer: Customer): Customer = dbQuery {
        CustomerTable.insert {
            it[customerCode]       = customer.customerCode
            it[customerName]       = customer.customerName
            it[createBy]           = customer.createBy
            it[address]            = customer.address
            it[salespersonCode]    = customer.salespersonCode
            it[createDate]         = customer.createDate
            it[genBusPostingGroup] = customer.genBusPostingGroup
            it[customerStatus]     = customer.customerStatus
            it[grade]              = customer.grade
        }
        CustomerTable.select { CustomerTable.customerCode eq customer.customerCode }.single().toCustomer()
    }

    suspend fun update(customerCode: String, updates: Map<String, Any?>): Customer? = dbQuery {
        CustomerTable.update({ CustomerTable.customerCode eq customerCode }) { stmt ->
            updates["customer_name"]?.let        { v -> stmt[customerName]       = v as String }
            updates["address"]?.let              { v -> stmt[address]            = v as String }
            updates["salesperson_code"]?.let     { v -> stmt[salespersonCode]    = v as String }
            updates["customer_status"]?.let      { v -> stmt[customerStatus]     = (v as Number).toInt() }
            updates["grade"]?.let                { v -> stmt[grade]              = (v as Number).toInt() }
            updates["gen_bus_posting_group"]?.let { v -> stmt[genBusPostingGroup] = v as String }
        }
        CustomerTable.select { CustomerTable.customerCode eq customerCode }.singleOrNull()?.toCustomer()
    }

    suspend fun delete(customerCode: String): Boolean = dbQuery {
        CustomerTable.deleteWhere { CustomerTable.customerCode eq customerCode } > 0
    }
}
