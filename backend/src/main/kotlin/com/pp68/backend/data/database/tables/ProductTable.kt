package com.pp68.backend.data.database.tables

import org.jetbrains.exposed.sql.Table

object ItemSilverTable : Table("item_silver") {
    val itemNo            = varchar("item_no", 64)
    val variantCode       = varchar("variant_code", 64).nullable()
    val description       = varchar("description", 512).nullable()
    val productBrandNo    = varchar("product_brand_no", 64).nullable()
    val productGroupNo    = varchar("product_group_no", 64).nullable()
    val productSubgroupNo = varchar("product_subgroup_no", 64).nullable()
    val productColorNo    = varchar("product_color_no", 64).nullable()
    val baseUnitOfMeasure = varchar("base_unit_of_measure", 50).nullable()
    val productWeight     = double("product_weight").nullable()
    val brandName         = varchar("brand_name", 255).nullable()
    val groupName         = varchar("group_name", 255).nullable()
    val subgroupName      = varchar("subgroup_name", 255).nullable()
    val colorName         = varchar("color_name", 255).nullable()

    override val primaryKey = PrimaryKey(itemNo)
}

object ProjectProductTable : Table("project_product") {
    val projectId        = varchar("project_id", 64)
    val productId        = varchar("product_id", 64)
    val quantity         = double("quantity").nullable()
    val desiredDate      = varchar("desired_date", 32).nullable()
    val shippingBranchId = text("shipping_branch_id").nullable()

    override val primaryKey = PrimaryKey(projectId, productId)
}
