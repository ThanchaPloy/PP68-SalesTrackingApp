package com.pp68.backend.domain.usecase

import at.favre.lib.crypto.bcrypt.BCrypt
import com.pp68.backend.domain.entity.Employee
import com.pp68.backend.domain.exception.ForbiddenException
import com.pp68.backend.domain.exception.NotFoundException
import com.pp68.backend.domain.exception.UnauthorizedException
import com.pp68.backend.data.repository.EmployeeRepositoryImpl

class AuthUseCase(private val employeeRepository: EmployeeRepositoryImpl) {

    private fun verifyPassword(plain: String, stored: String?): Boolean {
        if (stored == null) return false
        return if (stored.startsWith("\$2")) {
            BCrypt.verifyer().verify(plain.toCharArray(), stored).verified
        } else {
            plain == stored
        }
    }

    suspend fun login(empCode: String, password: String): Employee {
        val employee = employeeRepository.findByCode(empCode)
            ?: throw UnauthorizedException("Invalid credentials")
        if (!verifyPassword(password, employee.password)) throw UnauthorizedException("Invalid credentials")
        if (employee.stat != null && employee.stat != "1")
            throw ForbiddenException("Account is inactive")
        return employee
    }

    suspend fun changePassword(empCode: String, oldPassword: String, newPassword: String) {
        val employee = employeeRepository.findByCode(empCode)
            ?: throw NotFoundException("Employee not found")
        if (!verifyPassword(oldPassword, employee.password)) throw UnauthorizedException("Wrong current password")
        val hashed = BCrypt.withDefaults().hashToString(12, newPassword.toCharArray())
        employeeRepository.updatePassword(empCode, hashed)
    }
}
