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
    val unit: String?
)

@Singleton
class ProductRepository @Inject constructor(
    private val apiService: ApiService
) {

    suspend fun getAllProducts(): kotlin.Result<List<ProductSimpleDto>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getProductMaster()
                if (response.isSuccessful && response.body() != null) {
                    val list = response.body()!!.map {
                        ProductSimpleDto(
                            productId   = it.productId,
                            productName = it.productGroup ?: it.productId,  // ✅ ใช้ product_group เป็นชื่อ
                            brand       = it.brand?.trim()?.ifBlank { "ไม่ระบุแบรนด์" } ?: "ไม่ระบุแบรนด์",
                            category    = it.productType ?: "ทั่วไป",
                            subCategory = it.productSubgroup ?: "",
                            unit        = it.unit ?: "ชิ้น"
                        )
                    }
                    kotlin.Result.success(list)
                } else {
                    kotlin.Result.failure(Exception("HTTP ${response.code()}: ${response.errorBody()?.string()}"))
                }
            } catch (e: Exception) {
                kotlin.Result.failure(e)
            }
        }
    }

    suspend fun addProductToProject(
        projectId: String,
        productId: String,
        quantity: Double,
        wantedDate: String?
    ): kotlin.Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val body = ProjectProductInsertDto(
                    projectId = projectId,
                    productId = productId,
                    quantity  = quantity,
                    wantedDate = wantedDate
                )
                val response = apiService.addProductToProject(body)
                if (response.isSuccessful) {
                    kotlin.Result.success(Unit)
                } else {
                    kotlin.Result.failure(Exception("เพิ่มสินค้าไม่สำเร็จ: HTTP ${response.code()}"))
                }
            } catch (e: Exception) {
                kotlin.Result.failure(e)
            }
        }
    }
}
