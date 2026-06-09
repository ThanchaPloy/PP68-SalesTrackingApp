package com.pp68.backend.data.repository

import com.pp68.backend.data.database.tables.ProductTable
import com.pp68.backend.data.database.tables.ProjectProductTable
import com.pp68.backend.domain.entity.Product
import com.pp68.backend.domain.entity.ProjectProduct
import com.pp68.backend.domain.repository.ProductRepository
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class ProductRepositoryImpl : ProductRepository {

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }

    override suspend fun findAllProducts(): List<Product> = dbQuery {
        ProductTable.selectAll().map {
            Product(
                productId       = it[ProductTable.productId],
                productGroup    = it[ProductTable.productGroup],
                productType     = it[ProductTable.productType],
                productSubgroup = it[ProductTable.productSubgroup],
                productBrand    = it[ProductTable.productBrand],
                unit            = it[ProductTable.unit],
                color           = it[ProductTable.color],
                thickness       = it[ProductTable.thickness],
                width           = it[ProductTable.width],
                length          = it[ProductTable.length],
                dimensionUnit   = it[ProductTable.dimensionUnit]
            )
        }
    }

    override suspend fun findProjectProducts(projectId: String): List<ProjectProduct> = dbQuery {
        ProjectProductTable.select { ProjectProductTable.projectId eq projectId }.map {
            ProjectProduct(
                projectId    = it[ProjectProductTable.projectId],
                productId    = it[ProjectProductTable.productId],
                quantity     = it[ProjectProductTable.quantity],
                status       = it[ProjectProductTable.status],
                deliveryDate = it[ProjectProductTable.deliveryDate]
            )
        }
    }

    override suspend fun addProductToProject(item: ProjectProduct): ProjectProduct = dbQuery {
        ProjectProductTable.insert {
            it[projectId]    = item.projectId
            it[productId]    = item.productId
            it[quantity]     = item.quantity
            it[status]       = item.status
            it[deliveryDate] = item.deliveryDate
        }
        item
    }

    override suspend fun updateProjectProduct(projectId: String, productId: String, updates: Map<String, Any?>): ProjectProduct? = dbQuery {
        ProjectProductTable.update({
            (ProjectProductTable.projectId eq projectId) and (ProjectProductTable.productId eq productId)
        }) { stmt ->
            updates["quantity"]?.let      { v -> stmt[ProjectProductTable.quantity]     = (v as Number).toInt() }
            updates["status"]?.let        { v -> stmt[ProjectProductTable.status]       = v as String }
            updates["delivery_date"]?.let { v -> stmt[ProjectProductTable.deliveryDate] = v as String }
        }
        ProjectProductTable.select {
            (ProjectProductTable.projectId eq projectId) and (ProjectProductTable.productId eq productId)
        }.singleOrNull()?.let {
            ProjectProduct(
                projectId    = it[ProjectProductTable.projectId],
                productId    = it[ProjectProductTable.productId],
                quantity     = it[ProjectProductTable.quantity],
                status       = it[ProjectProductTable.status],
                deliveryDate = it[ProjectProductTable.deliveryDate]
            )
        }
    }

    override suspend fun deleteProjectProduct(projectId: String, productId: String): Boolean = dbQuery {
        ProjectProductTable.deleteWhere {
            (ProjectProductTable.projectId eq projectId) and (ProjectProductTable.productId eq productId)
        } > 0
    }
}