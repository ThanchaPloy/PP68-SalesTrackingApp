package com.pp68.backend.data.repository

import com.pp68.backend.data.database.tables.EmployeeTable
import com.pp68.backend.domain.entity.Employee
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

class EmployeeRepositoryImpl {

    private fun ResultRow.toEmployee() = Employee(
        empCode     = this[EmployeeTable.empCode],
        empName     = this[EmployeeTable.empName],
        empPostCode = this[EmployeeTable.empPostCode],
        empPost     = this[EmployeeTable.empPost],
        empBrchCode = this[EmployeeTable.empBrchCode],
        empBrchName = this[EmployeeTable.empBrchName],
        stat        = this[EmployeeTable.stat],
        createDate  = this[EmployeeTable.createDate]?.toString(),
        updatedAt   = this[EmployeeTable.updatedAt]?.toString(),
        empType     = this[EmployeeTable.empType],
        password    = this[EmployeeTable.password]
    )

    suspend fun findByCode(empCode: String): Employee? = dbQuery {
        EmployeeTable.select { EmployeeTable.empCode eq empCode }.singleOrNull()?.toEmployee()
    }

    suspend fun findByCodes(empCodes: List<String>): List<Employee> = dbQuery {
        EmployeeTable.select { EmployeeTable.empCode inList empCodes }.map { it.toEmployee() }
    }

    suspend fun findByBranch(brchCode: String): List<Employee> = dbQuery {
        EmployeeTable.select {
            (EmployeeTable.empBrchCode eq brchCode) and (EmployeeTable.stat eq "1")
        }.map { it.toEmployee() }
    }

    suspend fun updatePassword(empCode: String, passwordHash: String): Employee? = dbQuery {
        EmployeeTable.update({ EmployeeTable.empCode eq empCode }) { it[password] = passwordHash }
        EmployeeTable.select { EmployeeTable.empCode eq empCode }.singleOrNull()?.toEmployee()
    }
}
