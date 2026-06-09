package com.pp68.backend.application.routes

import com.pp68.backend.domain.entity.ProjectProduct
import com.pp68.backend.domain.repository.ProductRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.productRoutes() {
    val repo: ProductRepository by inject()

    authenticate("jwt-auth") {

        route("/products") {
            get { call.respond(repo.findAllProducts()) }
        }

        route("/project_product") {
            get {
                val projectId = call.request.queryParameters["project_id"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest)
                call.respond(repo.findProjectProducts(projectId))
            }
            post {
                val item = call.receive<ProjectProduct>()
                call.respond(HttpStatusCode.Created, listOf(repo.addProductToProject(item)))
            }
            patch {
                val projectId = call.request.queryParameters["project_id"]
                    ?: return@patch call.respond(HttpStatusCode.BadRequest)
                val productId = call.request.queryParameters["product_id"]
                    ?: return@patch call.respond(HttpStatusCode.BadRequest)
                val updates = call.receive<Map<String, String?>>()
                val updated = repo.updateProjectProduct(projectId, productId, updates)
                    ?: return@patch call.respond(HttpStatusCode.NotFound)
                call.respond(listOf(updated))
            }
            delete {
                val projectId = call.request.queryParameters["project_id"]
                    ?: return@delete call.respond(HttpStatusCode.BadRequest)
                val productId = call.request.queryParameters["product_id"]
                    ?: return@delete call.respond(HttpStatusCode.BadRequest)
                if (repo.deleteProjectProduct(projectId, productId)) call.respond(HttpStatusCode.NoContent)
                else call.respond(HttpStatusCode.NotFound)
            }
        }
    }
}