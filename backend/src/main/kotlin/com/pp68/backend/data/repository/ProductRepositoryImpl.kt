package com.pp68.backend.data.repository

import com.pp68.backend.data.database.tables.ItemSilverTable
import com.pp68.backend.data.database.tables.ProjectProductTable
import com.pp68.backend.domain.entity.Product
import com.pp68.backend.domain.entity.ProjectProduct
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

class ProductRepositoryImpl {

    suspend fun findAllProducts(): List<Product> = dbQuery {
        ItemSilverTable.selectAll().map {
            Product(
                productId      = it[ItemSilverTable.itemNo],
                description    = it[ItemSilverTable.description],
                productBrandNo = it[ItemSilverTable.productBrandNo],
                productGroupNo = it[ItemSilverTable.productGroupNo],
                unit           = it[ItemSilverTable.baseUnitOfMeasure],
                brandName      = it[ItemSilverTable.brandName],
                groupName      = it[ItemSilverTable.groupName],
                colorName      = it[ItemSilverTable.colorName]
            )
        }
    }

    suspend fun findProjectProducts(projectId: String): List<ProjectProduct> = dbQuery {
        ProjectProductTable.select { ProjectProductTable.projectId eq projectId }.map {
            ProjectProduct(
                projectId        = it[ProjectProductTable.projectId],
                productId        = it[ProjectProductTable.productId],
                quantity         = it[ProjectProductTable.quantity],
                desiredDate      = it[ProjectProductTable.desiredDate],
                shippingBranchId = it[ProjectProductTable.shippingBranchId]
            )
        }
    }

    suspend fun addProductToProject(item: ProjectProduct): ProjectProduct = dbQuery {
        ProjectProductTable.insert {
            it[projectId]        = item.projectId
            it[productId]        = item.productId
            it[quantity]         = item.quantity
            it[desiredDate]      = item.desiredDate
            it[shippingBranchId] = item.shippingBranchId
        }
        item
    }

    suspend fun updateProjectProduct(projectId: String, productId: String, updates: Map<String, Any?>): ProjectProduct? = dbQuery {
        ProjectProductTable.update({
            (ProjectProductTable.projectId eq projectId) and (ProjectProductTable.productId eq productId)
        }) { stmt ->
            updates["quantity"]?.let           { v -> stmt[ProjectProductTable.quantity]         = (v as Number).toDouble() }
            updates["desired_date"]?.let       { v -> stmt[ProjectProductTable.desiredDate]      = v as String }
            updates["shipping_branch_id"]?.let { v -> stmt[ProjectProductTable.shippingBranchId] = v as String }
        }
        ProjectProductTable.select {
            (ProjectProductTable.projectId eq projectId) and (ProjectProductTable.productId eq productId)
        }.singleOrNull()?.let {
            ProjectProduct(
                projectId        = it[ProjectProductTable.projectId],
                productId        = it[ProjectProductTable.productId],
                quantity         = it[ProjectProductTable.quantity],
                desiredDate      = it[ProjectProductTable.desiredDate],
                shippingBranchId = it[ProjectProductTable.shippingBranchId]
            )
        }
    }

    suspend fun deleteProjectProduct(projectId: String, productId: String): Boolean = dbQuery {
        ProjectProductTable.deleteWhere {
            (ProjectProductTable.projectId eq projectId) and (ProjectProductTable.productId eq productId)
        } > 0
    }
}
