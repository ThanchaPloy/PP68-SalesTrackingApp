package com.pp68.backend.application.routes

import com.pp68.backend.data.repository.BranchRepositoryImpl
import com.pp68.backend.domain.entity.Branch
import com.pp68.backend.domain.exception.NotFoundException
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.branchRoutes() {
    val branchRepo: BranchRepositoryImpl by inject()

    authenticate("jwt-auth") {
        route("/branch") {
            get {
                val branchId = call.request.queryParameters["branch_id"].stripEq()
                if (branchId != null) {
                    call.respond<List<Branch>>(listOf(branchRepo.findById(branchId) ?: throw NotFoundException("Branch not found: $branchId")))
                } else {
                    call.respond<List<Branch>>(branchRepo.findAll())
                }
            }
        }
    }
}