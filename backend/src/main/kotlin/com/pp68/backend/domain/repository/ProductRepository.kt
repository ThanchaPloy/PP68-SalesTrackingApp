package com.pp68.backend.domain.repository

import com.pp68.backend.domain.entity.Product
import com.pp68.backend.domain.entity.ProjectProduct

interface ProductRepository {
    suspend fun findAllProducts(): List<Product>
    suspend fun findProjectProducts(projectId: String): List<ProjectProduct>
    suspend fun addProductToProject(item: ProjectProduct): ProjectProduct
    suspend fun updateProjectProduct(projectId: String, productId: String, updates: Map<String, Any?>): ProjectProduct?
    suspend fun deleteProjectProduct(projectId: String, productId: String): Boolean
}