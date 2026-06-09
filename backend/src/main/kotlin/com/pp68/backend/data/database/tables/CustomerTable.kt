package com.pp68.backend.data.database.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object CustomerTable : Table("customer") {
    val custId        = varchar("cust_id", 64)
    val companyName   = varchar("company_name", 255)
    val branchId      = varchar("branch_id", 64).nullable()
    val branch        = varchar("branch", 255).nullable()
    val custType      = varchar("cust_type", 100).nullable()
    val companyAddr   = text("company_addr").nullable()
    val companyLat    = double("company_lat").nullable()
    val companyLong   = double("company_long").nullable()
    val companyStatus = varchar("company_status", 100).nullable()
    val createdAt     = timestamp("created_at").nullable()
    val userId        = varchar("user_id", 64).nullable()

    override val primaryKey = PrimaryKey(custId)
}