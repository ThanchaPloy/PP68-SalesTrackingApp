package com.pp68.backend.data.database.tables

import org.jetbrains.exposed.sql.Table

object ProductTable : Table("products") {
    val productId      = varchar("product_id", 64)
    val productGroup   = varchar("product_group", 100).nullable()
    val productType    = varchar("product_type", 100).nullable()
    val productSubgroup = varchar("product_subgroup", 100).nullable()
    val productBrand   = varchar("product_brand", 255).nullable()
    val unit           = varchar("unit", 50).nullable()
    val color          = varchar("color", 100).nullable()
    val thickness      = varchar("thickness", 50).nullable()
    val width          = varchar("width", 50).nullable()
    val length         = varchar("length", 50).nullable()
    val dimensionUnit  = varchar("dimension_unit", 50).nullable()

    override val primaryKey = PrimaryKey(productId)
}

object ProjectProductTable : Table("project_product") {
    val projectId    = varchar("project_id", 64)
    val productId    = varchar("product_id", 64)
    val quantity     = integer("quantity").nullable()
    val status       = varchar("status", 100).nullable()
    val deliveryDate = varchar("delivery_date", 32).nullable()

    override val primaryKey = PrimaryKey(projectId, productId)
}