package com.pp68.backend.application.routes

import com.pp68.backend.data.repository.ProductRepositoryImpl
import com.pp68.backend.domain.entity.Product
import com.pp68.backend.domain.entity.ProjectProduct
import com.pp68.backend.domain.exception.NotFoundException
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.productRoutes() {
    val productRepo: ProductRepositoryImpl by inject()

    authenticate("jwt-auth") {

        route("/products") {
            get { call.respond<List<Product>>(productRepo.findAllProducts()) }
        }

        route("/project_product") {
            get {
                val projectId = call.request.queryParameters["project_id"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest)
                call.respond<List<ProjectProduct>>(productRepo.findProjectProducts(projectId))
            }
            post {
                val item = call.receive<ProjectProduct>()
                call.respond<List<ProjectProduct>>(HttpStatusCode.Created, listOf(productRepo.addProductToProject(item)))
            }
            patch {
                val projectId = call.request.queryParameters["project_id"]
                    ?: return@patch call.respond(HttpStatusCode.BadRequest)
                val productId = call.request.queryParameters["product_id"]
                    ?: return@patch call.respond(HttpStatusCode.BadRequest)
                val updates = call.receive<Map<String, String?>>()
                call.respond<List<ProjectProduct>>(listOf(productRepo.updateProjectProduct(projectId, productId, updates) ?: throw NotFoundException("Project product not found: $projectId / $productId")))
            }
            delete {
                val projectId = call.request.queryParameters["project_id"]
                    ?: return@delete call.respond(HttpStatusCode.BadRequest)
                val productId = call.request.queryParameters["product_id"]
                    ?: return@delete call.respond(HttpStatusCode.BadRequest)
                if (!productRepo.deleteProjectProduct(projectId, productId)) throw NotFoundException("Project product not found: $projectId / $productId")
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}