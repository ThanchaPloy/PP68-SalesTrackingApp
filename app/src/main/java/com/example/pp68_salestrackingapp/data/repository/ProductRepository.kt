package com.example.pp68_salestrackingapp.data.repository

import com.example.pp68_salestrackingapp.data.remote.ApiService
import com.example.pp68_salestrackingapp.data.model.ProjectProductInsertDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class ProductSimpleDto(
    val productId: String,
    val productName: String,
    val brand: String,
    val category: String?,
    val subCategory: String?,
    val unit: String?,
    val color: String? = null,
    val thickness: String? = null,
    val width: String? = null,
    val length: String? = null,
    val dimensionUnit: String? = null
)

@Singleton
class ProductRepository @Inject constructor(
    private val apiService: ApiService
) {

    suspend fun getAllProducts(): Result<List<ProductSimpleDto>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getProductMaster()
                if (response.isSuccessful && response.body() != null) {
                    val list = response.body()!!.map {
                        ProductSimpleDto(
                            productId   = it.productId,
                            productName = it.productGroup ?: it.productId,
                            brand       = it.brand?.trim()?.ifBlank { "ไม่ระบุแบรนด์" } ?: "ไม่ระบุแบรนด์",
                            category    = it.productType ?: "ทั่วไป",
                            subCategory = it.productSubgroup ?: "",
                            unit        = it.unit ?: "ชิ้น",
                            color       = it.color,
                            thickness   = it.thickness,
                            width       = it.width,
                            length      = it.length,
                            dimensionUnit = it.dimensionUnit
                        )
                    }
                    Result.success(list)
                } else {
                    val errorBody = response.errorBody()?.string() ?: ""
                    Result.failure(Exception("HTTP ${response.code()}: $errorBody"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun addProductToProject(
        projectId: String,
        productId: String,
        quantity: Double,
        wantedDate: String?,
        shippingBranchId: String?
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val body = ProjectProductInsertDto(
                    projectId = projectId,
                    productId = productId,
                    quantity  = quantity,
                    desiredDate = wantedDate?.ifBlank { null },
                    shippingBranchId = shippingBranchId
                )
                val response = apiService.addProductToProject(body)
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    val errorBody = response.errorBody()?.string() ?: ""
                    Result.failure(Exception("HTTP ${response.code()}: $errorBody"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun updateProjectProduct(
        projectId: String,
        productId: String,
        updates: Map<String, Any?>
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.updateProjectProduct("eq.$projectId", "eq.$productId", updates)
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    val errorBody = response.errorBody()?.string() ?: ""
                    Result.failure(Exception("HTTP ${response.code()}: $errorBody"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun deleteProjectProduct(
        projectId: String,
        productId: String
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.deleteProjectProduct("eq.$projectId", "eq.$productId")
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    val errorBody = response.errorBody()?.string() ?: ""
                    Result.failure(Exception("HTTP ${response.code()}: $errorBody"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
