package com.pp68.backend.data.database.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object CustomerTable : Table("customer") {
    val customerCode      = varchar("customer_code", 64)
    val customerName      = varchar("customer_name", 255)
    val createBy          = varchar("create_by", 64).nullable()
    val address           = text("address").nullable()
    val salespersonCode   = varchar("salesperson_code", 50).nullable()
    val createDate        = varchar("create_date", 32).nullable()
    val genBusPostingGroup = varchar("gen_bus_posting_group", 100).nullable()
    val customerStatus    = integer("customer_status").nullable()
    val grade             = integer("grade").nullable()
    val updatedAt         = timestamp("updated_at").nullable()

    override val primaryKey = PrimaryKey(customerCode)
}
