package com.pp68.backend.domain.entity

data class Product(
    val productId: String,
    val productGroup: String?,
    val productType: String?,
    val productSubgroup: String?,
    val productBrand: String?,
    val unit: String?,
    val color: String?,
    val thickness: String?,
    val width: String?,
    val length: String?,
    val dimensionUnit: String?
)