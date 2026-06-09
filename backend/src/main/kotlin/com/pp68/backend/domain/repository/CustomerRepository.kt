package com.pp68.backend.domain.repository

import com.pp68.backend.domain.entity.Customer

interface CustomerRepository {
    suspend fun findAll(limit: Int = 1000): List<Customer>
    suspend fun findById(custId: String): Customer?
    suspend fun findByIds(custIds: List<String>): List<Customer>
    suspend fun findByBranch(branchId: String): List<Customer>
    suspend fun create(customer: Customer): Customer
    suspend fun update(custId: String, updates: Map<String, Any?>): Customer?
    suspend fun delete(custId: String): Boolean
}