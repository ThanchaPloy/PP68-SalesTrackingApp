package com.pp68.backend.data.database.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object ContactPersonTable : Table("contact_person") {
    val contactId    = long("contact_id").autoIncrement()
    val customerCode = varchar("customer_code", 64)
    val contactName  = varchar("contact_name", 255).nullable()
    val phone        = varchar("phone", 50).nullable()
    val mobilePhone  = varchar("mobile_phone", 50).nullable()
    val email        = varchar("email", 255).nullable()
    val fax          = varchar("fax", 50).nullable()
    val telexNo      = varchar("telex_no", 50).nullable()
    val isPrimary    = bool("is_primary").default(true)
    val createdAt    = timestamp("created_at").nullable()
    val updatedAt    = timestamp("updated_at").nullable()

    override val primaryKey = PrimaryKey(contactId)
}
